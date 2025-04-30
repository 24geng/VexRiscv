package vexriscv.plugin

import spinal.core._
import spinal.lib._
import vexriscv._
import vexriscv.ip.rvv._ // Import RVV Interface definitions
import vexriscv.Riscv // Import Riscv constants

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

      // Only insert specific values when it's actually VSETVL/VSETVLI
      // Defaults are handled by decoderService.addDefault now
      when(input(IS_VSETVL)) {
        insert(REQUESTED_AVL) := input(RS1).asUInt
        insert(REQUESTED_VTYPE_RS2) := input(RS2).asUInt // For VSETVL
        insert(REQUESTED_VTYPE_IMM) := input(INSTRUCTION)(30 downto 20) // imm[10:0] for VSETVLI
      }
    }

    execute plug new Area {
      import execute._

      // Read the current VTYPE bits from the CSR register
      // Note: Reading vtypeBitsReg directly assumes CsrPlugin makes the value available
      // combinationally in execute for reads. If it uses pipelineCsrRead=true,
      // we might need adjustment, but GenFull default is false.
      val currentVtypeBits = csrArea.vtypeBitsReg
      val requestedVTypeView = VType(p) // Combinational view
      requestedVTypeView.assignFromBits(currentVtypeBits(widthOf(requestedVTypeView)-1 downto 0))

      // Determine if the incoming instruction *would* modify VTYPE (for calculation)
      // This logic remains the same, but operates on inputs/instruction bits
      val isVsetvliForCalc = input(IS_VSETVL) && (input(INSTRUCTION)(rs1Range) === 0)
      val vtypeBitsForCalc = isVsetvliForCalc ? input(REQUESTED_VTYPE_IMM).asUInt.resized | input(REQUESTED_VTYPE_RS2)
      val vtypeFromInstr = VType(p)
      // Cast vtypeBitsForCalc to Bits before slicing and assigning
      vtypeFromInstr.assignFromBits(vtypeBitsForCalc.asBits(widthOf(vtypeFromInstr)-1 downto 0))
      when(isVsetvliForCalc) {
        vtypeFromInstr.vill := input(REQUESTED_VTYPE_IMM)(10)
        vtypeFromInstr.vma  := input(REQUESTED_VTYPE_IMM)(7)
        vtypeFromInstr.vta  := input(REQUESTED_VTYPE_IMM)(6)
        vtypeFromInstr.vlmul:= input(REQUESTED_VTYPE_IMM)(5 downto 3).asUInt
        vtypeFromInstr.vsew := input(REQUESTED_VTYPE_IMM)(2 downto 0).asUInt
      }
      
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
      
      insert(CALCULATED_VL) := finalVL 
      insert(CALCULATED_VTYPE) := actualVType // Insert the calculated actual vtype
    }

    // --- Memory Stage Logic --- 
    // Remove this area as the pipeline build mechanism handles the propagation automatically
    // when inserts are used correctly in the previous stage.
    /*
    memory plug new Area {
      import memory._
      // Always propagate stageables read by WriteBack stage or CSR area
      output(CALCULATED_VL)     := input(CALCULATED_VL)
      output(CALCULATED_VTYPE)   := input(CALCULATED_VTYPE)
      output(IS_VSETVL)         := input(IS_VSETVL)
      output(WRITE_RD_FROM_VL)  := input(WRITE_RD_FROM_VL)
    }
    */

    // --- WriteBack Stage Logic ---
    writeBack plug new Area {
      import writeBack._

      when(input(WRITE_RD_FROM_VL)) {
        output(REGFILE_WRITE_DATA) := input(CALCULATED_VL).asBits.resized
      }

      // Update CSRs from VSETVL instruction
      when(arbitration.isFiring && input(IS_VSETVL)) {
        csrArea.vlReg := input(CALCULATED_VL)
        // Update vtypeBitsReg directly with the calculated VTYPE bits
        csrArea.vtypeBitsReg := input(CALCULATED_VTYPE).asBits.resize(p.XLEN)
      }
    }
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