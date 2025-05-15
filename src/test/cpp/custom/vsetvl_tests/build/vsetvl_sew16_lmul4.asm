
build/vsetvl_sew16_lmul4.elf:     file format elf32-littleriscv


Disassembly of section .text:

80000000 <_start>:
80000000:	00008e37          	lui	t3,0x8
80000004:	106e0e13          	addi	t3,t3,262 # 8106 <_start-0x7fff7efa>
80000008:	00a00513          	li	a0,10
8000000c:	04a00593          	li	a1,74
80000010:	80b572d7          	vsetvl	t0,a0,a1
80000014:	00a00313          	li	t1,10
80000018:	00629e63          	bne	t0,t1,80000034 <fail>
8000001c:	c20023f3          	csrr	t2,vl
80000020:	00639a63          	bne	t2,t1,80000034 <fail>
80000024:	04a00313          	li	t1,74
80000028:	c21023f3          	csrr	t2,vtype
8000002c:	00639463          	bne	t2,t1,80000034 <fail>
80000030:	0140006f          	j	80000044 <pass>

80000034 <fail>:
80000034:	f0100137          	lui	sp,0xf0100
80000038:	f2410113          	addi	sp,sp,-220 # f00fff24 <_end+0x700ffecc>
8000003c:	01c12023          	sw	t3,0(sp)
80000040:	0140006f          	j	80000054 <end_test>

80000044 <pass>:
80000044:	f0100137          	lui	sp,0xf0100
80000048:	f2010113          	addi	sp,sp,-224 # f00fff20 <_end+0x700ffec8>
8000004c:	00012023          	sw	zero,0(sp)
80000050:	0040006f          	j	80000054 <end_test>

80000054 <end_test>:
80000054:	0000006f          	j	80000054 <end_test>
