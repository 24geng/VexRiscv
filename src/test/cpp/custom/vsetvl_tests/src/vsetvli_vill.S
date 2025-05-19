.section .text
.globl _start
_start:
    # 测试vsetvli指令: 测试设置vill位
    # 这个测试首先故意使用非法配置设置vill=1
    # 然后尝试用合法配置恢复，检查vill位是否正确清零

    li x28, 8           # 测试ID
    li x10, 10          # a0 = AVL 请求 = 10
    
    # 步骤1: 使用非法配置，设置vill=1
    # 对于非法配置:
    # zimm[10:0] = 0_00_1_1_100_100 = 0x0E4
    .word (0x0E4 << 20) | (10 << 15) | (0x7 << 12) | (5 << 7) | 0x57  # vsetvli t0, x10, 非法配置

    # 检查vill位是否正确设置
    li x6, 0x80000000   # 预期vtype: 0x80000000 (vill=1, 其他位=0)
    csrr x7, 0xC21      # 读取vtype CSR到x7
    bne x7, x6, fail    # 检查vtype CSR
    
    # 检查vl是否为0
    li x6, 0            # 预期vl = 0
    csrr x7, 0xC20      # 读取vl CSR到x7
    bne x7, x6, fail    # 检查vl CSR
    
    # 步骤2: 使用合法配置，vill位应该被清零
    # 对于e32, m1, ta, ma:
    # zimm[10:0] = 0_00_1_1_010_000 = 0x0D0
    .word (0x0D0 << 20) | (10 << 15) | (0x7 << 12) | (6 << 7) | 0x57  # vsetvli t1, x10, 合法配置
    
    # 检查vl是否正确设置
    li x6, 8            # 预期vl = 8
    csrr x7, 0xC20      # 读取vl CSR到x7
    bne x7, x6, fail    # 检查vl CSR
    
    # 检查vtype是否正确设置，vill位应该为0
    li x6, 0xD0         # 预期vtype: 0b11010000 (vma=1, vta=1, vsew=010, vlmul=000)
    csrr x7, 0xC21      # 读取vtype CSR到x7
    bne x7, x6, fail    # 检查vtype CSR
    
    j pass

fail:
    li x2, 0xF00FFF24   # 失败地址
    sw x28, 0(x2)
    j end_test

pass:
    li x2, 0xF00FFF20   # 通过地址
    sw x0, 0(x2)
    j end_test

end_test:
    # 无限循环
    j end_test 