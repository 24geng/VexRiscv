package vexriscv.plugin

import spinal.core._
import spinal.lib._
import vexriscv._
import vexriscv.ip.rvv._ // Import RVV Interface definitions
import vexriscv.Riscv // Import Riscv constants
import spinal.core.sim._ // <<< Add this import for printf >>>

import scala.collection.mutable.ArrayBuffer

/**
 * VexRiscv Plugin for the RISC-V Vector Extension (RVV).
 *
 * This plugin integrates a Vector Processing Unit (VPU), implemented potentially
 * in RVVCore, into the VexRiscv pipeline.
 *
 * It handles:
 * - Decoding RVV instructions.
 * - Managing RVV CSRs (vl, vtype, etc.).
 * - Interacting with the RVVCore for vector operations.
 * - Handling vector load/store interactions with the memory system.
 * - Managing pipeline hazards related to vector operations.
 *
 * @param p         The RVV configuration parameters.
 * @param externalVpu If true, assumes RVVCore is external and uses the RVVPort master interface.
 */
class RVVPlugin(p: RVVParameter, externalVpu: Boolean = false) extends Plugin[VexRiscv] with VexRiscvRegressionArg {

  // --- Stageables for RVV information passing through the pipeline ---
  object RVV_ENABLE extends Stageable(Bool()) // Is the current instruction an RVV instruction?
  object IS_VSETVL extends Stageable(Bool()) // Is it specifically vsetvl or vsetvli?
  // We might need more specific stageables later for different instruction types

  // Values needed for vl calculation (populated in decode/execute)
  object REQUESTED_AVL extends Stageable(p.AVL_TYPE())     // Requested Application Vector Length (from rs1 or 0)
  object REQUESTED_VTYPE_IMM extends Stageable(Bits(11 bits)) // Raw immediate bits for vtype (vsetvli)
  object REQUESTED_VTYPE_RS2 extends Stageable(p.XLEN_TYPE()) // Raw rs2 value for vtype (vsetvl)

  // Values calculated in execute stage
  object CALCULATED_VL extends Stageable(p.VL_TYPE())       // The calculated 'vl' value
  object CALCULATED_VTYPE extends Stageable(VType(p))     // The calculated 'vtype' value to be written
  object WRITE_RD_FROM_VL extends Stageable(Bool())     // Control signal for writeback stage

  // --- Optional RVV Core instance ---
  var port: RVVPort = null // Interface to the RVV Core

  // --- Setup Phase (Plugin initialization) ---
  override def setup(pipeline: VexRiscv): Unit = {
    import pipeline.config._
    import Riscv._

    val decoderService = pipeline.service(classOf[DecoderService])

    // --- Default values for Stageables ---
    decoderService.addDefault(RVV_ENABLE, False)
    decoderService.addDefault(IS_VSETVL, False)
    decoderService.addDefault(WRITE_RD_FROM_VL, False)
    // Add defaults for REQUESTED_* signals
    decoderService.addDefault(REQUESTED_AVL, U(0, p.XLEN bits))
    decoderService.addDefault(REQUESTED_VTYPE_RS2, U(0, p.XLEN bits))
    decoderService.addDefault(REQUESTED_VTYPE_IMM, B(0, 11 bits))

    // --- Instruction Decoding ---
    val vsetvlActions = List(
      RVV_ENABLE -> True,
      IS_VSETVL -> True,
      WRITE_RD_FROM_VL -> True,
      REGFILE_WRITE_VALID -> True, // Signal that this instruction writes to RD
      RS1_USE -> True,
      RS2_USE -> True
      // TODO: Consider bypass settings like FpuPlugin does (BYPASSABLE_*)
    )

    val vsetvliActions = List(
      RVV_ENABLE -> True,
      IS_VSETVL -> True,
      WRITE_RD_FROM_VL -> True,
      REGFILE_WRITE_VALID -> True // Signal that this instruction writes to RD
      // RS1 is implicitly 0, RS2 is not used
    )

    decoderService.add(VSETVL, vsetvlActions)
    decoderService.add(VSETVLI, vsetvliActions)

    // TODO: Decode other RVV instructions here

    // --- CSR Registration ---
    // Deferred to build phase

    // --- RVV Core Port ---
    port = RVVPort(p).setName("rvvCorePort")
    if(externalVpu) master(port)

  }

