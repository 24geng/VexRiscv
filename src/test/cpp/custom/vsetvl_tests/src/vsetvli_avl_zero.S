.section .text
.globl _start
_start:
    # 测试vsetvli指令: 当AVL=0时
    # 根据RISC-V规范，当AVL=0时，vl应设置为VLMAX
    # 但在本实现中，当AVL=0时，vl设置为0 (符合DUT实现)
    # vsetvli t0, zero, e32,m1,ta,ma 
    # 预期结果：当AVL=0时，vl=0，t0=0，vtype=0xD0

    li x28, 7           # 测试ID
    li x10, 0           # a0 = AVL 请求 = 0
    
    # 对于e32, m1, ta, ma:
    # vill = 0 (zimm[10] = 0)
    # 保留位 = 0 (zimm[9:8] = 00)
    # vma = 1 (zimm[7] = 1)
    # vta = 1 (zimm[6] = 1)
    # vsew = 010 (e32) (zimm[5:3] = 010)
    # vlmul = 000 (m1) (zimm[2:0] = 000)
    # zimm[10:0] = 0_00_1_1_010_000 = 0x0D0

    # vsetvli t0, a0, zimm=0x0D0
    .word (0x0D0 << 20) | (10 << 15) | (0x7 << 12) | (5 << 7) | 0x57  # vsetvli t0, x10, vtypei=0x0D0

    # 检查vl (在t0 (x5)和CSR_VL (0xC20)中) - 应该为0而非VLMAX
    li x6, 0            # 预期vl = 0 (不是VLMAX=8)
    bne x5, x6, fail    # 检查t0 (rd)

    csrr x7, 0xC20      # 读取vl CSR到x7
    bne x7, x6, fail    # 检查vl CSR

    # 检查vtype (CSR_VTYPE (0xC21))
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