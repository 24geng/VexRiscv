.section .text
.globl _start
_start:
    # 测试vsetvli指令: SEW=64, LMUL=1/4
    # vsetvli t0, a0, e64,mf4,ta,ma 
    # VLMAX = (VLEN/64)*(1/4) = 256/64*0.25 = 1
    # 预期结果：当AVL=10时，vl=1，t0=1，vtype=0xEE

    li x28, 5           # 测试ID
    li x10, 10          # a0 = AVL 请求 = 10
    
    # 对于e64, mf4(lmul=1/4), ta, ma:
    # vill = 0 (zimm[10] = 0)
    # 保留位 = 0 (zimm[9:8] = 00)
    # vma = 1 (zimm[7] = 1)
    # vta = 1 (zimm[6] = 1)
    # vsew = 011 (e64) (zimm[5:3] = 011)
    # vlmul = 110 (mf4) (zimm[2:0] = 110)
    # zimm[10:0] = 0_00_1_1_011_110 = 0x0EE

    .word (0x0EE << 20) | (10 << 15) | (0x7 << 12) | (5 << 7) | 0x57  # vsetvli t0, x10, vtypei=0x0EE
    
    # 检查vl (在t0 (x5)和CSR_VL (0xC20)中)
    li x6, 1            # 预期vl = 1
    bne x5, x6, fail    # 检查t0 (x5)
    
    # 检查CSR_VL
    csrr x7, 0xC20      # 读取vl CSR到x7
    bne x7, x6, fail    # 检查vl CSR
    
    # 检查vtype (CSR_VTYPE (0xC21))
    li x6, 0xEE         # 预期vtype: 0xEE (vma=1, vta=1, vsew=011, vlmul=110)
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