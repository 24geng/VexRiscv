package vexriscv.ip.vpu

import spinal.core._
import spinal.lib._

// 向量配置寄存器
case class VectorConfig() extends Bundle {
  // vtype寄存器字段 - 使用默认值而不是init
  val vill = Bool() default(False)         // 非法配置标志
  val vma = Bool() default(False)          // 内存访问对齐标志
  val vta = Bool() default(False)          // 尾部非活动元素处理标志
  val vlmul = Bits(3 bits) default(B"000") // LMUL字段 (向量寄存器分组)
  val vsew = Bits(3 bits) default(B"000")  // SEW字段 (元素宽度)
  val reserved = Bits(23 bits) default(0)  // 保留位
  
  // 派生值 (不是寄存器的一部分，但很有用)
  def getLMUL(): UInt = {
    val result = UInt(4 bits)
    switch(vlmul) {
      is(B"000") { result := 1 }     // LMUL = 1
      is(B"001") { result := 2 }     // LMUL = 2
      is(B"010") { result := 4 }     // LMUL = 4
      is(B"011") { result := 8 }     // LMUL = 8
      is(B"100") { result := 8 }     // LMUL = 1/8 (分子=1, 分母=8)
      is(B"101") { result := 4 }     // LMUL = 1/4 (分子=1, 分母=4)
      is(B"110") { result := 2 }     // LMUL = 1/2 (分子=1, 分母=2)
      default { result := 0 }        // 无效
    }
    result
  }
  
  def getLMULDenom(): UInt = {
    val result = UInt(4 bits)
    switch(vlmul) {
      is(B"000") { result := 1 }     // LMUL = 1
      is(B"001") { result := 1 }     // LMUL = 2
      is(B"010") { result := 1 }     // LMUL = 4
      is(B"011") { result := 1 }     // LMUL = 8
      is(B"100") { result := 8 }     // LMUL = 1/8
      is(B"101") { result := 4 }     // LMUL = 1/4
      is(B"110") { result := 2 }     // LMUL = 1/2
      default { result := 1 }        // 无效
    }
    result
  }
  
  def getLMULNum(): UInt = {
    val result = UInt(4 bits)
    switch(vlmul) {
      is(B"000") { result := 1 }     // LMUL = 1
      is(B"001") { result := 2 }     // LMUL = 2
      is(B"010") { result := 4 }     // LMUL = 4
      is(B"011") { result := 8 }     // LMUL = 8
      is(B"100") { result := 1 }     // LMUL = 1/8
      is(B"101") { result := 1 }     // LMUL = 1/4
      is(B"110") { result := 1 }     // LMUL = 1/2
      default { result := 0 }        // 无效
    }
    result
  }
  
  def isFractional(): Bool = vlmul(2)
  
  def getSEW(): UInt = {
    val result = UInt(8 bits)
    switch(vsew) {
      is(B"000") { result := 8 }     // SEW = 8位
      is(B"001") { result := 16 }    // SEW = 16位
      is(B"010") { result := 32 }    // SEW = 32位
      is(B"011") { result := 64 }    // SEW = 64位
      default { result := 0 }        // 无效
    }
    result
  }
  
  // 将配置编码为vtype寄存器值
  def encode(): Bits = {
    vill ## vma ## vta ## vlmul ## vsew ## reserved
  }
}

// 向量CSR寄存器组
case class VectorCSR() extends Bundle {
  val vtype = VectorConfig()         // 向量类型配置
  val vl = UInt(32 bits) default(0)  // 向量长度寄存器
  val vstart = UInt(32 bits) default(0) // 向量起始索引
  val vxsat = Bool() default(False)  // 向量饱和标志
  val vxrm = Bits(2 bits) default(0) // 向量定点舍入模式
} 