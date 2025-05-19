.section .text
.globl _start
_start:
    # 测试vsetvli指令: SEW=32, LMUL=1/2
    # vsetvli t0, a0, e32,mf2,ta,ma 
    # VLMAX = (VLEN/32)*(1/2) = 256/32*0.5 = 4
    # 预期结果：当AVL=10时，vl=4，t0=4，vtype=0xD7

    li x28, 4           # 测试ID
    li x10, 10          # a0 = AVL 请求 = 10
    
    # 对于e32, mf2(lmul=1/2), ta, ma:
    # vill = 0 (zimm[10] = 0)
    # 保留位 = 0 (zimm[9:8] = 00)
    # vma = 1 (zimm[7] = 1)
    # vta = 1 (zimm[6] = 1)
    # vsew = 010 (e32) (zimm[5:3] = 010)
    # vlmul = 111 (mf2) (zimm[2:0] = 111)
    # zimm[10:0] = 0_00_1_1_010_111 = 0x0D7

    # vsetvli t0, a0, zimm=0x0D7
    .word (0x0D7 << 20) | (10 << 15) | (0x7 << 12) | (5 << 7) | 0x57  # vsetvli t0, x10, vtypei=0x0D7

    # 检查vl (在t0 (x5)和CSR_VL (0xC20)中)
    li x6, 4            # 预期vl = 4
    bne x5, x6, fail    # 检查t0 (rd)

    csrr x7, 0xC20      # 读取vl CSR到x7
    bne x7, x6, fail    # 检查vl CSR

    # 检查vtype (CSR_VTYPE (0xC21))
    li x6, 0xD7         # 预期vtype: 0b11010111 (vma=1, vta=1, vsew=010, vlmul=111)
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