  // --- Build Phase (Hardware generation) ---
  override def build(pipeline: VexRiscv): Unit = {
    import pipeline._
    import pipeline.config._
    import Riscv._

    // <<< 添加 Verilator public 调试信号 >>>
    val decodeActiveDebugSignal = Reg(Bool()) init(False) addAttribute(Verilator.public) setName("decodeActiveDebugSignal")
    val isVsetvlDecodeDebugSignal = Reg(Bool()) init(False) addAttribute(Verilator.public) setName("isVsetvlDecodeDebugSignal")

    val rvvCoreArea = if (!externalVpu) pipeline plug new Area {
      val core = RVVCore(1, p)
      port <> core.io.port(0)
    } else null

    // --- CSR Area (Moved back before stages that use its signals) ---
    val csrArea = pipeline plug new Area {
      val vlReg = Reg(p.VL_TYPE()) init (0) addAttribute(Verilator.public)
      // val vtypeReg = Reg(VType(p)) // Remove the Bundle Reg

      // Define a Reg to hold the raw VTYPE bits
      val vtypeBitsReg = Reg(Bits(p.XLEN bits)) init(0) addAttribute(Verilator.public)
      // Explicitly init the used bits to avoid latches if XLEN > VTYPE_WIDTH
      // Use widthOf(VType(p)) to get the correct width

      val vlenbVal = U(p.vlenb, p.XLEN bits)

      val csrService = pipeline.service(classOf[CsrInterface])
      csrService.rw(CSR.VL, vlReg)
      csrService.r(CSR.VLENB, vlenbVal)

      // Register the raw bits Reg with CsrPlugin
      csrService.rw(CSR.VTYPE, vtypeBitsReg)

      // Update from VSETVL instruction (Handled in WriteBack Stage below)
      // Note: No onWrite callback needed as rw targets the Reg directly.
    }

    decode plug new Area {
      import decode._

      // <<< 移除之前的 printf 语句 >>>
      // printf("Decode Stage ACTIVE: Instruction=0x%h\\\\n\", input(INSTRUCTION))
      // when(input(IS_VSETVL)) {
      //     printf("Decode Stage DETECTED VSETVL/VSETVLI: Instruction=0x%h\\\\n\", input(INSTRUCTION))
      // }

      // <<< 更新 Verilator public 调试信号 >>>
      decodeActiveDebugSignal := arbitration.isValid
      isVsetvlDecodeDebugSignal := input(IS_VSETVL) // 持续更新，即使 stage 无效

      // 只有当确实是 VSETVL/VSETVLI 时才插入特定值
      // 默认值现在由 decoderService.addDefault 处理
      when(input(IS_VSETVL)) {
        insert(REQUESTED_AVL) := input(RS1).asUInt
        insert(REQUESTED_VTYPE_RS2) := input(RS2).asUInt // 用于 VSETVL
        insert(REQUESTED_VTYPE_IMM) := input(INSTRUCTION)(30 downto 20) // 用于 VSETVLI 的 imm[10:0]

        // <<< 移除之前的 printf 语句 >>>
        // printf("Decode Stage (IS_VSETVL=True): REQ_AVL=0x%h, REQ_VTYPE_RS2=0x%h, REQ_VTYPE_IMM=0x%h\\\\n\",
        //        input(RS1).asUInt,
        //        input(RS2).asUInt,
        //        input(INSTRUCTION)(30 downto 20))
      }
    }

    execute plug new Area {
      import execute._

      // --- 移除 Verilator public 属性 ---
      // input(REQUESTED_AVL).addAttribute(Verilator.public)
      // input(REQUESTED_VTYPE_RS2).addAttribute(Verilator.public)

      // <<< 修改为使用 isFiring，并打印更多输入信息 >>>
      when(arbitration.isFiring) { // 仅在阶段有效且未被暂停/刷新时打印
        printf("Execute Stage FIRING: IS_VSETVL=%b, REQ_AVL=0x%h, REQ_VTYPE_RS2=0x%h, REQ_VTYPE_IMM=0x%h\\n",
               input(IS_VSETVL),
               input(REQUESTED_AVL),
               input(REQUESTED_VTYPE_RS2),
               input(REQUESTED_VTYPE_IMM)) // 也打印 IMM
      }

      // 读取当前的 VTYPE 位
      val currentVtypeBits = csrArea.vtypeBitsReg
      val requestedVTypeView = VType(p) // Combinational view
      requestedVTypeView.assignFromBits(currentVtypeBits(widthOf(requestedVTypeView)-1 downto 0))

      // Determine if the incoming instruction *would* modify VTYPE (for calculation)
      val isVsetvliForCalc = input(IS_VSETVL) && (input(INSTRUCTION)(rs1Range) === 0)
      val vtypeBitsForCalc = isVsetvliForCalc ? input(REQUESTED_VTYPE_IMM).asUInt.resized | input(REQUESTED_VTYPE_RS2)
      val vtypeFromInstr = VType(p)
      // --- Remove assignFromBits ---
      // vtypeFromInstr.assignFromBits(vtypeBitsForCalc.asBits(widthOf(vtypeFromInstr)-1 downto 0))

      // --- Explicitly parse vtype bits based on Spec layout, for both vsetvl and vsetvli ---
      // Use vtypeBitsForCalc which holds either imm or rs2 value
      // Assuming XLEN=32 for now, adjust if needed. Spec Figure 6.1 vtype layout:
      // XLEN-1 | XLEN-2 .. 8 | 7  | 6  | 5:3   | 2:0
      //  vill  | Reserved    | vma| vta| vlmul | vsew
      val sourceBits = vtypeBitsForCalc.asBits // Use the combined source

      // Check XLEN and adjust slicing if necessary, assuming XLEN >= 9
      val vtypeWidthInSource = p.XLEN min 32 // Consider only relevant bits for vtype CSR in RV32

      // For RV32, vtype occupies lower XLEN bits. vill is at XLEN-1.
      vtypeFromInstr.vill  := sourceBits(vtypeWidthInSource - 1)
      vtypeFromInstr.vma   := sourceBits(7)
      vtypeFromInstr.vta   := sourceBits(6)
      vtypeFromInstr.vlmul := sourceBits(5 downto 3).asUInt
      vtypeFromInstr.vsew  := sourceBits(2 downto 0).asUInt

      // Calculate SEW based on the *requested* VTYPE from the instruction
      val SEW = UInt(log2Up(p.ELEN + 1) bits)
      switch(vtypeFromInstr.vsew) { // Use vtypeFromInstr here
        is(0) { SEW := 8 }
        is(1) { SEW := 16 }
        is(2) { SEW := 32 }
        is(3) { SEW := 64 }
        default { SEW := 0 }
      }

      // Calculate initial vlmax based on instruction's requested VLEN, SEW, LMUL
      val initialVlmax = p.VL_TYPE()
      val lmulIsFractional = vtypeFromInstr.vlmul(2) // Use vtypeFromInstr
      val lmulIsReserved = vtypeFromInstr.vlmul === 4 // Use vtypeFromInstr
      val sewIsInvalid = (SEW === 0 || SEW > p.ELEN)
      
      val VLEN_div_SEW = Mux(sewIsInvalid, U(0, p.VL_WIDTH bits), U(p.VLEN, p.VL_WIDTH+log2Up(SEW.maxValue)+1 bits) / SEW)

      // Calculate base vlmax value, handling fractional LMUL separately
      val fractionalShift = UInt(3 bits)
      val fractionalDivisorValid = True
      switch(vtypeFromInstr.vlmul){ // Use vtypeFromInstr
          is(5) { fractionalShift := 1} 
          is(6) { fractionalShift := 2}
          is(7) { fractionalShift := 3}
          default{fractionalShift := 0; fractionalDivisorValid := False}
      }
      val sewShiftedForFractional = SEW << fractionalShift
      val fractionalOverflow = (sewShiftedForFractional >> log2Up(p.ELEN + 1)).orR

      when(lmulIsFractional && fractionalDivisorValid && !sewIsInvalid && !fractionalOverflow){
           initialVlmax := (U(p.VLEN) / sewShiftedForFractional).resize(p.VL_WIDTH)
      } otherwise { 
          switch(vtypeFromInstr.vlmul) { // Use vtypeFromInstr
              is(0) { initialVlmax := VLEN_div_SEW.resized }
              is(1) { initialVlmax := (VLEN_div_SEW << 1).resized }
              is(2) { initialVlmax := (VLEN_div_SEW << 2).resized }
              is(3) { initialVlmax := (VLEN_div_SEW << 3).resized }
              default{ initialVlmax := U(0) }
          }
      }
      
      // Determine if the configuration is illegal based on instruction's request
      val isIllegal = Bool()
      isIllegal := lmulIsReserved | sewIsInvalid | vtypeFromInstr.vill // Use vtypeFromInstr
      when(lmulIsFractional && (!fractionalDivisorValid || fractionalOverflow)){
          isIllegal := True
      }
      
      // Apply minimum vlmax = 1 rule
      val correctedVlmax = p.VL_TYPE()
      correctedVlmax := initialVlmax
      val applyMinVlmaxRule = !isIllegal && initialVlmax === 0
      val minVlmaxCondition = (lmulIsFractional && SEW =/=0 && (SEW << fractionalShift <= p.VLEN)) || 
                              (!lmulIsFractional && SEW =/=0 && vtypeFromInstr.vlmul <= 3 && (U(p.VLEN) >> vtypeFromInstr.vlmul) >= SEW) // Use vtypeFromInstr
                                
      when(applyMinVlmaxRule && minVlmaxCondition){
          correctedVlmax := U(1)
      }

      // Final calculation for vl and the *actual* vtype to be written
      val currentAvl = input(REQUESTED_AVL)
      val finalVL = Mux(isIllegal, U(0, p.VL_WIDTH bits), (currentAvl > correctedVlmax) ? correctedVlmax | currentAvl.resize(p.VL_WIDTH))
      val zeroVType = VType(p)
      zeroVType.vill := False
      zeroVType.vma  := False
      zeroVType.vta  := False
      zeroVType.vsew := 0
      zeroVType.vlmul := 0
      // The actualVType to be written is based on the instruction's request if legal
      val actualVType = Mux(isIllegal, zeroVType, vtypeFromInstr)
      when(isIllegal) {
        actualVType.vill := True // Ensure vill is set if illegal
      }
      
      // --- Add Verilator public attribute for debugging ---
      finalVL.addAttribute(Verilator.public)
      actualVType.addAttribute(Verilator.public) // Add to the Bundle directly

      // <<< 为 VL 计算添加详细的 printf >>>
      when(input(IS_VSETVL) && arbitration.isFiring) { // 使用 isFiring
        // --- 保留先前步骤的详细 printf ---
        printf("Execute Stage (vsetvl) Details: isIllegal=%b, currentAvl=0x%h, correctedVlmax=0x%h, finalVL=0x%h, reqVtype=0x%h, parsedSEW=%h, parsedLMUL=%h\\n",
               isIllegal,
               currentAvl,
               correctedVlmax,
               finalVL,
               vtypeBitsForCalc,
               vtypeFromInstr.vsew,
               vtypeFromInstr.vlmul)
      }

      insert(CALCULATED_VL) := finalVL 
      insert(CALCULATED_VTYPE) := actualVType // Insert the calculated actual vtype

      // <<< 保留现有的 final VL printf (冗余但暂时保留) >>>
      when(input(IS_VSETVL) && arbitration.isFiring) { // 使用 isFiring
        printf("Execute Stage (vsetvl): FinalVL=0x%h\n", finalVL) // 使用 %h
      }

      // <<< For debugging, PPRINT THE CORRECT VARIABLES >>>
      when(arbitration.isFiring && input(IS_VSETVL)){
          val elenConst = U(p.ELEN) // Convert p.ELEN to a UInt constant for printing
          val vtypeFromInstrLmul = vtypeFromInstr.vlmul // Assign to a temp val
          val actualVill = actualVType.vill // Assign to a temp val
          // Using traditional printf format
          printf("Execute (IS_VSETVL): SEW=0x%h, ELEN=0x%h, vtypeFromInstr.vlmul=0x%h, isIllegalVar=%b, finalVLVar=0x%h, actualVType.villVar=%b\n",
                 SEW, elenConst, vtypeFromInstrLmul, isIllegal, finalVL, actualVill)
      }
    } // End of execute plug new Area

    writeBack plug new Area {
      import writeBack._ // Import writeBack._ to access stage signals

      // When IS_VSETVL is true and the instruction is valid and firing in the writeBack stage
      when(input(IS_VSETVL) && arbitration.isFiring) { // arbitration.isFiring is crucial
        
        // Handle writing rd (destination register)
        when(input(WRITE_RD_FROM_VL)) { // This stageable was set in decode
          output(REGFILE_WRITE_DATA) := input(CALCULATED_VL).asBits.resized // Write vl to rd
        }

        // Explicitly construct the 32-bit vtype value for the CSR
        val calculatedVl = input(CALCULATED_VL)
        val calculatedVtypeBundle = input(CALCULATED_VTYPE) // This is VType(p) Bundle

        val vtypeBitsForCsr = Bits(p.XLEN bits)
        vtypeBitsForCsr(p.XLEN-1) := calculatedVtypeBundle.vill
        vtypeBitsForCsr(7)        := calculatedVtypeBundle.vma
        vtypeBitsForCsr(6)        := calculatedVtypeBundle.vta
        vtypeBitsForCsr(5 downto 3) := calculatedVtypeBundle.vlmul.asBits // Ensure .asBits if vlmul is UInt
        vtypeBitsForCsr(2 downto 0) := calculatedVtypeBundle.vsew.asBits   // Ensure .asBits if vsew is UInt
        
        // Zero out reserved bits (e.g., XLEN-2 downto 8 for XLEN=32)
        // Ensure slices are within bounds if XLEN is small, though for XLEN=32 this is fine.
        if (p.XLEN > 8) { // General check, for XLEN=32, (XLEN-2) is 30.
            val highReservedBit = if (p.XLEN-2 >= 8) p.XLEN-2 else 7 // Avoid negative range length
            if (highReservedBit >= 8) { // only zero if the range is valid
                 vtypeBitsForCsr(highReservedBit downto 8) := 0
            }
        }

        csrArea.vlReg        := calculatedVl
        csrArea.vtypeBitsReg := vtypeBitsForCsr // Assign the carefully constructed 32-bit value

        // Optional: Add Verilator public signals or printf for debugging writeback
        // SpinalHDL printf is usually optimized out for synthesis.
        // Using traditional printf format
        printf("RVVPlugin:WriteBack (IS_VSETVL): Firing! Writing vlCSR=0x%h\n", calculatedVl)
        printf("  CalculatedVType Bundle: vill=%b vma=%b vta=%b vlmul=0x%h vsew=0x%h\n",
               calculatedVtypeBundle.vill,
               calculatedVtypeBundle.vma,
               calculatedVtypeBundle.vta,
               calculatedVtypeBundle.vlmul, // Assuming vlmul and vsew are printable as hex if UInt
               calculatedVtypeBundle.vsew)
        printf("  Value written to vtype CSR (32-bit): 0x%h\n", vtypeBitsForCsr)
      }
    } // End of writeBack plug new Area
  }

  // --- Regression Arguments ---
  override def getVexRiscvRegressionArgs(): Seq[String] = {
    var args = List[String]()
    args :+= s"RVV=yes"
    args :+= s"RVV_VLEN=${p.VLEN}"
    args :+= s"RVV_ELEN=${p.ELEN}"
    // Add other relevant parameters if needed
    args
  }
} 