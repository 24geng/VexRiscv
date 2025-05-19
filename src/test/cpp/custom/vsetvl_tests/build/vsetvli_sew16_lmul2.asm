
build/vsetvli_sew16_lmul2.elf:     file format elf32-littleriscv


Disassembly of section .text:

80000000 <_start>:
80000000:	00300e13          	li	t3,3
80000004:	00a00513          	li	a0,10
80000008:	0c9572d7          	vsetvli	t0,a0,e16,m2,ta,ma,d1
8000000c:	00a00313          	li	t1,10
80000010:	00629e63          	bne	t0,t1,8000002c <fail>
80000014:	c20023f3          	csrr	t2,vl
80000018:	00639a63          	bne	t2,t1,8000002c <fail>
8000001c:	0c900313          	li	t1,201
80000020:	c21023f3          	csrr	t2,vtype
80000024:	00639463          	bne	t2,t1,8000002c <fail>
80000028:	0140006f          	j	8000003c <pass>

8000002c <fail>:
8000002c:	f0100137          	lui	sp,0xf0100
80000030:	f2410113          	addi	sp,sp,-220 # f00fff24 <_end+0x700ffed4>
80000034:	01c12023          	sw	t3,0(sp)
80000038:	0140006f          	j	8000004c <end_test>

8000003c <pass>:
8000003c:	f0100137          	lui	sp,0xf0100
80000040:	f2010113          	addi	sp,sp,-224 # f00fff20 <_end+0x700ffed0>
80000044:	00012023          	sw	zero,0(sp)
80000048:	0040006f          	j	8000004c <end_test>

8000004c <end_test>:
8000004c:	0000006f          	j	8000004c <end_test>
