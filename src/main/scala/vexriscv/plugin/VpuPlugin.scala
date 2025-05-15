package vexriscv.plugin

import spinal.core._
import spinal.lib._
import vexriscv._
import vexriscv.ip.vpu._

// VexRiscv的VPU插件，实现向量处理单元
class VpuPlugin(val vpuParameter: VpuParameter = VpuParameter()) extends Plugin[VexRiscv] {
  // 定义流水线级信号
  object VPU_ENABLE extends Stageable(Bool())
  object VPU_FLUSH extends Stageable(Bool())
  object VPU_OPCODE extends Stageable(VpuOpcode())
  object VPU_VS1 extends Stageable(UInt(5 bits)) 
  object VPU_VS2 extends Stageable(UInt(5 bits))
  object VPU_VS3 extends Stageable(UInt(5 bits))
  object VPU_VD extends Stageable(UInt(5 bits))
  object VPU_VM extends Stageable(Bool)
  object VPU_ZIMM extends Stageable(Bits(11 bits))
  object VPU_FUNCT3 extends Stageable(Bits(3 bits))
  object VPU_FUNCT6 extends Stageable(Bits(6 bits))
  object VPU_WRITE_SCALAR extends Stageable(Bool())
  object VPU_SCALAR_WRITE_DATA extends Stageable(Bits(32 bits)) // Assuming XLEN is 32 for scalar write
  
  // CSR地址常量
  val VLENB_CSR_ADDR = 0xC22
  
  override def setup(pipeline: VexRiscv): Unit = {
    import pipeline.config._
    
    val decoderService = pipeline.service(classOf[DecoderService])
    
    // 默认值设置
    decoderService.addDefault(VPU_ENABLE, False)
    decoderService.addDefault(VPU_OPCODE, VpuOpcode.VSETVLI) // Default, can be any VpuOpcode value
    // Add defaults for other VPU_ stageables if they are read in execute stage without being set by all decoded VPU instructions
    // For example:
    // decoderService.addDefault(VPU_FUNCT3, B(0))

    // VSETVL : funct6 = 100000 (bit31=1, bit30=0), vm=0, funct3=111, opcode=1010111
    // Pattern : 1 00000 0 --- vm=0 ---
    decoderService.add(
      key = M"1000000----------111-----1010111", // bit31=1 distinguishes VSETVL from VSETVLI
      List(
        VPU_ENABLE -> True,
        VPU_OPCODE -> VpuOpcode.VSETVL,
        RS1_USE    -> True, 
        RS2_USE    -> True, 
        REGFILE_WRITE_VALID -> True // Inform pipeline GPR write might occur (if rd != 0)
      )
    )
    
    // VSETVLI: vm=0 (bit 25), funct3=0b111, opcode=0b1010111 (OP-V)
    // vtypei=instr[30:20], uimm=instr[19:15]. Bit 31 is vtypei[10].
    // Pattern: [31:26]=vtypei[10:5], [25]=vm=0, [24:20]=vtypei[4:0], [19:15]=uimm, ...
    decoderService.add(
      key = M"------0----------111-----1010111", // Derived from user-style, ensuring vm=0
      List(
        VPU_ENABLE -> True,
        VPU_OPCODE -> VpuOpcode.VSETVLI,
        RS1_USE    -> True, // rs1 field is uimm (AVL source)
        // RS2_USE is not set if rs2 GPR value isn't used (bits are part of immediate)
        REGFILE_WRITE_VALID -> True // Inform pipeline GPR write might occur (if rd != 0)
      )
    )
  }
  
  override def build(pipeline: VexRiscv): Unit = {
    import pipeline._
    import pipeline.config._
    
    val vpuCore = VpuCore(vpuParameter)
    val XLEN = vpuParameter.XLEN

    // Default connections for VpuCore inputs.
    vpuCore.io.port.cmd.valid := False
    vpuCore.io.port.cmd.payload.opcode := VpuOpcode.VSETVLI // Default
    vpuCore.io.port.cmd.payload.rs1Data := B(0, XLEN bits)
    vpuCore.io.port.cmd.payload.rs2DataForVtype := B(0, XLEN bits)
    vpuCore.io.port.cmd.payload.vs1 := U(0, 5 bits)
    vpuCore.io.port.cmd.payload.vd  := U(0, 5 bits)
    vpuCore.io.port.cmd.payload.zimm := B(0, 11 bits)
    
    vpuCore.io.scalarReadData := B(0) 
    vpuCore.io.scalarWrite.ready := False 
    vpuCore.io.memory.ready := False

    // --- CSR Connections ---
    val csrService = pipeline.service(classOf[CsrInterface])
    csrService.r(0xC20, vpuCore.io.status.vl)    // VL CSR
    csrService.r(0xC21, vpuCore.io.status.vtype) // VTYPE CSR
    
    val vlenb_value = Bits(XLEN bits)
    vlenb_value := B(vpuParameter.vlen / 8, XLEN bits) 
    csrService.r(VLENB_CSR_ADDR, vlenb_value) // VLENB CSR (read-only with fixed value)

    // --- Execute Stage Logic ---
    execute plug new Area {
      import execute._

      val currentInstruction = input(INSTRUCTION)
      val rdAddrInInst = currentInstruction(11 downto 7).asUInt // rd field from instruction

      when(input(VPU_ENABLE) && arbitration.isValid) {
        vpuCore.io.port.cmd.payload.opcode := input(VPU_OPCODE)
        vpuCore.io.port.cmd.payload.vd     := rdAddrInInst 

        switch(input(VPU_OPCODE)) {
          is(VpuOpcode.VSETVL) {
            vpuCore.io.port.cmd.valid := True
            // RESTORED Original assignments:
            vpuCore.io.port.cmd.payload.rs1Data := input(RS1).asBits
            vpuCore.io.port.cmd.payload.rs2DataForVtype := input(RS2).asBits
          }
          is(VpuOpcode.VSETVLI) {
            vpuCore.io.port.cmd.valid := True
            vpuCore.io.port.cmd.payload.rs1Data := input(RS1).asUInt.resize(XLEN bits).asBits 
            vpuCore.io.port.cmd.payload.zimm    := currentInstruction(30 downto 20)
          }
          // Add default case if necessary, for example, to ensure cmd.valid is False for unhandled VPU_OPCODEs
          // default {
          //   vpuCore.io.port.cmd.valid := False // This is already the default from outside the 'when(input(VPU_ENABLE)...)'
          // }
        }

        when(vpuCore.io.port.cmd.valid && !vpuCore.io.port.cmd.ready) {
          arbitration.haltItself := True
        }
      }

      // Handle VPU completion for GPR write (e.g., for vsetvl/i writing to rd)
      // This connection ensures that if the VPU completes and the instruction was decoded
      // to write to a GPR (REGFILE_WRITE_VALID is True) and rd is not x0, the data is written.
      when(input(REGFILE_WRITE_VALID) && rdAddrInInst =/= 0 && vpuCore.io.port.completion.valid){
        output(REGFILE_WRITE_DATA) := vpuCore.io.port.completion.payload.value
      }
      // VpuCore itself might also try to write to GPRs via io.scalarWrite.
      // For now, assume completion path is the one tied to REGFILE_WRITE_VALID from decoder for vsetvl/i.
      // We can make scalarWrite.ready dependent on GPR availability if VpuCore uses it for other GPR writes.
    }
  }
} 