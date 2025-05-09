# 测试中使用到的寄存器说明
# x5 (rd) - 保存 vsetvl 指令的结果 (计算出的 vl 值)
# x6 (rs1) - 保存 AVL (应用向量长度)
# x7 (rs2) - 保存 vsetvl 指令请求的 VTYPE 值
# x10 - 保存从 vl CSR 读取的值
# x11 - 保存从 vtype CSR 读取的值
# x12 - 用于比较的临时寄存器
# x28 - 保存用于比较的预期值
# x30 - 保存当前测试用例编号，用于报告失败

.section .text
.global _start

_start: # 标准入口点
rvv_vsetvl_test:
    # --- 测试设置 ---
    li x28, 0 # 初始化预期值寄存器

    # === VSETVL 测试 ===
    # VLEN=128, ELEN=64 (硬件参数)

    # 测试用例 1: 合法, SEW=8, LMUL=m1 (VLMAX=16), AVL=10 < VLMAX -> vl=10
    li x30, 1          # 设置当前测试用例编号
    li x6, 10          # rs1 = AVL = 10
    li x7, 0x00        # rs2 = VTYPE (vsew=8, vlmul=m1)
    vsetvl x5, x6, x7  # 执行 vsetvl
    csrr x10, vl       # 读取 vl CSR
    csrr x11, vtype    # 读取 vtype CSR
    li x28, 10         # 预期 vl = 10
    bne x5, x28, fail_test # 检查 x5 中的 vl
    bne x10, x28, fail_test # 检查 vl CSR
    li x28, 0x00       # 预期 vtype (vill=0)
    bne x11, x28, fail_test # 检查 vtype CSR

    # 测试用例 2: 合法, SEW=32, LMUL=m2 (VLMAX=8), AVL=10 > VLMAX -> vl=8
    li x30, 2          # 设置当前测试用例编号
    li x6, 10          # rs1 = AVL = 10
    li x7, 0x0A        # rs2 = VTYPE (vsew=32, vlmul=m2)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 8          # 预期 vl = min(10, VLMAX=(128/32)*2=8) = 8
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x0A       # 预期 vtype (vill=0)
    bne x11, x28, fail_test

    # 测试用例 3: 合法, SEW=16, LMUL=mf2 (VLMAX=4), AVL=3 < VLMAX -> vl=3
    # VTYPE: vsew=16(001), lmul=mf2(111), vta=0, vma=0 => 0b...00111001 = 0x39
    li x30, 3          # 设置当前测试用例编号
    li x6, 3           # rs1 = AVL = 3
    li x7, 0x39        # rs2 = VTYPE (SEW=16, LMUL=mf2)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 3          # 预期 vl = min(3, VLMAX=(128/16)/2=4) = 3
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x39       # 预期 vtype (vill=0)
    bne x11, x28, fail_test

    # 测试用例 4: 合法, SEW=8, LMUL=mf4 (VLMAX=4), AVL=5 > VLMAX -> vl=4
    # VTYPE: vsew=8(000), lmul=mf4(110), vta=0, vma=0 => 0b...110000 = 0x30
    li x30, 4          # 设置当前测试用例编号
    li x6, 5           # rs1 = AVL = 5
    li x7, 0x30        # rs2 = VTYPE (SEW=8, LMUL=mf4)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 4          # 预期 vl = min(5, VLMAX=(128/8)/4=4) = 4
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x30       # 预期 vtype (vill=0)
    bne x11, x28, fail_test

    # 测试用例 5: 非法, rs2 中设置了 vill 位
    li x30, 5          # 设置当前测试用例编号
    li x6, 10          # AVL = 10
    li x7, 0x80000000  # rs2 = VTYPE, vill=1 (假设 XLEN=32)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 0          # 预期 vl = 0 (因为请求非法)
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x80000000 # 预期 vtype CSR 保留 vill=1
    bne x11, x28, fail_test

    # 测试用例 6: 非法, rs2 中的保留位 bit 8 被设置
    li x30, 6          # 设置当前测试用例编号
    li x6, 10          # AVL = 10
    li x7, 0x00000100  # rs2 = VTYPE, 设置保留位 bit 8
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 0          # 预期 vl = 0
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    srli x12, x11, 31   # 提取 vtype CSR 的 vill 位 (假设 XLEN=32)
    li x28, 1           # 预期 vill = 1
    bne x12, x28, fail_test

    # 测试用例 7: 非法, LMUL 编码为保留值 (100)
    # VTYPE: vsew=8(000), lmul=reserved(100) => 0b...100000 = 0x20
    li x30, 7          # 设置当前测试用例编号
    li x6, 10          # AVL = 10
    li x7, 0x20        # rs2 = VTYPE (SEW=8, LMUL=reserved)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 0          # 预期 vl = 0
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    srli x12, x11, 31  # 检查 vtype CSR 的 vill 位
    li x28, 1          # 预期 vill = 1
    bne x12, x28, fail_test

    # 测试用例 8: 合法, 请求 SEW=64 (vsew=011), 硬件 ELEN=64
    # VTYPE: vsew=64(011), lmul=m1(000) => 0b...000011 = 0x03
    li x30, 8          # 设置当前测试用例编号
    li x6, 10          # rs1 = AVL = 10
    li x7, 0x03        # rs2 = VTYPE (SEW=64, LMUL=m1)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 2          # 预期 vl = min(10, VLMAX=(128/64)*1=2) = 2
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x03       # 预期 vtype (vill=0)
    bne x11, x28, fail_test

    # 测试用例 9: 合法, 小数 LMUL: SEW=8, LMUL=mf8. 约束 SEW*LMUL_DEN < 8 (8*8 < 8) 为假.
    # VTYPE = 0x28. VLMAX = (128/8)/8 = 2. AVL=10. vl = min(10,2) = 2.
    li x30, 9          # 设置当前测试用例编号
    li x6, 10          # AVL = 10
    li x7, 0x28        # rs2 = VTYPE (SEW=8, vlmul=mf8)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 2          # 预期 vl = 2
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x28       # 预期 vtype (vill=0)
    bne x11, x28, fail_test

    # 测试用例 10: 合法, 测试 vta=1, vma=1 位是否被正确保留
    # VTYPE: vsew=32(010), lmul=m1(000), vta=1, vma=1 => 0b...1100010 = 0xC2
    li x30, 10         # 设置当前测试用例编号
    li x6, 5           # AVL = 5
    li x7, 0xC2        # rs2 = VTYPE (SEW=32, LMUL=m1, vta=1, vma=1)
    vsetvl x5, x6, x7  # VLMAX = (128/32)*1 = 4
    csrr x10, vl
    csrr x11, vtype
    li x28, 4          # 预期 vl = min(5, VLMAX=4) = 4
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0xC2       # 预期 vtype (vill=0, vta=1, vma=1)
    bne x11, x28, fail_test

    # 测试用例 11: 合法, 测试 LMUL=m4. SEW=8 (VLMAX=64), AVL=70 > VLMAX -> vl=64
    # VTYPE: vsew=8(000), lmul=m4(010) => 0x10
    li x30, 11         # 设置当前测试用例编号
    li x6, 70          # rs1 = AVL = 70
    li x7, 0x10        # rs2 = VTYPE (SEW=8, LMUL=m4)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 64         # 预期 vl = min(70, VLMAX=(128/8)*4=64) = 64
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x10       # 预期 vtype (vill=0)
    bne x11, x28, fail_test

    # 测试用例 12: 合法, 测试 LMUL=m8 和最大 vl. SEW=8 (VLMAX=128), AVL=130 > VLMAX -> vl=128
    # VTYPE: vsew=8(000), lmul=m8(011) => 0x18
    li x30, 12         # 设置当前测试用例编号
    li x6, 130         # rs1 = AVL = 130
    li x7, 0x18        # rs2 = VTYPE (SEW=8, LMUL=m8)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 128        # 预期 vl = min(130, VLMAX=(128/8)*8=128) = 128
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x18       # 预期 vtype (vill=0)
    bne x11, x28, fail_test

    # 测试用例 13: 合法, 测试 SEW=16, LMUL=m1 (VLMAX=8), AVL=5 < VLMAX -> vl=5
    # VTYPE: vsew=16(001), lmul=m1(000) => 0x01
    li x30, 13         # 设置当前测试用例编号
    li x6, 5           # rs1 = AVL = 5
    li x7, 0x01        # rs2 = VTYPE (SEW=16, LMUL=m1)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 5          # 预期 vl = min(5, VLMAX=(128/16)*1=8) = 5
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x01       # 预期 vtype (vill=0)
    bne x11, x28, fail_test

    # 测试用例 14: 合法, 测试 SEW=16, LMUL=m2 (VLMAX=16), AVL=20 > VLMAX -> vl=16
    # VTYPE: vsew=16(001), lmul=m2(001) => 0x09
    li x30, 14         # 设置当前测试用例编号
    li x6, 20          # rs1 = AVL = 20
    li x7, 0x09        # rs2 = VTYPE (SEW=16, LMUL=m2)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 16         # 预期 vl = min(20, VLMAX=(128/16)*2=16) = 16
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x09       # 预期 vtype (vill=0)
    bne x11, x28, fail_test

    # 测试用例 15: 合法, 测试 SEW=32, LMUL=m1 (VLMAX=4), AVL=3 < VLMAX -> vl=3
    # VTYPE: vsew=32(010), lmul=m1(000) => 0x02
    li x30, 15         # 设置当前测试用例编号
    li x6, 3           # rs1 = AVL = 3
    li x7, 0x02        # rs2 = VTYPE (SEW=32, LMUL=m1)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 3          # 预期 vl = min(3, VLMAX=(128/32)*1=4) = 3
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x02       # 预期 vtype (vill=0)
    bne x11, x28, fail_test

    # 测试用例 16: 合法, 测试 SEW=64, LMUL=m1 (VLMAX=2), AVL=1 < VLMAX -> vl=1
    # VTYPE: vsew=64(011), lmul=m1(000) => 0x03
    li x30, 16         # 设置当前测试用例编号
    li x6, 1           # rs1 = AVL = 1
    li x7, 0x03        # rs2 = VTYPE (SEW=64, LMUL=m1)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 1          # 预期 vl = min(1, VLMAX=(128/64)*1=2) = 1
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x03       # 预期 vtype (vill=0)
    bne x11, x28, fail_test

    # === 所有 vsetvl 测试结束 ===

    # === 最终成功 ===
    li a0, 1           # 成功代码为 1
    j end_test

fail_test:
    # 加载测试用例编号到 a0 (高位为1表示失败: test_num << 1)
    # 跳转到此处前，x30 应保存着失败的测试用例编号
    slli a0, x30, 1
    # 继续执行到 end_test

end_test:
    # 将结果码写入 WorkspaceRegression 期望的 tohost 地址
    # 需要确保仿真环境中的 C++ 代码 (RVVTest::dBusAccess) 使用了正确的 TOHOST_ADDR
    li x1, 0xF0010000 # 使用 WorkspaceRegression 期望的地址
    sw a0, 0(x1)      # 写入结果码 (成功时 a0=1, 失败时 a0 = test_num*2)

    # 无限循环
1:  j 1b


.section .data
.align 4
tohost: .word 0
fromhost: .word 0 