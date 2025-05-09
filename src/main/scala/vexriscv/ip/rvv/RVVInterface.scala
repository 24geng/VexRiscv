package vexriscv.ip.rvv

import spinal.core._
import spinal.lib._

// --- Enums ---

// Defines the Vector Operation Opcodes recognized by RVVPlugin/RVVCore
object RVVOpcode extends SpinalEnum {
  val VSETVL, VSETVLI = newElement()
  // TODO: Add other RVV opcodes (VECTOR_LOAD, VECTOR_STORE, VECTOR_ARITH, etc.)
}

// TODO: Define other necessary enums like Masking Modes, Width Encodings if needed

// --- Configuration ---

// Parameters for configuring the RVV Unit (VPU)
case class RVVParameter(
    VLEN: Int = 128, // Vector Register Length in bits (e.g., 128, 256)
    ELEN: Int = 64,  // Maximum Element Width supported (e.g., 64 for RV64V, could be 32)
    XLEN: Int = 32,  // Base Integer Architecture Width (usually 32 for VexRiscv)
    VLENB: Int,    // Removed default value: = VLEN / 8
    ELEN_MAX: Int // Removed default value: = ELEN
    // TODO: Add flags for supported features (Floating Point, specific op groups, etc.)
) {
  require(VLEN > 0 && (VLEN & (VLEN - 1)) == 0, "VLEN must be a power of 2")
  require(ELEN >= 8 && (ELEN & (ELEN - 1)) == 0, "ELEN must be a power of 2")
  require(XLEN == 32 || XLEN == 64, "XLEN must be 32 or 64")
  require(VLEN >= ELEN, "VLEN must be greater than or equal to ELEN")

  val vlenb = VLEN / 8
  val VL_WIDTH = log2Up(VLEN + 1) // Width for vl CSR
  val AVL_TYPE = HardType(UInt(XLEN bits)) // Type for Requested Application Vector Length (avl)
  val VL_TYPE = HardType(UInt(VL_WIDTH bits)) // Type for Vector Length CSR (vl)
  val XLEN_TYPE = HardType(UInt(XLEN bits)) // Type for general XLEN registers
  val VREG_ADDR_WIDTH = 5 // Vector registers v0-v31
  val REG_ADDR_WIDTH = 5  // Integer registers x0-x31
}

// --- Data Structures ---

// Represents the VTYPE CSR content
case class VType(p: RVVParameter) extends Bundle {
  val vill = Bool() // Illegal type indicator (set if reserved encoding used)
  val vma = Bool()  // Vector Mask Agnostic
  val vta = Bool()  // Vector Tail Agnostic
  val vsew = UInt(3 bits) // Standard Element Width (000=8b, 001=16b, 010=32b, 011=64b, ...)
  val vlmul = UInt(3 bits)// Vector Register Grouping (000=1, 001=2, ..., 101=1/8)

  // Helper functions can be added here to decode vsew/vlmul
}

// TODO: Define structures for internal vector representation if needed, similar to FpuFloat

// --- Communication Bundles ---

// Command sent from RVVPlugin to RVVCore
case class RVVCmd(p: RVVParameter) extends Bundle {
  val opcode = RVVOpcode()
  // For VSETVL(I): requested AVL (from rs1 or 0), requested VType (from rs2 or imm)
  val rs1Val = p.XLEN_TYPE()
  val rs2Val = p.XLEN_TYPE() // Or immediate value for VSETVLI needs careful handling
  val imm = Bits(11 bits)   // For VSETVLI (imm[10] is vill, imm[9:8] reserved, imm[7] vma, imm[6] vta, imm[5:3] vlmul, imm[2:0] vsew)
  val rd = UInt(p.REG_ADDR_WIDTH bits) // Destination integer register for vl

  // TODO: Add fields for vector operations (vs1, vs2, vd, vm, funct6, etc.)
}

// Response sent from RVVCore to RVVPlugin (for results going to Integer RF)
case class RVVResp(p: RVVParameter) extends Bundle {
  val data = p.XLEN_TYPE() // Data to write back (e.g., calculated vl)
  // TODO: Add exception flags if needed (e.g., for illegal vtype)
}

// Placeholder for completion signal (similar to FpuCompletion)
case class RVVCompletion(p: RVVParameter) extends Bundle {
  // TODO: Define flags (e.g., vxsat - fixed-point saturation)
  val written = Bool() // Indicates if a vector register was written
}

// Placeholder for data commitment (similar to FpuCommit)
case class RVVCommit(p: RVVParameter) extends Bundle {
  // TODO: Define structure for committing data (e.g., for vector stores)
}


// --- Main Interface Port ---

case class RVVPort(p: RVVParameter) extends Bundle with IMasterSlave {
  // Plugin -> Core
  val cmd = Stream(RVVCmd(p))
  val commit = Stream(RVVCommit(p)) // Placeholder

  // Core -> Plugin
  val rsp = Stream(RVVResp(p))
  val completion = Flow(RVVCompletion(p)) // Placeholder

  override def asMaster(): Unit = {
    master(cmd, commit)
    slave(rsp)
    in(completion)
  }
} 