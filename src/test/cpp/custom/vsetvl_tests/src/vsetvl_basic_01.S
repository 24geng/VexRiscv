\
.section .text
.globl _start
_start:
    # Test case: Basic vsetvl
    # vsetvl t0, a0, a1
    # a0 (x10) = requested AVL
    # a1 (x11) = new vtype setting
    # t0 (x5)  = resulting vl

    li x28, 2           # Test ID for vsetvl_basic_01

    # Setup inputs
    li x10, 3           # AVL = 3
    li x11, 0x49        # New vtype: e16, m2, vta=1, vma=0
                        # vma(bit7)=0, vta(bit6)=1, vsew(5:3)=001 (e16), vlmul(2:0)=001 (m2)
                        # vtype value = 0b01001001 = 0x49

    # Execute vsetvl instruction
    # Encoding for vsetvl x5, x10, x11:
    # funct7[6] (bit 31) = 1 (for vsetvl)
    # funct7[5] (bit 30) = 0 (for vsetvl)
    # rs2      (bits 24-20) = 11 (x11)
    # rs1      (bits 19-15) = 10 (x10)
    # funct3   (bits 14-12) = 0b111 (OP-V variant)
    # rd       (bits 11-7)  = 5  (x5)
    # opcode   (bits 6-0)   = 0b1010111 (OP-V)
    #
    # Word: (1 << 31) | (0 << 30) | (11 << 20) | (10 << 15) | (0x7 << 12) | (5 << 7) | 0x57
    #       0x80000000 | 0x00000000 | 0x00B00000 | 0x00050000 | 0x00007000 | 0x00000280 | 0x00000057
    #     = 0x80B572D7
    .word 0x80B572D7    # vsetvl x5, x10, x11

    # --- Checks ---
    # Expected vl = 3 (since AVL=3 < VLMAX for e16,m2 with VLEN=256)
    # VLMAX = (256/16)*2 = 16*2 = 32.
    # Expected vtype = 0x49

    # Check rd (x5) for vl
    li x6, 3            # Expected vl = 3
    bne x5, x6, fail    # Check x5 (rd)

    # Check vl CSR (0xC20)
    csrr x7, 0xC20      # Read vl CSR into x7
    bne x7, x6, fail    # Check vl CSR against expected vl

    # Check vtype CSR (0xC21)
    li x6, 0x49         # Expected vtype in CSR
    csrr x7, 0xC21      # Read vtype CSR into x7
    bne x7, x6, fail    # Check vtype CSR

    j pass

fail:
    li x2, 0xF00FFF24   # fail address (consistent with other tests)
    sw x28, 0(x2)       # Store Test ID on failure
    j end_test

pass:
    li x2, 0xF00FFF20   # pass address (consistent with other tests)
    sw x0, 0(x2)        # Store 0 on pass
    j end_test

end_test:
    # Loop forever
    j end_test 