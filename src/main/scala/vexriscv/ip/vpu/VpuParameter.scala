case class VpuParameter(
  vlen : Int = 256,  // 向量寄存器宽度（位）
  elen : Int = 64,   // 元素最大宽度（位）
  XLEN : Int = 32    // 标量寄存器宽度（位）
) {
  require(vlen % 8 == 0, "VLEN必须是8的倍数")
} 