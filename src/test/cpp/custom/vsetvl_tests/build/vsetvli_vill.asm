
build/vsetvli_vill.elf:     file format elf32-littleriscv


Disassembly of section .text:

80000000 <_start>:
80000000:	00800e13          	li	t3,8
80000004:	00a00513          	li	a0,10
80000008:	0e4572d7          	vsetvli	t0,a0,e128,(null),ta,ma,d1
8000000c:	80000337          	lui	t1,0x80000
80000010:	c21023f3          	csrr	t2,vtype
80000014:	02639863          	bne	t2,t1,80000044 <fail>
80000018:	00000313          	li	t1,0
8000001c:	c20023f3          	csrr	t2,vl
80000020:	02639263          	bne	t2,t1,80000044 <fail>
80000024:	0d057357          	vsetvli	t1,a0,e32,m1,ta,ma,d1
80000028:	00800313          	li	t1,8
8000002c:	c20023f3          	csrr	t2,vl
80000030:	00639a63          	bne	t2,t1,80000044 <fail>
80000034:	0d000313          	li	t1,208
80000038:	c21023f3          	csrr	t2,vtype
8000003c:	00639463          	bne	t2,t1,80000044 <fail>
80000040:	0140006f          	j	80000054 <pass>

80000044 <fail>:
80000044:	f0100137          	lui	sp,0xf0100
80000048:	f2410113          	addi	sp,sp,-220 # f00fff24 <_end+0x700ffebc>
8000004c:	01c12023          	sw	t3,0(sp)
80000050:	0140006f          	j	80000064 <end_test>

80000054 <pass>:
80000054:	f0100137          	lui	sp,0xf0100
80000058:	f2010113          	addi	sp,sp,-224 # f00fff20 <_end+0x700ffeb8>
8000005c:	00012023          	sw	zero,0(sp)
80000060:	0040006f          	j	80000064 <end_test>

80000064 <end_test>:
80000064:	0000006f          	j	80000064 <end_test>
