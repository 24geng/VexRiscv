
build/vsetvl_illegal_config.elf:     file format elf32-littleriscv


Disassembly of section .text:

80000000 <_start>:
80000000:	00008e37          	lui	t3,0x8
80000004:	110e0e13          	addi	t3,t3,272 # 8110 <_start-0x7fff7ef0>
80000008:	00a00513          	li	a0,10
8000000c:	06000593          	li	a1,96
80000010:	80b572d7          	vsetvl	t0,a0,a1
80000014:	00000313          	li	t1,0
80000018:	04629663          	bne	t0,t1,80000064 <test1_fail>
8000001c:	c20023f3          	csrr	t2,vl
80000020:	04639263          	bne	t2,t1,80000064 <test1_fail>
80000024:	c21023f3          	csrr	t2,vtype
80000028:	01f3d413          	srli	s0,t2,0x1f
8000002c:	00100313          	li	t1,1
80000030:	02641a63          	bne	s0,t1,80000064 <test1_fail>
80000034:	00a00513          	li	a0,10
80000038:	04400593          	li	a1,68
8000003c:	80b572d7          	vsetvl	t0,a0,a1
80000040:	00000313          	li	t1,0
80000044:	02629663          	bne	t0,t1,80000070 <test2_fail>
80000048:	c20023f3          	csrr	t2,vl
8000004c:	02639263          	bne	t2,t1,80000070 <test2_fail>
80000050:	c21023f3          	csrr	t2,vtype
80000054:	01f3d413          	srli	s0,t2,0x1f
80000058:	00100313          	li	t1,1
8000005c:	00641a63          	bne	s0,t1,80000070 <test2_fail>
80000060:	02c0006f          	j	8000008c <pass>

80000064 <test1_fail>:
80000064:	00008e37          	lui	t3,0x8
80000068:	111e0e13          	addi	t3,t3,273 # 8111 <_start-0x7fff7eef>
8000006c:	0100006f          	j	8000007c <fail>

80000070 <test2_fail>:
80000070:	00008e37          	lui	t3,0x8
80000074:	112e0e13          	addi	t3,t3,274 # 8112 <_start-0x7fff7eee>
80000078:	0040006f          	j	8000007c <fail>

8000007c <fail>:
8000007c:	f0100137          	lui	sp,0xf0100
80000080:	f2410113          	addi	sp,sp,-220 # f00fff24 <_end+0x700ffe84>
80000084:	01c12023          	sw	t3,0(sp)
80000088:	0140006f          	j	8000009c <end_test>

8000008c <pass>:
8000008c:	f0100137          	lui	sp,0xf0100
80000090:	f2010113          	addi	sp,sp,-224 # f00fff20 <_end+0x700ffe80>
80000094:	00012023          	sw	zero,0(sp)
80000098:	0040006f          	j	8000009c <end_test>

8000009c <end_test>:
8000009c:	0000006f          	j	8000009c <end_test>
