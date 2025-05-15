package vexriscv.ip.vpu

import spinal.core._
import spinal.lib._

// RISC-V向量扩展的操作码定义
object VpuOpcode extends SpinalEnum {
  // 配置指令
  val VSETVLI, VSETIVLI, VSETVL = newElement()
  
  // 加载/存储指令
  val VLE, VSE, VLSE, VLUXEI, VLOXEI, VSUXEI, VSOXEI = newElement()
  
  // 算术指令
  val VADD, VSUB, VAND, VOR, VXOR = newElement()
  val VADC, VMADC, VSBC, VMSBC = newElement()
  val VMIN, VMAX, VMINU, VMAXU = newElement()
  
  // 乘法指令
  val VMUL, VMULH, VMULHU, VMULHSU = newElement()
  
  // 除法指令
  val VDIV, VDIVU, VREM, VREMU = newElement()
  
  // 浮点运算
  val VFADD, VFSUB, VFMUL, VFDIV, VFMACC, VFNMACC = newElement()
  
  // 移位指令
  val VSLL, VSRL, VSRA = newElement()
  
  // 掩码指令
  val VMSEQ, VMSNE, VMSLT, VMSGT, VMSLE, VMSGE = newElement()
  
  // 其他指令
  val VMERGE = newElement()
}

// VPU参数配置
case class VpuParameter(
  vlenb: Int = 16,                   // 向量长度(字节)
  elen: Int = 32,                    // 最大元素宽度
  vlmax: Int = 256,                  // 最大向量长度
  withIntegerArith: Boolean = true,  // 整数算术支持
  withFloatArith: Boolean = false,   // 浮点算术支持
  withReduction: Boolean = true,      // 规约操作支持
  XLEN: Int = 32                    // Width of scalar datapath (e.g., 32 for RV32)
) {
  val vlen = vlenb * 8               // 向量长度(位)
}

// VPU状态输出Bundle
case class VpuStatusBundle(p: VpuParameter) extends Bundle with IMasterSlave {
  val vl = UInt(log2Up(p.vlen + 1) bits)
  val vtype = Bits(p.XLEN bits)
  
  override def asMaster(): Unit = {
    out(vl, vtype)
  }
  // asSlave is not strictly needed if only used as master, but good practice for IMasterSlave
  override def asSlave(): Unit = {
    in(vl,vtype) // This direction would be for inputs, which is not the case for status
                 // For a status bundle that is purely output, asMaster is sufficient 
                 // and asSlave could be left to default or explicitly make them out for slave (which is weird)
                 // Let's ensure slave means they are inputs to the component owning the slave port.
                 // So, if a component has `slave(VpuStatusBundle)`, it would mean it receives vl/vtype.
                 // SpinalHDL might default to reversing direction. Let's stick to out() in asMaster.
  }
}

// 向量指令
case class VpuCmd(p: VpuParameter) extends Bundle {
  val opcode = VpuOpcode()           // 向量操作码
  val vs1 = UInt(5 bits)             // 源寄存器1
  val vs2 = UInt(5 bits)             // 源寄存器2
  val vs3 = UInt(5 bits)             // 源寄存器3
  val vd = UInt(5 bits)              // 目标寄存器
  val vm = Bool()                    // 掩码位
  val rs1Data = Bits(p.XLEN bits)    // 新增：用于传递rs1的值 (例如 AVL)
  val zimm = Bits(11 bits)           // 立即数(用于vsetvli)
  val rs2DataForVtype = Bits(32 bits) // 新增：用于vsetvl时传递rs2的值作为vtypei
  val funct3 = Bits(3 bits)          // 操作功能码
  val funct6 = Bits(6 bits)          // 操作功能码
}

// VPU完成信号
case class VpuCompletion() extends Bundle {
  val valid = Bool()                 // 完成有效
  val rd = UInt(5 bits)              // 目标寄存器
  val value = Bits(32 bits)          // 32位值(用于回写)
}

// VPU端口
case class VpuPort(p: VpuParameter) extends Bundle with IMasterSlave {
  val cmd = Stream(VpuCmd(p))        // 指令流
  val completion = Flow(VpuCompletion()) // 完成信号
  val busy = Bool()                  // 忙状态
  
  override def asMaster(): Unit = {
    master(cmd)
    slave(completion)
    in(busy)
  }
} 