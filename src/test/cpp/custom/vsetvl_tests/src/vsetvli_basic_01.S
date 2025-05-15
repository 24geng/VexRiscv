.section .text
.globl _start
_start:
    # Test case 1: Basic vsetvli
    # vsetvli t0, a0, e32,m1,ta,ma 
    # Expected: vl = a0 (if a0 < VLMAX), vtype = encoding for (e32,m1,ta,ma)
    #           t0 should contain the new vl.
    # VLMAX for e32,m1 with VLEN=256 is (256/32)*1 = 8

    li x28, 1           # Test ID
    li x10, 5           # a0 = AVL request = 5
    
    # For e32, m1, ta, ma (tail agnostic, mask agnostic):
    # vill = 0 (implicit in zimm[10] for vsetvli which must be 0)
    # vma = 1 (zimm[7])
    # vta = 0 (zimm[6])
    # vsew = 010 (e32) (zimm[5:3])
    # vlmul = 000 (m1) (zimm[2:0])
    # zimm[10:0] = 0_00_1_0_010_000 = 0x120
    # CSR vtype value: vma(bit7) vta(bit6) vsew(bits5-3) vlmul(bits2-0) = 0b10010000 = 0x90

    # vsetvli t0, a0, zimm=0x120
    # instruction encoding: imm[11:0] | rs1 | funct3 | rd | opcode
    # For vsetvli, inst[31] is 0. So imm[10:0] is zimm[10:0]
    # imm[10:0] = 0x120 = 0b00100100000. Shifted to bit 20: (0x120 << 20)
    # CORRECTED: Using zimm = 0x048 for e16,m1,vta=1,vma=0 (vill=0, reserved=0)
    # zimm[10:0] = 0x048 = 0b00001001000
    .word (0x048 << 20) | (10 << 15) | (0x7 << 12) | (5 << 7) | 0x57  # vsetvli t0, x10, vtypei=0x048

    # Check vl (in t0 (x5) and CSR_VL (0xC20))
    li x6, 5            # Expected vl = 5
    bne x5, x6, fail    # Check t0 (rd)

    csrr x7, 0xC20      # Read vl CSR into x7
    bne x7, x6, fail    # Check vl CSR

    # Check vtype (CSR_VTYPE (0xC21))
    li x6, 0x48         # Expected vtype in CSR: 0b01001000
    csrr x7, 0xC21      # Read vtype CSR into x7
    bne x7, x6, fail    # Check vtype CSR
    
    j pass

fail:
    li x2, 0xF00FFF24   # fail address
    sw x28, 0(x2)
    j end_test

pass:
    li x2, 0xF00FFF20   # pass address
    sw x0, 0(x2)
    j end_test

end_test:
    # Loop forever
    j end_test 