
build/rvv_test.elf:     file format elf32-littleriscv


Disassembly of section .text:

80000000 <_start>:
80000000:	00a00313          	li	t1,10
80000004:	00200393          	li	t2,2
80000008:	807372d7          	vsetvl	t0,t1,t2
8000000c:	c20022f3          	csrr	t0,vl
80000010:	c2102373          	csrr	t1,vtype
80000014:	00028393          	mv	t2,t0
80000018:	00400e13          	li	t3,4
8000001c:	00200e93          	li	t4,2
80000020:	01c29a63          	bne	t0,t3,80000034 <fail_vl>
80000024:	01d31c63          	bne	t1,t4,8000003c <fail_vtype>
80000028:	01c39e63          	bne	t2,t3,80000044 <fail_rd>

8000002c <pass>:
8000002c:	00100293          	li	t0,1
80000030:	0180006f          	j	80000048 <report_result>

80000034 <fail_vl>:
80000034:	00200293          	li	t0,2
80000038:	0100006f          	j	80000048 <report_result>

8000003c <fail_vtype>:
8000003c:	00300293          	li	t0,3
80000040:	0080006f          	j	80000048 <report_result>

80000044 <fail_rd>:
80000044:	00400293          	li	t0,4

80000048 <report_result>:
80000048:	f0010f37          	lui	t5,0xf0010
8000004c:	005f2023          	sw	t0,0(t5) # f0010000 <TOHOST_ADDR+0xfff100f0>
80000050:	00100073          	ebreak
