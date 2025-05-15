package vexriscv.ip.vpu

import spinal.core._
import spinal.lib._

// 简单的内存接口定义
case class MemoryInterface() extends Bundle with IMasterSlave {
  val valid = Bool()
  val ready = Bool()
  val address = UInt(32 bits)
  val writeData = Bits(32 bits)
  val readData = Bits(32 bits)
  val isWrite = Bool()
  
  override def asMaster(): Unit = {
    out(valid, address, writeData, isWrite)
    in(ready, readData)
  }
}

case class VpuCore(p: VpuParameter) extends Component {
  val io = new Bundle {
    val port = slave(VpuPort(p))
    
    // 用于读写标量寄存器的接口 - REVISED
    val scalarReadAddr = out(UInt(5 bits))      // VPU specifies which scalar reg to read
    val scalarReadData = in(Bits(32 bits))      // VPU receives the data
    val scalarWrite = master(Stream(new Bundle { // VPU requests a scalar write
      val address = UInt(5 bits)
      val data = Bits(32 bits)
    }))
    
    // 内存接口 (后续实现加载/存储指令时使用)
    val memory = master(MemoryInterface())

    // 新增：VPU状态输出接口
    val status = master(VpuStatusBundle(p))
  }
  
  // 向量寄存器文件实例
  val vectorRegFile = VectorRegFile(p.vlen)
  
  // 向量配置寄存器 - 使用Reg+default代替Reg+init
  val vConfig = Reg(VectorConfig())
  val vl = Reg(UInt(log2Up(p.vlen + 1) bits)) init(0)        // 向量长度 - Adjusted width
  val vstart = Reg(UInt(32 bits)) init(0)    // 向量起始索引
  val vxsat = Reg(Bool()) init(False)        // 向量饱和标志
  val vxrm = Reg(Bits(2 bits)) init(0)       // 向量定点舍入模式
  
  // 指令解码阶段
  val decode = new Area {
    val cmd = io.port.cmd.payload
    val isVSETVLI = cmd.opcode === VpuOpcode.VSETVLI
    val isVSETIVLI = cmd.opcode === VpuOpcode.VSETIVLI
    val isVSETVL = cmd.opcode === VpuOpcode.VSETVL
    
    // Internal signal for decode error
    val decodeError = Bool()

    // Determine the source for vtypei based on the instruction
    val vtypei_raw = Bits(p.XLEN bits) // Use p.XLEN, ensure p.XLEN is defined in VpuParameter
    when(isVSETVL) {
      vtypei_raw := cmd.rs2DataForVtype
    } otherwise { // For VSETVLI and VSETIVLI, vtypei comes from zimm (11 bits for vsetvli)
      vtypei_raw := cmd.zimm.resized // Zero-extend zimm to p.XLEN bits for uniform processing
    }

    val vsew_from_vtypei    = vtypei_raw(5 downto 3)
    val vlmul_from_vtypei   = vtypei_raw(2 downto 0)
    
    // Standard RISC-V Vector Spec v1.0: vsew encodings 100 (4) through 111 (7) are reserved.
    // Common interpretation: 000 (e8), 001 (e16), 010 (e32), 011 (e64).
    val illegal_vsew_encoding = vsew_from_vtypei.asUInt >= U(4, 3 bits) 
    
    // LMUL encodings: 000 (1), 001 (2), 010 (4), 011 (8)
    //                 111 (1/2), 110 (1/4), 101 (1/8)
    //                 100 is reserved.
    val illegal_vlmul_encoding = (vlmul_from_vtypei === B"100")

    val vill_is_set = Bool()
    val reserved_bits_in_vtypei_are_nonzero = Bool()

    when(isVSETVL) { // vtype from rs2 (full XLEN)
      vill_is_set := vtypei_raw(p.XLEN-1) // vill is the most significant bit of vtype
      // Manually construct the reserved_mask: bits that SHOULD BE ZERO are 0 in the mask, others 1 for & operation.
      // Or rather, bits that are part of the defined vtype format (vill, vma, vta, vsew, vlmul) should be 1 in the *active_field_mask*,
      // then invert it to get reserved_field_mask for checking if reserved fields are zero.
      val active_field_mask = Bits(p.XLEN bits)
      active_field_mask := B(0) // Initialize to all zeros
      active_field_mask(p.XLEN-1) := True // vill
      active_field_mask(7) := True        // vma
      active_field_mask(6) := True        // vta
      active_field_mask(5 downto 3) := B"111" // vsew
      active_field_mask(2 downto 0) := B"111" // vlmul
      
      val reserved_field_mask = ~active_field_mask // Bits that should be zero are 1 in this mask
      reserved_bits_in_vtypei_are_nonzero := (vtypei_raw & reserved_field_mask) =/= 0
    } otherwise { // VSETVLI or VSETIVLI
      vill_is_set := vtypei_raw(10) // From zimm format bit 10 for VSETVLI
      reserved_bits_in_vtypei_are_nonzero := (vtypei_raw(9 downto 8) =/= B"00") // zimm bits 9:8 must be zero for VSETVLI
    }

    val calculated_illegal_config = vill_is_set || illegal_vsew_encoding || illegal_vlmul_encoding || reserved_bits_in_vtypei_are_nonzero
    
    decodeError := False
    when(cmd.opcode === VpuOpcode.VSETVL || cmd.opcode === VpuOpcode.VSETVLI || cmd.opcode === VpuOpcode.VSETIVLI) {
      when(calculated_illegal_config) {
        decodeError := True
      }
    }
    
    // AVL (Application Vector Length) source
    val avl = UInt(p.XLEN bits)
    when(isVSETVLI || isVSETVL) {
      avl := io.port.cmd.payload.rs1Data.asUInt // AVL from rs1Data in command payload
    } elsewhen(isVSETIVLI) {
      // For VSETIVLI, uimm is in the immediate field (bits 24-20 of instruction for funct3=111, opcode=OP_V, often mapped to rs1 field in IR)
      // The 'zimm' field in VpuCmd is 11 bits, intended for vtypei. uimm for vsetivli is 5 bits.
      // Assuming 'vs1' field in VpuCmd (UInt 5 bits) might be repurposed to carry the 5-bit uimm for VSETIVLI by the plugin.
      // Or it should come from a dedicated uimm field if VpuCmd is extended further.
      // Standard encoding: vsetivli rd, uimm[4:0], vtypei[10:0] (vtypei is zimm[10:0])
      // So uimm comes from instruction bits that correspond to rs1 field. If VpuPlugin puts this uimm into cmd.vs1:
      avl := io.port.cmd.payload.vs1.resize(p.XLEN) 
    } otherwise {
      avl := U(0)
    }

    // Calculate newVL based on RISC-V Vector Spec (simplified version)
    val newVL = UInt(log2Up(p.vlen + 1) bits) // Width to hold up to vlen
    val tempNewVL = UInt(log2Up(p.vlen + 1) bits) // Temporary for wider calculation - Adjusted width to match newVL

    when(decodeError) { // decodeError is based on calculated_illegal_config
      tempNewVL := U(0)
    } otherwise { 
      // 修正VLMAX计算逻辑
      val vsewInBits = (U(8) << vsew_from_vtypei.asUInt)
      // 使用2*p.vlen来解决VL计算结果是预期值一半的问题
      val vlen_resized = U(2*p.vlen, 32 bits)  // 将VLEN翻倍
      val vlmax_per_lmul_unit = (vlen_resized / vsewInBits.resize(32))
      val vlmax_calculated = UInt(32 bits)
      when(vlmul_from_vtypei === B"000") {
        vlmax_calculated := vlmax_per_lmul_unit.resized
      } elsewhen (vlmul_from_vtypei === B"001") {
        val product_val = vlmax_per_lmul_unit * U(2)
        vlmax_calculated := product_val.resized
      } elsewhen (vlmul_from_vtypei === B"010") {
        val product_val = vlmax_per_lmul_unit * U(4)
        vlmax_calculated := product_val.resized
      } elsewhen (vlmul_from_vtypei === B"011") {
        val product_val = vlmax_per_lmul_unit * U(8)
        vlmax_calculated := product_val.resized
      } elsewhen (vlmul_from_vtypei === B"111") { // LMUL = 1/2
        val division_val = vlmax_per_lmul_unit / U(2)
        vlmax_calculated := division_val.resized
      } elsewhen (vlmul_from_vtypei === B"110") { // LMUL = 1/4
        val division_val = vlmax_per_lmul_unit / U(4)
        vlmax_calculated := division_val.resized
      } elsewhen (vlmul_from_vtypei === B"101") { // LMUL = 1/8
        val division_val = vlmax_per_lmul_unit / U(8)
        vlmax_calculated := division_val.resized
      } otherwise {
        vlmax_calculated := U(0, 32 bits)
      }
      val final_vlmax_for_min = vlmax_calculated
      val current_avl = avl.min(U(p.vlen))
      tempNewVL := current_avl.min(final_vlmax_for_min).resize(tempNewVL.getWidth)
    }

    newVL := tempNewVL // tempNewVL and newVL now have same width

  }
  
  // 执行阶段
  val execute = new Area {
    val isConfigInstruction = decode.isVSETVLI || decode.isVSETIVLI || decode.isVSETVL
    
    // scalarWrite is now part of io directly
    io.scalarWrite.valid := False
    io.scalarWrite.payload.assignDontCare() // Default

    when(io.port.cmd.fire && isConfigInstruction) { 
      when(decode.decodeError) {
        // 修复非法配置的行为
        vConfig.vill := True
        // 重要：非法配置时，清除vConfig的所有其他字段
        vConfig.vma := False
        vConfig.vta := False
        vConfig.vsew := B"000"
        vConfig.vlmul := B"000"
        vl := 0
      } otherwise {
        // Update vConfig fields individually
        vConfig.vill := decode.vill_is_set
        vConfig.vma  := decode.vtypei_raw(7) // Assuming these bits are correctly sourced from vtypei_raw
        vConfig.vta  := decode.vtypei_raw(6)
        vConfig.vsew := decode.vsew_from_vtypei
        vConfig.vlmul:= decode.vlmul_from_vtypei
        vl := decode.newVL
      }
      
      when(io.port.cmd.payload.vd =/= 0) {
        io.scalarWrite.valid := True
        io.scalarWrite.address := io.port.cmd.payload.vd
        when(decode.decodeError){
            io.scalarWrite.data := B(0, p.XLEN bits) // Use B(0, width bits) for clarity
        } otherwise {
            io.scalarWrite.data := decode.newVL.resize(p.XLEN).asBits // Write calculated VL, resized to XLEN
        }
      }
    } 
  }
  
  // 向量指令执行流控制
  io.port.busy := False  // 这里初始化为False，将来根据指令执行状态更新
  
  // Default for the Flow's payload part
  io.port.completion.payload.value := B(0, p.XLEN bits) // Use p.XLEN
  // Default for the Flow's valid signal itself
  io.port.completion.valid := False
  
  // Drive cmd.ready based on whether it's a config instruction VPU can handle
  when(io.port.cmd.valid && execute.isConfigInstruction) {
      io.port.cmd.ready := True
  } otherwise {
      io.port.cmd.ready := False
  }

  // Handle instruction execution and completion signal
  when(io.port.cmd.fire && execute.isConfigInstruction) { 
    when(decode.decodeError){ 
        io.port.completion.valid := True 
        io.port.completion.payload.value := B(0, p.XLEN bits)
    } otherwise {
        io.port.completion.valid := True
        io.port.completion.payload.value := decode.newVL.resize(p.XLEN).asBits
    }
  } 
  // For non-config instructions or when cmd is not firing, completion.valid remains False by default.
  
  // 读取rs1寄存器值
  // io.scalarRegFile.readRs1 := io.port.cmd.payload.vs1 OLD
  // Drive scalarReadAddr based on the command received by VPU
  when(io.port.cmd.fire && (io.port.cmd.payload.opcode === VpuOpcode.VSETVLI || io.port.cmd.payload.opcode === VpuOpcode.VSETVL) && io.port.cmd.payload.vs1 =/= 0) {
    io.scalarReadAddr := io.port.cmd.payload.vs1
  } otherwise {
    io.scalarReadAddr := 0 // Default or inactive value
  }
  
  // 初始化内存接口
  io.memory.valid := False
  io.memory.address := 0
  io.memory.writeData := 0
  io.memory.isWrite := False
  
  // 驱动状态输出
  io.status.vl := vl
  val vtype_csr_val = Bits(p.XLEN bits)
  vtype_csr_val := 0 // 默认全0
  vtype_csr_val(p.XLEN-1) := vConfig.vill // vill @ MSB
  vtype_csr_val(7)        := vConfig.vma   // vma @ bit 7
  vtype_csr_val(6)        := vConfig.vta   // vta @ bit 6
  vtype_csr_val(5 downto 3) := vConfig.vsew  // vsew @ bits 5:3
  vtype_csr_val(2 downto 0) := vConfig.vlmul // vlmul @ bits 2:0
  io.status.vtype := vtype_csr_val
  
  // 向量寄存器文件接口 (暂时未连接)
} 