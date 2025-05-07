package vexriscv.demo

import vexriscv.plugin._
import vexriscv.ip.{DataCacheConfig, InstructionCacheConfig}
import vexriscv.{plugin, VexRiscv, VexRiscvConfig}
import spinal.core._
import spinal.lib._ // Required for master/slave, Stream, etc.
import spinal.lib.com.jtag.{Jtag, JtagTapInstructionCtrl} // Include JtagTapInstructionCtrl
// import spinal.lib.system.debugger.{SystemDebugger, SystemDebuggerConfig, JtagBridge, SystemDebuggerMemBus} // No longer needed directly here
import vexriscv.ip.rvv.RVVParameter

/**
 * Created by spinalvm on 15.06.17.
 */
object GenFull extends App{
  def cpu() = {
    val rvvParam = RVVParameter(VLEN = 128, ELEN = 64, XLEN = 32)
    val simpleBusResetVector = 80000000 // Use a simple reset vector for IBusSimplePlugin

    val config = VexRiscvConfig(
    plugins = List(
        new IBusSimplePlugin(
          resetVector = 0x80000000L,
          cmdForkOnSecondStage = false,
          cmdForkPersistence = false,
          prediction = NONE,
          catchAccessFault = true, // Keep basic fault catching if desired
          compressedGen = false,
          injectorStage = true,    // Enable instruction injection
          busLatencyMin = 1
        ),
        new DBusSimplePlugin(
          catchAddressMisaligned = true, // Keep basic checks
          catchAccessFault = true,
          earlyInjection = false // Use default
      ),
      new DecoderSimplePlugin(
        catchIllegalInstruction = true
      ),
      new RegFilePlugin(
        regFileReadyKind = plugin.SYNC,
        zeroBoot = false
      ),
      new IntAluPlugin,
      new SrcPlugin(
        separatedAddSub = false,
        executeInsertion = true
      ),
      new FullBarrelShifterPlugin,
      new HazardSimplePlugin(
        bypassExecute           = true,
        bypassMemory            = true,
        bypassWriteBack         = true,
        bypassWriteBackBuffer   = true,
        pessimisticUseSrc       = false,
        pessimisticWriteRegFile = false,
        pessimisticAddressMatch = false
      ),
      new MulPlugin,
      new DivPlugin,
        new CsrPlugin(CsrPluginConfig.small(mtvecInit = 0x80000020l)),
      new BranchPlugin(
        earlyBranch = false,
        catchAddressMisaligned = true
      ),
        new YamlPlugin("cpu0.yaml"),
        new RVVPlugin(p = rvvParam)
    )
  )

    new VexRiscv(
    config
  )
  }

  SpinalVerilog(cpu())
}
