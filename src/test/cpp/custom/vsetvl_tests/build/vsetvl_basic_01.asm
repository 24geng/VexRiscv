
build/vsetvl_basic_01.elf:     file format elf32-littleriscv


Disassembly of section .text:

80000000 <_start>:
80000000:	00200e13          	li	t3,2
80000004:	00300513          	li	a0,3
80000008:	04900593          	li	a1,73
8000000c:	80b572d7          	vsetvl	t0,a0,a1
80000010:	00300313          	li	t1,3
80000014:	00629e63          	bne	t0,t1,80000030 <fail>
80000018:	c20023f3          	csrr	t2,vl
8000001c:	00639a63          	bne	t2,t1,80000030 <fail>
80000020:	04900313          	li	t1,73
80000024:	c21023f3          	csrr	t2,vtype
80000028:	00639463          	bne	t2,t1,80000030 <fail>
8000002c:	0140006f          	j	80000040 <pass>

80000030 <fail>:
80000030:	f0100137          	lui	sp,0xf0100
80000034:	f2410113          	addi	sp,sp,-220 # f00fff24 <_end+0x700ffed0>
80000038:	01c12023          	sw	t3,0(sp)
8000003c:	0140006f          	j	80000050 <end_test>

80000040 <pass>:
80000040:	f0100137          	lui	sp,0xf0100
80000044:	f2010113          	addi	sp,sp,-224 # f00fff20 <_end+0x700ffecc>
80000048:	00012023          	sw	zero,0(sp)
8000004c:	0040006f          	j	80000050 <end_test>

80000050 <end_test>:
80000050:	0000006f          	j	80000050 <end_test>
