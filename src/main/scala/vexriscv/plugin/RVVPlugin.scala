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
  object IS_VSETVL_OR_VSETVLI extends Stageable(Bool()) // True for both vsetvl and vsetvli
  object IS_VSETVLI extends Stageable(Bool())           // True for vsetvli only

  object VSETVL_RD_WRITE_EN extends Stageable(Bool())   // True if rd of vsetvl/i is not x0
  object VSETVL_AVL extends Stageable(UInt(p.XLEN bits)) // rs1 value for vsetvl, zimm value for vsetvli
  object VSETVL_VTYPE_RS2 extends Stageable(Bits(p.XLEN bits)) // rs2 value for vsetvl
  object VSETVL_VTYPE_IMM extends Stageable(Bits(11 bits))   // 11-bit immediate for vsetvli
  object VSETVL_AVL_IS_VLMAX extends Stageable(Bool()) // For vsetvli, if zimm=0, requested AVL is VLMAX

  object EXEC_OLD_VL extends Stageable(UInt(p.VL_WIDTH bits))
  object EXEC_FINAL_VL extends Stageable(UInt(p.VL_WIDTH bits))
  object EXEC_FINAL_VTYPE extends Stageable(VType(p))

  // Values calculated in execute stage
  // object CALCULATED_VL extends Stageable(p.VL_TYPE())       // The calculated 'vl' value
  // object CALCULATED_VTYPE extends Stageable(VType(p))     // The calculated 'vtype' value to be written
  // object WRITE_RD_FROM_VL extends Stageable(Bool())     // Control signal for writeback stage
  // object OLD_VL_VALUE extends Stageable(p.VL_TYPE())        // Stores the original vl value for rd writeback

  // --- Optional RVV Core instance ---
  var port: RVVPort = null // Interface to the RVV Core

  // --- Setup Phase (Plugin initialization) ---
  override def setup(pipeline: VexRiscv): Unit = {
    import pipeline.config._
    import Riscv._

    val decoderService = pipeline.service(classOf[DecoderService])

    // --- Default values for Stageables ---
    decoderService.addDefault(RVV_ENABLE, False)
    decoderService.addDefault(IS_VSETVL_OR_VSETVLI, False)
    decoderService.addDefault(IS_VSETVLI, False)
    decoderService.addDefault(VSETVL_RD_WRITE_EN, False)
    decoderService.addDefault(VSETVL_AVL, U(0))
    decoderService.addDefault(VSETVL_VTYPE_RS2, B(0))
    decoderService.addDefault(VSETVL_VTYPE_IMM, B(0))
    decoderService.addDefault(VSETVL_AVL_IS_VLMAX, False)
    // Add defaults for REQUESTED_* signals
    // decoderService.addDefault(REQUESTED_AVL, U(0, p.XLEN bits))
    // decoderService.addDefault(REQUESTED_VTYPE_RS2, U(0, p.XLEN bits))
    // decoderService.addDefault(REQUESTED_VTYPE_IMM, B(0, 11 bits))

    // --- Instruction Decoding ---
    val vsetvl_actions = List(
      RVV_ENABLE             -> True,
      IS_VSETVL_OR_VSETVLI   -> True,
      IS_VSETVLI             -> False,
      RS1_USE                -> True,
      RS2_USE                -> True,
      REGFILE_WRITE_VALID    -> True // Tentative, refined in decode
    )

    val vsetvli_actions = List(
      RVV_ENABLE             -> True,
      IS_VSETVL_OR_VSETVLI   -> True,
      IS_VSETVLI             -> True,
      RS1_USE                -> True, // rs1 field is zimm
      RS2_USE                -> False,
      REGFILE_WRITE_VALID    -> True // Tentative, refined in decode
    )

    decoderService.add(VSETVL, vsetvl_actions)
    decoderService.add(VSETVLI, vsetvli_actions)

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
    val decode_is_vsetvli_check_val_debug = Reg(Bool()) init(False) addAttribute(Verilator.public) setName("decode_is_vsetvli_check_val_debug")
    val decode_intended_IS_VSETVLI_val_debug = Reg(Bool()) init(False) addAttribute(Verilator.public) setName("decode_intended_IS_VSETVLI_val_debug")
    val decode_intended_VTYPE_RS2_val_debug = Reg(Bits(p.XLEN bits)) init(0) addAttribute(Verilator.public) setName("decode_intended_VTYPE_RS2_val_debug")
    val decode_intended_VTYPE_IMM_val_debug = Reg(Bits(11 bits)) init(0) addAttribute(Verilator.public) setName("decode_intended_VTYPE_IMM_val_debug")

    val rvvCoreArea = if (!externalVpu) pipeline plug new Area {
      val core = RVVCore(1, p)
      port <> core.io.port(0)
    } else null

    // --- CSR Area (Moved back before stages that use its signals) ---
    val csrArea = pipeline plug new Area {
      val vlReg = Reg(UInt(p.VL_WIDTH bits)) init(0) addAttribute(Verilator.public) setName("csr_vl")
      val vtypeBitsReg = Reg(Bits(p.XLEN bits)) init(0) addAttribute(Verilator.public) setName("csr_vtype_bits")
      val vlenbVal = U(p.VLENB, p.XLEN bits)

      val csrService = pipeline.service(classOf[CsrInterface])
      csrService.rw(CSR.VL, vlReg)
      // --- Corrected VTYPE CSR Handling using r() only ---
      // Remove previous attempts
      // csrService.rw(CSR.VTYPE, vtypeBitsReg)
      // csrService.read(CSR.VTYPE, vtypeBitsReg) 
      // csrService.readToWrite(CSR.VTYPE, vtypeBitsReg)
      
      // Use r(): Allows CSR reads from vtypeBitsReg.
      // CSR writes will likely be treated as illegal access by CsrPlugin 
      // as no 'w' or 'rw' action is registered for CSR.VTYPE.
      csrService.r(CSR.VTYPE, vtypeBitsReg)
      
      // --- End of Correction ---
      csrService.r(CSR.VLENB, vlenbVal)
    }

    decode plug new Area {
      import decode._

      // Determine instruction type early. These signals are combinational based on current instruction.
      val is_vsetvl_or_vsetvli_instr = input(IS_VSETVL_OR_VSETVLI)
      // Corrected vsetvli check: bit 25 should be 1 for vsetvli
      val is_vsetvli_instr = is_vsetvl_or_vsetvli_instr && input(INSTRUCTION)(25)

      // Default values for stageables (will be overridden if it's a vsetvl/i and firing)
      // This helps ensure they have a known state if the conditions below aren't met.
      // However, decoderService.addDefault should handle this for most.
      // Let's rely on decoderService for defaults and only insert when specific conditions are met.

      // Process only when the stage is firing to avoid using stale inputs
      when(arbitration.isFiring) {
          // Update general debug signals for the instruction currently firing
          isVsetvlDecodeDebugSignal := is_vsetvl_or_vsetvli_instr
          decode_is_vsetvli_check_val_debug := is_vsetvli_instr

          when(is_vsetvl_or_vsetvli_instr) {
              // Calculate final values for stageables based on instruction type
              val final_vtype_rs2_val = Mux(is_vsetvli_instr, B(0), input(RS2).asBits)
              val final_vtype_imm_val = Mux(is_vsetvli_instr, input(INSTRUCTION)(30 downto 20).asBits, B(0))
              val final_avl_val = input(RS1).asUInt
              val final_avl_is_vlmax_val = is_vsetvli_instr && (input(INSTRUCTION)(rs1Range) === 0)
              val final_rd_write_en_val = (input(INSTRUCTION)(rdRange) =/= 0) // rd write enabled if rd is not x0

              // Update "intended" debug signals right before potential insert
              decode_intended_IS_VSETVLI_val_debug := is_vsetvli_instr
              decode_intended_VTYPE_RS2_val_debug := final_vtype_rs2_val
              decode_intended_VTYPE_IMM_val_debug := final_vtype_imm_val

              // Insert into pipeline stageables
              insert(IS_VSETVLI) := is_vsetvli_instr
              insert(VSETVL_VTYPE_RS2) := final_vtype_rs2_val
              insert(VSETVL_VTYPE_IMM) := final_vtype_imm_val
              insert(VSETVL_AVL) := final_avl_val
              insert(VSETVL_AVL_IS_VLMAX) := final_avl_is_vlmax_val
              insert(VSETVL_RD_WRITE_EN) := final_rd_write_en_val
              insert(REGFILE_WRITE_VALID) := final_rd_write_en_val // For vsetvl/i, if rd is not x0, it writes to CSR, but also to rd.

              // Printf logic
              when(is_vsetvli_instr){
                  printf("Decode FIRING VSETVLI: inst=0x%h, is_vsetvli=%b, AVL(rs1)=0x%h, vtype_imm=0x%h\\n",
                         input(INSTRUCTION), is_vsetvli_instr, final_avl_val, final_vtype_imm_val)
              } otherwise {
                  printf("Decode FIRING VSETVL: inst=0x%h, is_vsetvli=%b, AVL(rs1)=0x%h, vtype_rs2=0x%h\\n",
                         input(INSTRUCTION), is_vsetvli_instr, final_avl_val, final_vtype_rs2_val)
              }
          } otherwise {
              // If it's firing but NOT a vsetvl/i, clear "intended" debugs for vsetvl/i specific values
              decode_intended_IS_VSETVLI_val_debug := False
              decode_intended_VTYPE_RS2_val_debug := 0
              decode_intended_VTYPE_IMM_val_debug := 0
          }
      } otherwise { // When decode stage is NOT firing (stalled or invalid)
          // Clear general debug signals if not firing
           isVsetvlDecodeDebugSignal := False
           decode_is_vsetvli_check_val_debug := False
          // Keep "intended" debug signals as they are (reflecting last firing) or clear them.
          // Clearing might be less confusing.
           decode_intended_IS_VSETVLI_val_debug := False
           decode_intended_VTYPE_RS2_val_debug := 0
           decode_intended_VTYPE_IMM_val_debug := 0
      }
    } // End decode plug

    execute plug new Area {
      import execute._

      printf("RVVPlugin Execute: p.VLEN = %h, p.ELEN = %h, p.ELEN_MAX = %h\n", U(p.VLEN), U(p.ELEN), U(p.ELEN_MAX))

      // +++ Add Verilator public attribute for debugging REQUESTED_VTYPE_RS2 and IS_VSETVLI in execute +++
      val execute_REQUESTED_VTYPE_RS2_debug = input(VSETVL_VTYPE_RS2) addAttribute(Verilator.public) setName("execute_REQUESTED_VTYPE_RS2_debug")
      val execute_IS_VSETVLI_debug = input(IS_VSETVLI) addAttribute(Verilator.public) setName("execute_IS_VSETVLI_debug")

      // <<< 修改为使用 isFiring，并打印更多输入信息 >>>
      when(arbitration.isFiring) { // 仅在阶段有效且未被暂停/刷新时打印
        printf("Execute Stage FIRING: IS_VSETVL_OR_VSETVLI=%b, REQ_AVL=0x%h, REQ_VTYPE_RS2=0x%h, REQ_VTYPE_IMM=0x%h\n",
               input(IS_VSETVL_OR_VSETVLI),
               input(VSETVL_AVL),
               input(VSETVL_VTYPE_RS2),
               input(VSETVL_VTYPE_IMM)) // 也打印 IMM
      }

      // Capture the old vl value before any modification, if rd is written
      when(input(IS_VSETVL_OR_VSETVLI) && input(VSETVL_RD_WRITE_EN)) {
        insert(EXEC_OLD_VL) := csrArea.vlReg
      } otherwise {
        insert(EXEC_OLD_VL) := U(0) // Default to 0 if not writing or not vsetvl
      }

      // 读取当前的 VTYPE 位
      val currentVtypeBits = csrArea.vtypeBitsReg
      val requestedVTypeView = VType(p) // Combinational view
      requestedVTypeView.assignFromBits(currentVtypeBits(widthOf(requestedVTypeView)-1 downto 0))

      // Determine if the incoming instruction *would* modify VTYPE (for calculation)
      // val isVsetvliForCalc = input(IS_VSETVL_OR_VSETVLI) && (input(INSTRUCTION)(rs1Range) === 0) // Original condition might be ambiguous
      val vtypeBitsForCalc = Mux(input(IS_VSETVLI), // Use the explicit flag
                                 input(VSETVL_VTYPE_IMM).resized, // imm is 11 bits, resize to XLEN
                                 input(VSETVL_VTYPE_RS2)) // rs2 is XLEN bits
      
      // +++ Make vtypeBitsForCalc public for debugging +++
      vtypeBitsForCalc.addAttribute(Verilator.public).setName("execute_vtypeBitsForCalc")

      val vtypeFromInstr = VType(p)
      // --- Remove assignFromBits ---
      // vtypeFromInstr.assignFromBits(vtypeBitsForCalc.asBits(widthOf(vtypeFromInstr)-1 downto 0))

      // --- Explicitly parse vtype bits based on Spec layout, for both vsetvl and vsetvli ---
      // Use vtypeBitsForCalc which holds either imm or rs2 value
      // Assuming XLEN=32 for now, adjust if needed. Spec Figure 6.1 vtype layout:
      // XLEN-1 | XLEN-2 .. 8 | 7  | 6  | 5:3   | 2:0
      //  vill  | Reserved    | vma| vta| vlmul | vsew
      // val sourceBits = vtypeBitsForCalc.asBits // Use the combined source // No longer using sourceBits directly like this for all fields

      // Check XLEN and adjust slicing if necessary, assuming XLEN >= 9
      // val vtypeWidthInSource = p.XLEN min 32 // Consider only relevant bits for vtype CSR in RV32 // This concept is removed for direct parsing

      // For RV32, vtype occupies lower XLEN bits. vill is at XLEN-1.
      // vtypeFromInstr.vill  := sourceBits(vtypeWidthInSource - 1)
      // vtypeFromInstr.vma   := sourceBits(7)
      // vtypeFromInstr.vta   := sourceBits(6)
      // vtypeFromInstr.vlmul := sourceBits(5 downto 3).asUInt
      // vtypeFromInstr.vsew  := sourceBits(2 downto 0).asUInt

      // --- Corrected parsing for vtypeFromInstr ---
      when(input(IS_VSETVLI)){
        val imm = input(VSETVL_VTYPE_IMM) // This is Bits(11 bits)
        vtypeFromInstr.vsew  := imm(2 downto 0).asUInt
        vtypeFromInstr.vlmul := imm(5 downto 3).asUInt
        vtypeFromInstr.vta   := imm(6)
        vtypeFromInstr.vma   := imm(7)
        vtypeFromInstr.vill  := imm(10)
      } otherwise { // vsetvl
        val rs2Val = input(VSETVL_VTYPE_RS2) // This is Bits(XLEN bits)
        vtypeFromInstr.vsew  := rs2Val(2 downto 0).asUInt
        vtypeFromInstr.vlmul := rs2Val(5 downto 3).asUInt
        vtypeFromInstr.vta   := rs2Val(6)
        vtypeFromInstr.vma   := rs2Val(7)
        vtypeFromInstr.vill  := rs2Val(p.XLEN - 1)
      }
      // --- End of corrected parsing ---

      // Calculate SEW based on the *requested* VTYPE from the instruction
      val SEW = UInt(log2Up(p.ELEN_MAX + 1) bits)
      val vsewIsReserved = Bool()
      switch(vtypeFromInstr.vsew) { // Use vtypeFromInstr here
        is(0) { SEW := 8; vsewIsReserved := False }
        is(1) { SEW := 16; vsewIsReserved := False }
        is(2) { SEW := 32; vsewIsReserved := False }
        is(3) { SEW := 64; vsewIsReserved := False }
        if (p.ELEN_MAX >= 128) { is(4) { SEW := 128; vsewIsReserved := False } } else { is(4) { SEW := 0; vsewIsReserved := True} }
        if (p.ELEN_MAX >= 256) { is(5) { SEW := 256; vsewIsReserved := False } } else { is(5) { SEW := 0; vsewIsReserved := True} }
        default { SEW := 0; vsewIsReserved := True }
      }

      // Calculate initial vlmax based on instruction's requested VLEN, SEW, LMUL
      val initialVlmax = p.VL_TYPE()
      val lmulIsFractional = vtypeFromInstr.vlmul(2) // Use vtypeFromInstr
      val lmulIsReserved = vtypeFromInstr.vlmul === 4 // Use vtypeFromInstr
      val sewIsInvalid = vsewIsReserved || (SEW > p.ELEN) || (SEW === 0 && !vsewIsReserved)
      
      val VLEN_div_SEW = Mux(sewIsInvalid, U(0, p.VL_WIDTH bits), U(p.VLEN, p.VL_WIDTH+log2Up(SEW.maxValue)+1 bits) / SEW)

      // Calculate base vlmax value, handling fractional LMUL separately
      val fractionalShift = UInt(3 bits)
      val fractionalDivisorValid = True
      switch(vtypeFromInstr.vlmul){ // Use vtypeFromInstr. vlmul encoding: 101 (5) -> mf8, 110 (6) -> mf4, 111 (7) -> mf2
          is(5) { fractionalShift := 3} // mf8 (denominator 8 = 2^3)
          is(6) { fractionalShift := 2} // mf4 (denominator 4 = 2^2)
          is(7) { fractionalShift := 1} // mf2 (denominator 2 = 2^1)
          default{fractionalShift := 0; fractionalDivisorValid := False}
      }
      val sewShiftedForFractional = SEW << fractionalShift
      val fractionalOverflow = (sewShiftedForFractional >> log2Up(p.ELEN_MAX + 1)).orR

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
      
      // --- Add Verilator public attribute for debugging isIllegal ---
      val executeIsIllegalDebug = Bool() addAttribute(Verilator.public) setName("execute_isIllegal_debug")
      
      // --- Remove Drastically Simplified isIllegal Calculation --- 
      // val villBitFromSource = vtypeBitsForCalc(p.XLEN-1) 
      // executeIsIllegalDebug := villBitFromSource
      // --- End of Drastically Simplified isIllegal --- 

      // Determine if the configuration is illegal based on instruction's request
      val isIllegal = Bool()
      // Re-assign isIllegal using the already calculated debug signal to ensure consistency
      // isIllegal := executeIsIllegalDebug // Remove this line, calculate isIllegal correctly below
      
      // --- Corrected isIllegal Calculation ---
      // 1. Parse vill correctly from source
      val villFromSource = Mux(input(IS_VSETVLI), vtypeBitsForCalc(10), vtypeBitsForCalc(p.XLEN - 1))
      
      // 2. Check reserved bits in the source operand
      // For vsetvli, check imm[9:8]. For vsetvl, check rs2[XLEN-2:8]
      val reservedBitsCheck = Mux(input(IS_VSETVLI),
                                   vtypeBitsForCalc(9 downto 8).orR,
                                   (if (p.XLEN > 8) vtypeBitsForCalc(p.XLEN - 2 downto 8).orR else False) // Handle XLEN=8 case
                                 )

      // 3. Check LMUL reserved encoding (100b)
      // lmulIsReserved = vtypeFromInstr.vlmul === 4 (Already calculated)

      // 4. Check SEW validity (reserved encoding or SEW > ELEN)
      // sewIsInvalid = vsewIsReserved || (SEW > p.ELEN) || (SEW === 0 && !vsewIsReserved) (Already calculated)
      // Simplified check: SEW=0 implies invalid/reserved vsew, or SEW > ELEN
      val sewCheck = vsewIsReserved || (SEW > p.ELEN) // SEW=0 is covered by vsewIsReserved

      // 5. Check Fractional LMUL constraint: SEW * Denominator < 8
      // lmulIsFractional = vtypeFromInstr.vlmul(2) (Already calculated)
      val fractionalCheck = Bool()
      // Calculate SEW * Denominator. Avoid multiplication by zero if SEW is invalid.
      // Need to handle potential multiplication carefully if SEW can be non-power-of-2, but here it is.
      // Use shifts for multiplication by powers of 2 (LMUL factors 2, 4, 8)
      val sew_div_lmul_factor = UInt(log2Up(p.ELEN_MAX + 1) bits)
      switch(vtypeFromInstr.vlmul) {
          is(5) { sew_div_lmul_factor := (SEW >> 3).resized } // LMUL = 1/8
          is(6) { sew_div_lmul_factor := (SEW >> 2).resized } // LMUL = 1/4
          is(7) { sew_div_lmul_factor := (SEW >> 1).resized } // LMUL = 1/2
          default { sew_div_lmul_factor := 0 } // Not fractional or invalid SEW case handled below
      }
      // Illegal if fractional LMUL is used AND (SEW is invalid OR (SEW * Denominator < 8))
      // Corrected logic: Illegal if fractional LMUL is used AND (SEW is invalid OR (SEW * Denominator < 8))
      // Denominator = 1 << fractionalShift (already calculated before)
      val sew_times_denominator = SEW << fractionalShift
      fractionalCheck := lmulIsFractional && fractionalDivisorValid && (sewCheck || (sew_times_denominator < 8))

      // Combine all checks
      isIllegal := villFromSource | reservedBitsCheck | lmulIsReserved | sewCheck | fractionalCheck
      
      // Assign to debug signal
      executeIsIllegalDebug := isIllegal
      // --- End of Corrected isIllegal Calculation ---

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
      val currentAvl = input(VSETVL_AVL)
      val effectiveAvl = Mux(input(VSETVL_AVL_IS_VLMAX), correctedVlmax, currentAvl.resize(p.VL_WIDTH))
      
      // --- Remove Drastically Simplified finalVL calculation for Debugging --- 
      // val finalVL = UInt(p.VL_WIDTH bits)
      // when(isIllegal) {
      //     finalVL := U(0xEE, p.VL_WIDTH bits) // Special value if illegal
      // } otherwise {
      //     finalVL := U(0xAA, p.VL_WIDTH bits) // Special value if legal
      // }
      // --- End of drastically simplified finalVL ---
      
      // --- Restore Correct finalVL calculation --- 
      val finalVL = UInt(p.VL_WIDTH bits)
      when(isIllegal) {
          finalVL := 0 // Set vl to 0 if configuration is illegal
      } otherwise {
          // Calculate vl = min(requested AVL, VLMAX)
          // Assuming effectiveAvl and correctedVlmax are already calculated
          finalVL := Mux(effectiveAvl < correctedVlmax, effectiveAvl, correctedVlmax)
      }
      // --- End of restored finalVL calculation ---

      val zeroVType = VType(p)
      zeroVType.vill := False
      zeroVType.vma  := False
      zeroVType.vta  := False
      zeroVType.vsew := 0
      zeroVType.vlmul := 0
      
      // The actualVType to be written is based on the instruction's request if legal
      // val actualVType = Mux(isIllegal, zeroVType, vtypeFromInstr) // Old Mux
      // when(isIllegal) {
      //   actualVType.vill := True // Ensure vill is set if illegal
      // }

      // --- Refined actualVType calculation ---
      val actualVType = VType(p)
      when(isIllegal) {
          actualVType.vsew  := 0
          actualVType.vlmul := 0 // For LMUL, ensure it's UInt compatible if it was Bits
          actualVType.vta   := False
          actualVType.vma   := False
          actualVType.vill  := True
      } otherwise {
          // If legal, actual vtype takes vsew, vlmul, vta, vma from instruction's request
          actualVType.vsew  := vtypeFromInstr.vsew
          actualVType.vlmul := vtypeFromInstr.vlmul
          actualVType.vta   := vtypeFromInstr.vta
          actualVType.vma   := vtypeFromInstr.vma
          // Crucially, if the configuration is legal, vtype.vill must be 0.
          actualVType.vill  := False
      }
      // --- End of refined actualVType ---
      
      // --- Add Verilator public attribute for debugging ---
      finalVL.addAttribute(Verilator.public)
      actualVType.addAttribute(Verilator.public) // Add to the Bundle directly

      // <<< 为 VL 计算添加详细的 printf >>>
      when(input(IS_VSETVL_OR_VSETVLI) && arbitration.isFiring) { // 使用 isFiring
        // --- 保留先前步骤的详细 printf ---
        printf("Execute Stage (vsetvl) Details: isIllegal=%b, currentAvl=0x%h, correctedVlmax=0x%h, finalVL=0x%h, reqVtypeBits=0x%h, parsedSEW=%h, parsedLMUL=%h, parsedVill=%b\n",
               isIllegal,
               currentAvl,
               correctedVlmax,
               finalVL,
               vtypeBitsForCalc, // Renamed for clarity
               vtypeFromInstr.vsew,  // Changed %d to %h
               vtypeFromInstr.vlmul, // Changed %d to %h
               vtypeFromInstr.vill)
      }

      insert(EXEC_FINAL_VL) := finalVL 
      insert(EXEC_FINAL_VTYPE) := actualVType // Insert the calculated actual vtype

      // <<< 保留现有的 final VL printf (冗余但暂时保留) >>>
      when(input(IS_VSETVL_OR_VSETVLI) && arbitration.isFiring) { // 使用 isFiring
        printf("Execute Stage (vsetvl): FinalVL=0x%h\n", finalVL) // 使用 %h
      }

      // <<< For debugging, PPRINT THE CORRECT VARIABLES >>>
      when(arbitration.isFiring && input(IS_VSETVL_OR_VSETVLI)){
          val elenConst = U(p.ELEN_MAX) // Convert p.ELEN_MAX to a UInt constant for printing
          // Using traditional printf format
          // Print all fields of actualVType
          printf("Execute (IS_VSETVL_OR_VSETVLI) DEBUG: finalVL=0x%h, actualVType(vill=%b,vma=%b,vta=%b,vsew=%h,vlmul=%h), SEW_calc=%h, ELEN_MAX=%h, isIllegal=%b\n",
                 finalVL,
                 actualVType.vill,
                 actualVType.vma,
                 actualVType.vta,
                 actualVType.vsew,  // Changed to %h
                 actualVType.vlmul, // Changed to %h
                 SEW,             // Changed to %h
                 elenConst,       // Changed to %h
                 isIllegal)
      }
    } // End of execute plug new Area

    writeBack plug new Area {
      import writeBack._ // Import writeBack._ to access stage signals

      // When IS_VSETVL_OR_VSETVLI is true and the instruction is valid and firing in the writeBack stage
      when(input(IS_VSETVL_OR_VSETVLI) && arbitration.isFiring) { // arbitration.isFiring is crucial
        
        // Handle writing rd (destination register)
        when(input(VSETVL_RD_WRITE_EN)) { // This stageable was set in decode
          output(REGFILE_WRITE_DATA) := input(EXEC_FINAL_VL).asBits.resized 
        }

        // Explicitly construct the 32-bit vtype value for the CSR
        val calculatedVl = input(EXEC_FINAL_VL)
        val calculatedVtypeBundle = input(EXEC_FINAL_VTYPE) // This is VType(p) Bundle

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
        printf("RVVPlugin:WriteBack (IS_VSETVL_OR_VSETVLI): Firing! Writing vlCSR=0x%h\n", calculatedVl)
        printf("  CalculatedVType Bundle for WriteBack: vill=%b vma=%b vta=%b vlmul=%h vsew=%h\n",
               calculatedVtypeBundle.vill,
               calculatedVtypeBundle.vma,
               calculatedVtypeBundle.vta,
               calculatedVtypeBundle.vlmul, // Changed to %h
               calculatedVtypeBundle.vsew) // Changed to %h
        printf("  Value written to vtype CSR (Bits): 0x%h\n", vtypeBitsForCsr)
      }
    } // End of writeBack plug new Area
  }

  // --- Regression Arguments ---
  override def getVexRiscvRegressionArgs(): Seq[String] = {
    var args = List[String]()
    args = args :+ s"RVV=yes"
    args = args :+ s"RVV_VLEN=${p.VLEN}"
    args = args :+ s"RVV_ELEN=${p.ELEN}"
    args = args :+ s"RVV_ELEN_MAX=${p.ELEN_MAX}"
    // Add other relevant parameters if needed
    args
  }
} 