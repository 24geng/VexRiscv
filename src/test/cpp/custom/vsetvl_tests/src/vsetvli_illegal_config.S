.section .text
.globl _start
_start:
    # 测试vsetvli指令：非法配置
    # 测试一个保留的vsew值(100)和保留的vlmul值(100)
    # 预期行为: vill标志位置1，vtype其他位为0，vl设置为0

    li x28, 6           # 测试ID
    li x10, 10          # a0 = AVL 请求 = 10
    
    # 对于非法配置:
    # vill = 0 (zimm[10] = 0) - 将在执行时被设置为1
    # 保留位 = 0 (zimm[9:8] = 00)
    # vma = 1 (zimm[7] = 1)
    # vta = 1 (zimm[6] = 1)
    # vsew = 100 (非法值) (zimm[5:3] = 100)
    # vlmul = 100 (非法值) (zimm[2:0] = 100)
    # zimm[10:0] = 0_00_1_1_100_100 = 0x0E4

    .word (0x0E4 << 20) | (10 << 15) | (0x7 << 12) | (5 << 7) | 0x57  # vsetvli t0, x10, vtypei=0x0E4

    # 检查vl (在t0 (x5)和CSR_VL (0xC20)中) - 应该为0
    li x6, 0            # 预期vl = 0
    bne x5, x6, fail    # 检查t0 (x5)
    
    # 检查CSR_VL
    csrr x7, 0xC20      # 读取vl CSR到x7
    bne x7, x6, fail    # 检查vl CSR
    
    # 检查vtype (CSR_VTYPE (0xC21)) - 应该为0x80000000 (vill=1)
    li x6, 0x80000000   # 预期vtype: 0x80000000 (vill=1, 其他位=0)
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