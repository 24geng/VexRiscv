
build/rvv_test.elf:     file format elf32-littleriscv


Disassembly of section .text:

80000000 <_start>:
80000000:	00000e13          	li	t3,0
80000004:	00100f13          	li	t5,1
80000008:	00a00313          	li	t1,10
8000000c:	00000393          	li	t2,0
80000010:	807372d7          	vsetvl	t0,t1,t2
80000014:	c2002573          	csrr	a0,vl
80000018:	c21025f3          	csrr	a1,vtype
8000001c:	00a00e13          	li	t3,10
80000020:	2bc29e63          	bne	t0,t3,800002dc <fail_test>
80000024:	2bc51c63          	bne	a0,t3,800002dc <fail_test>
80000028:	00000e13          	li	t3,0
8000002c:	2bc59863          	bne	a1,t3,800002dc <fail_test>
80000030:	00200f13          	li	t5,2
80000034:	00a00313          	li	t1,10
80000038:	00a00393          	li	t2,10
8000003c:	807372d7          	vsetvl	t0,t1,t2
80000040:	c2002573          	csrr	a0,vl
80000044:	c21025f3          	csrr	a1,vtype
80000048:	00800e13          	li	t3,8
8000004c:	29c29863          	bne	t0,t3,800002dc <fail_test>
80000050:	29c51663          	bne	a0,t3,800002dc <fail_test>
80000054:	00a00e13          	li	t3,10
80000058:	29c59263          	bne	a1,t3,800002dc <fail_test>
8000005c:	00300f13          	li	t5,3
80000060:	00300313          	li	t1,3
80000064:	03900393          	li	t2,57
80000068:	807372d7          	vsetvl	t0,t1,t2
8000006c:	c2002573          	csrr	a0,vl
80000070:	c21025f3          	csrr	a1,vtype
80000074:	00300e13          	li	t3,3
80000078:	27c29263          	bne	t0,t3,800002dc <fail_test>
8000007c:	27c51063          	bne	a0,t3,800002dc <fail_test>
80000080:	03900e13          	li	t3,57
80000084:	25c59c63          	bne	a1,t3,800002dc <fail_test>
80000088:	00400f13          	li	t5,4
8000008c:	00500313          	li	t1,5
80000090:	03000393          	li	t2,48
80000094:	807372d7          	vsetvl	t0,t1,t2
80000098:	c2002573          	csrr	a0,vl
8000009c:	c21025f3          	csrr	a1,vtype
800000a0:	00400e13          	li	t3,4
800000a4:	23c29c63          	bne	t0,t3,800002dc <fail_test>
800000a8:	23c51a63          	bne	a0,t3,800002dc <fail_test>
800000ac:	03000e13          	li	t3,48
800000b0:	23c59663          	bne	a1,t3,800002dc <fail_test>
800000b4:	00500f13          	li	t5,5
800000b8:	00a00313          	li	t1,10
800000bc:	800003b7          	lui	t2,0x80000
800000c0:	807372d7          	vsetvl	t0,t1,t2
800000c4:	c2002573          	csrr	a0,vl
800000c8:	c21025f3          	csrr	a1,vtype
800000cc:	00000e13          	li	t3,0
800000d0:	21c29663          	bne	t0,t3,800002dc <fail_test>
800000d4:	21c51463          	bne	a0,t3,800002dc <fail_test>
800000d8:	80000e37          	lui	t3,0x80000
800000dc:	21c59063          	bne	a1,t3,800002dc <fail_test>
800000e0:	00600f13          	li	t5,6
800000e4:	00a00313          	li	t1,10
800000e8:	10000393          	li	t2,256
800000ec:	807372d7          	vsetvl	t0,t1,t2
800000f0:	c2002573          	csrr	a0,vl
800000f4:	c21025f3          	csrr	a1,vtype
800000f8:	00000e13          	li	t3,0
800000fc:	1fc29063          	bne	t0,t3,800002dc <fail_test>
80000100:	1dc51e63          	bne	a0,t3,800002dc <fail_test>
80000104:	01f5d613          	srli	a2,a1,0x1f
80000108:	00100e13          	li	t3,1
8000010c:	1dc61863          	bne	a2,t3,800002dc <fail_test>
80000110:	00700f13          	li	t5,7
80000114:	00a00313          	li	t1,10
80000118:	02000393          	li	t2,32
8000011c:	807372d7          	vsetvl	t0,t1,t2
80000120:	c2002573          	csrr	a0,vl
80000124:	c21025f3          	csrr	a1,vtype
80000128:	00000e13          	li	t3,0
8000012c:	1bc29863          	bne	t0,t3,800002dc <fail_test>
80000130:	1bc51663          	bne	a0,t3,800002dc <fail_test>
80000134:	80000e37          	lui	t3,0x80000
80000138:	020e0e13          	addi	t3,t3,32 # 80000020 <TOHOST_ADDR+0x8ff00110>
8000013c:	01f5d613          	srli	a2,a1,0x1f
80000140:	00100e13          	li	t3,1
80000144:	19c61c63          	bne	a2,t3,800002dc <fail_test>
80000148:	00800f13          	li	t5,8
8000014c:	00a00313          	li	t1,10
80000150:	00300393          	li	t2,3
80000154:	807372d7          	vsetvl	t0,t1,t2
80000158:	c2002573          	csrr	a0,vl
8000015c:	c21025f3          	csrr	a1,vtype
80000160:	00200e13          	li	t3,2
80000164:	17c29c63          	bne	t0,t3,800002dc <fail_test>
80000168:	17c51a63          	bne	a0,t3,800002dc <fail_test>
8000016c:	00300e13          	li	t3,3
80000170:	17c59663          	bne	a1,t3,800002dc <fail_test>
80000174:	00900f13          	li	t5,9
80000178:	00a00313          	li	t1,10
8000017c:	02800393          	li	t2,40
80000180:	807372d7          	vsetvl	t0,t1,t2
80000184:	c2002573          	csrr	a0,vl
80000188:	c21025f3          	csrr	a1,vtype
8000018c:	00200e13          	li	t3,2
80000190:	15c29663          	bne	t0,t3,800002dc <fail_test>
80000194:	15c51463          	bne	a0,t3,800002dc <fail_test>
80000198:	02800e13          	li	t3,40
8000019c:	15c59063          	bne	a1,t3,800002dc <fail_test>
800001a0:	00a00f13          	li	t5,10
800001a4:	00500313          	li	t1,5
800001a8:	0c200393          	li	t2,194
800001ac:	807372d7          	vsetvl	t0,t1,t2
800001b0:	c2002573          	csrr	a0,vl
800001b4:	c21025f3          	csrr	a1,vtype
800001b8:	00400e13          	li	t3,4
800001bc:	13c29063          	bne	t0,t3,800002dc <fail_test>
800001c0:	11c51e63          	bne	a0,t3,800002dc <fail_test>
800001c4:	0c200e13          	li	t3,194
800001c8:	11c59a63          	bne	a1,t3,800002dc <fail_test>
800001cc:	00b00f13          	li	t5,11
800001d0:	04600313          	li	t1,70
800001d4:	01000393          	li	t2,16
800001d8:	807372d7          	vsetvl	t0,t1,t2
800001dc:	c2002573          	csrr	a0,vl
800001e0:	c21025f3          	csrr	a1,vtype
800001e4:	04000e13          	li	t3,64
800001e8:	0fc29a63          	bne	t0,t3,800002dc <fail_test>
800001ec:	0fc51863          	bne	a0,t3,800002dc <fail_test>
800001f0:	01000e13          	li	t3,16
800001f4:	0fc59463          	bne	a1,t3,800002dc <fail_test>
800001f8:	00c00f13          	li	t5,12
800001fc:	08200313          	li	t1,130
80000200:	01800393          	li	t2,24
80000204:	807372d7          	vsetvl	t0,t1,t2
80000208:	c2002573          	csrr	a0,vl
8000020c:	c21025f3          	csrr	a1,vtype
80000210:	08000e13          	li	t3,128
80000214:	0dc29463          	bne	t0,t3,800002dc <fail_test>
80000218:	0dc51263          	bne	a0,t3,800002dc <fail_test>
8000021c:	01800e13          	li	t3,24
80000220:	0bc59e63          	bne	a1,t3,800002dc <fail_test>
80000224:	00d00f13          	li	t5,13
80000228:	00500313          	li	t1,5
8000022c:	00100393          	li	t2,1
80000230:	807372d7          	vsetvl	t0,t1,t2
80000234:	c2002573          	csrr	a0,vl
80000238:	c21025f3          	csrr	a1,vtype
8000023c:	00500e13          	li	t3,5
80000240:	09c29e63          	bne	t0,t3,800002dc <fail_test>
80000244:	09c51c63          	bne	a0,t3,800002dc <fail_test>
80000248:	00100e13          	li	t3,1
8000024c:	09c59863          	bne	a1,t3,800002dc <fail_test>
80000250:	00e00f13          	li	t5,14
80000254:	01400313          	li	t1,20
80000258:	00900393          	li	t2,9
8000025c:	807372d7          	vsetvl	t0,t1,t2
80000260:	c2002573          	csrr	a0,vl
80000264:	c21025f3          	csrr	a1,vtype
80000268:	01000e13          	li	t3,16
8000026c:	07c29863          	bne	t0,t3,800002dc <fail_test>
80000270:	07c51663          	bne	a0,t3,800002dc <fail_test>
80000274:	00900e13          	li	t3,9
80000278:	07c59263          	bne	a1,t3,800002dc <fail_test>
8000027c:	00f00f13          	li	t5,15
80000280:	00300313          	li	t1,3
80000284:	00200393          	li	t2,2
80000288:	807372d7          	vsetvl	t0,t1,t2
8000028c:	c2002573          	csrr	a0,vl
80000290:	c21025f3          	csrr	a1,vtype
80000294:	00300e13          	li	t3,3
80000298:	05c29263          	bne	t0,t3,800002dc <fail_test>
8000029c:	05c51063          	bne	a0,t3,800002dc <fail_test>
800002a0:	00200e13          	li	t3,2
800002a4:	03c59c63          	bne	a1,t3,800002dc <fail_test>
800002a8:	01000f13          	li	t5,16
800002ac:	00100313          	li	t1,1
800002b0:	00300393          	li	t2,3
800002b4:	807372d7          	vsetvl	t0,t1,t2
800002b8:	c2002573          	csrr	a0,vl
800002bc:	c21025f3          	csrr	a1,vtype
800002c0:	00100e13          	li	t3,1
800002c4:	01c29c63          	bne	t0,t3,800002dc <fail_test>
800002c8:	01c51a63          	bne	a0,t3,800002dc <fail_test>
800002cc:	00300e13          	li	t3,3
800002d0:	01c59663          	bne	a1,t3,800002dc <fail_test>
800002d4:	00100513          	li	a0,1
800002d8:	0080006f          	j	800002e0 <end_test>

800002dc <fail_test>:
800002dc:	001f1513          	slli	a0,t5,0x1

800002e0 <end_test>:
800002e0:	f00100b7          	lui	ra,0xf0010
800002e4:	00a0a023          	sw	a0,0(ra) # f0010000 <TOHOST_ADDR+0xfff100f0>
800002e8:	0000006f          	j	800002e8 <end_test+0x8>
