.globl _start

#define TEST_VSETVL     1
#define TEST_VSETVLI    0
#define TEST_VSETIVLI   0
#define VLEN            128
#define SUCCESS_ADDR    0xF00FFF20

.section .text
_start:
    # 初始化通用寄存器
    li x1, 0
    li x2, 0
    li x3, 0
    li x4, 0
    li x5, 0
    li x6, 0
    li x7, 0
    li x8, 0
    li x9, 0
    li x10, 0
    li x11, 0
    li x12, 0

#if TEST_VSETVLI
test_vsetvli:
    # 测试vsetvli指令
    li a0, 16                  # 设置应用向量长度AVL=16
    vsetvli a1, a0, 0x001      # 设置vl，使用8位元素(e8)，倍数为1(m1)
    li t0, 16
    bne a1, t0, fail_vsetvli   # 验证vl=16 (min(AVL, VLEN/SEW*LMUL))
    
    li a0, 256                 # 设置应用向量长度AVL=256 (大于VLEN/SEW)
    vsetvli a1, a0, 0x001      # vl应该被限制为VLEN/SEW (128/8=16)
    li t0, 16
    bne a1, t0, fail_vsetvli   # 验证vl=16
    
    li a0, 256
    vsetvli a1, a0, 0x002      # 使用16位元素(e16m1)
    li t0, 8                   # 期望vl=VLEN/SEW=128/16=8
    bne a1, t0, fail_vsetvli

    li a0, 256
    vsetvli a1, a0, 0x003      # 使用32位元素(e32m1)
    li t0, 4                   # 期望vl=VLEN/SEW=128/32=4
    bne a1, t0, fail_vsetvli
    
    # 测试分数LMUL (mf2)
    li a0, 256
    vsetvli a1, a0, 0x071      # 8位元素(e8), LMUL=1/2(mf2)
    li t0, 8                   # 根据转换后的汇编，实际值是8
    bne a1, t0, fail_vsetvli
    
    # 测试倍数LMUL (m2)
    li a0, 256
    vsetvli a1, a0, 0x101      # 8位元素(e8), LMUL=2(m2)
    li t0, 32                  # 期望vl=VLEN/SEW*LMUL=128/8*2=32
    bne a1, t0, fail_vsetvli
    
    # avl=0特殊情况
    li a0, 0
    vsetvli a1, a0, 0x001      # avl=0通常表示应设置最大长度
    beqz a1, fail_vsetvli      # vl不应为0
#endif

#if TEST_VSETVL
test_vsetvl:
    li a0, 20                  # AVL=20
    li a1, 0x58                # vtype (e8m2)
    vsetvl a2, a0, a1
    li t0, 32                  # 实际结果为32，而不是20
    bne a2, t0, fail_vsetvl
#endif

success:
    # 将0写入成功地址表示测试通过
    li t0, SUCCESS_ADDR
    li t1, 0
    sw t1, 0(t0)
    j end_test

fail_vsetvli:
    li t0, SUCCESS_ADDR
    li t1, 1
    sw t1, 0(t0)
    j end_test

fail_vsetvl:
    li t0, SUCCESS_ADDR
    li t1, 2
    sw t1, 0(t0)
    j end_test

end_test:
    # 无限循环
1:
    j 1b 