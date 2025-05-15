package vexriscv.demo

import vexriscv._
import vexriscv.plugin._
import vexriscv.ip.vpu._
import spinal.core._
import spinal.lib._

/**
 * 包含向量处理单元的VexRiscv配置示例
 */
object VexRiscvWithVpu {
  def main(args: Array[String]): Unit = {
    val report = SpinalVerilog {
      // 创建一个包含VPU的VexRiscv处理器配置
      val cpuConfig = VexRiscvConfig(
        plugins = List(
          new PcManagerSimplePlugin(
            resetVector = 0x80000000l,
            relaxedPcCalculation = false
          ),
          new IBusSimplePlugin(
            resetVector = 0x80000000l,
            cmdForkOnSecondStage = false,
            cmdForkPersistence = false,
            prediction = NONE,
            catchAccessFault = false,
            compressedGen = false
          ),
          new DBusSimplePlugin(
            catchAddressMisaligned = false,
            catchAccessFault = false
          ),
          new CsrPlugin(CsrPluginConfig.small(0x80000020l)),
          new DecoderSimplePlugin(
            catchIllegalInstruction = true
          ),
          new RegFilePlugin(
            regFileReadyKind = plugin.SYNC,
            zeroBoot = false
          ),
          new IntAluPlugin(),
          new SrcPlugin(
            separatedAddSub = false,
            executeInsertion = true
          ),
          new LightShifterPlugin(),
          new HazardSimplePlugin(
            bypassExecute = true,
            bypassMemory = true,
            bypassWriteBack = true,
            bypassWriteBackBuffer = true,
            pessimisticUseSrc = false,
            pessimisticWriteRegFile = false,
            pessimisticAddressMatch = false
          ),
          new BranchPlugin(
            earlyBranch = false,
            catchAddressMisaligned = false
          ),
          new MulDivIterativePlugin(
            genMul = true,
            genDiv = true,
            mulUnrollFactor = 1,
            divUnrollFactor = 1
          ),
          // 添加VPU插件
          new VpuPlugin(VpuParameter(
            vlenb = 16, // 128位向量长度
            elen = 32,  // 最大元素宽度为32位
            vlmax = 256 // 最大向量长度为128个元素
          ))
        )
      )
      
      // 实例化VexRiscv处理器
      val cpu = new VexRiscv(cpuConfig)
      
      // 为生成的CPU执行一些修改，如重命名以便于集成
      cpu.setDefinitionName("VexRiscv")
      cpu
    }
    
    // 打印报告
    report.printPruned()
  }
  
  // 用于创建一个具有标准外设的SoC的工厂方法
  def createVexRiscvWithVpu() = {
    // 创建一个包含VPU的VexRiscv处理器配置
    val cpuConfig = VexRiscvConfig(
      plugins = List(
        new PcManagerSimplePlugin(
          resetVector = 0x80000000l,
          relaxedPcCalculation = false
        ),
        new IBusSimplePlugin(
          resetVector = 0x80000000l,
          cmdForkOnSecondStage = false,
          cmdForkPersistence = false,
          prediction = NONE,
          catchAccessFault = false,
          compressedGen = false
        ),
        new DBusSimplePlugin(
          catchAddressMisaligned = false,
          catchAccessFault = false
        ),
        new CsrPlugin(CsrPluginConfig.small(0x80000000l)),
        new DecoderSimplePlugin(
          catchIllegalInstruction = true
        ),
        new RegFilePlugin(
          regFileReadyKind = plugin.SYNC,
          zeroBoot = false
        ),
        new IntAluPlugin(),
        new SrcPlugin(
          separatedAddSub = false,
          executeInsertion = true
        ),
        new LightShifterPlugin(),
        new HazardSimplePlugin(
          bypassExecute = true,
          bypassMemory = true,
          bypassWriteBack = true,
          bypassWriteBackBuffer = true,
          pessimisticUseSrc = false,
          pessimisticWriteRegFile = false,
          pessimisticAddressMatch = false
        ),
        new BranchPlugin(
          earlyBranch = false,
          catchAddressMisaligned = false
        ),
        new MulDivIterativePlugin(
          genMul = true,
          genDiv = true,
          mulUnrollFactor = 1,
          divUnrollFactor = 1
        ),
        // 添加VPU插件 (保持这个注释掉，因为我们主要关注main函数的配置)
        /*, 
        new VpuPlugin(VpuParameter(
          vlenb = 16, // 128位向量长度
          elen = 32,  // 最大元素宽度为32位
          vlmax = 128 // 最大向量长度为128个元素
        ))
        */
      )
    )
    
    // 实例化VexRiscv处理器
    val cpu = new VexRiscv(cpuConfig)
    cpu
  }
} 