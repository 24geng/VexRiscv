
build/vsetvl_vill.elf:     file format elf32-littleriscv


Disassembly of section .text:

80000000 <_start>:
80000000:	00008e37          	lui	t3,0x8
80000004:	113e0e13          	addi	t3,t3,275 # 8113 <_start-0x7fff7eed>
80000008:	00a00513          	li	a0,10
8000000c:	800005b7          	lui	a1,0x80000
80000010:	04858593          	addi	a1,a1,72 # 80000048 <_end+0xffffffe8>
80000014:	80b572d7          	vsetvl	t0,a0,a1
80000018:	00000313          	li	t1,0
8000001c:	02629063          	bne	t0,t1,8000003c <fail>
80000020:	c20023f3          	csrr	t2,vl
80000024:	00639c63          	bne	t2,t1,8000003c <fail>
80000028:	c21023f3          	csrr	t2,vtype
8000002c:	01f3d413          	srli	s0,t2,0x1f
80000030:	00100313          	li	t1,1
80000034:	00641463          	bne	s0,t1,8000003c <fail>
80000038:	0140006f          	j	8000004c <pass>

8000003c <fail>:
8000003c:	f0100137          	lui	sp,0xf0100
80000040:	f2410113          	addi	sp,sp,-220 # f00fff24 <_end+0x700ffec4>
80000044:	01c12023          	sw	t3,0(sp)
80000048:	0140006f          	j	8000005c <end_test>

8000004c <pass>:
8000004c:	f0100137          	lui	sp,0xf0100
80000050:	f2010113          	addi	sp,sp,-224 # f00fff20 <_end+0x700ffec0>
80000054:	00012023          	sw	zero,0(sp)
80000058:	0040006f          	j	8000005c <end_test>

8000005c <end_test>:
8000005c:	0000006f          	j	8000005c <end_test>
