package vexriscv.ip.vpu

import spinal.core._
import spinal.lib._

// 向量寄存器文件读端口
case class VectorRegFileReadPort(vlen: Int) extends Bundle with IMasterSlave {
  val address = UInt(5 bits)
  val enable = Bool()
  val data = Bits(vlen bits)
  
  override def asMaster(): Unit = {
    out(address, enable)
    in(data)
  }
}

// 向量寄存器文件写端口
case class VectorRegFileWritePort(vlen: Int) extends Bundle with IMasterSlave {
  val address = UInt(5 bits)
  val enable = Bool()
  val data = Bits(vlen bits)
  val mask = Bits(vlen bits) // 用于部分写入
  
  override def asMaster(): Unit = {
    out(address, enable, data, mask)
  }
}

// 向量寄存器文件组件
case class VectorRegFile(vlen: Int) extends Component {
  val io = new Bundle {
    // 读端口 (三个源向量寄存器)
    val readPort1 = slave(VectorRegFileReadPort(vlen))
    val readPort2 = slave(VectorRegFileReadPort(vlen)) 
    val readPort3 = slave(VectorRegFileReadPort(vlen))
    
    // 写端口
    val writePort = slave(VectorRegFileWritePort(vlen))
    
    // 掩码端口 (v0)
    val maskPort = slave(VectorRegFileReadPort(vlen))
  }
  
  // 32个向量寄存器，每个宽度为VLEN
  val registers = Vec(Reg(Bits(vlen bits)) init(0), 32)
  
  // 读逻辑
  io.readPort1.data := (io.readPort1.enable) ? registers(io.readPort1.address) | B(0, vlen bits)
  io.readPort2.data := (io.readPort2.enable) ? registers(io.readPort2.address) | B(0, vlen bits)
  io.readPort3.data := (io.readPort3.enable) ? registers(io.readPort3.address) | B(0, vlen bits)
  
  // 掩码读逻辑
  io.maskPort.data := (io.maskPort.enable) ? registers(io.maskPort.address) | B(0, vlen bits)
  
  // 写逻辑 - 支持掩码写
  when(io.writePort.enable) {
    // 通过掩码更新寄存器值
    registers(io.writePort.address) := (io.writePort.data & io.writePort.mask) | (registers(io.writePort.address) & ~io.writePort.mask)
  }
} 