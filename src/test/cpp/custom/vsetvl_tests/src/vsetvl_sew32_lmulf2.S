.section .text
.globl _start
_start:
    # Test case: vsetvl with sew32_lmulf2
    # Configuration:
    # - SEW = 32 bits (vsew encoding = 010)
    # - LMUL = 1/2 (vlmul encoding = 111)
    # - vta = 1 (tail agnostic)
    # - vma = 0 (mask agnostic)
    # VLEN = 256, ELEN = 64
    # Expected VLMAX = (VLEN/SEW)*LMUL = (256/32)*0.5 = 4
    
    li x28, 0x810B      # Test ID for vsetvl_sew32_lmulf2

    # Setup inputs
    li x10, 10          # AVL = 10 (larger than VLMAX=4)
    li x11, 0x57        # vtype value: 
                        # vma(bit7)=0, vta(bit6)=1, vsew(5:3)=010 (e32), vlmul(2:0)=111 (mf2)
                        # vtype = 0b01010111 = 0x57

    # Execute vsetvl instruction
    # Encoding for vsetvl x5, x10, x11:
    # funct6=100000 (vsetvl), rs2=x11, rs1=x10, funct3=111, rd=x5, opcode=1010111
    .word 0x80B572D7    # vsetvl x5, x10, x11

    # --- Checks ---
    # Expected vl = 4 (since AVL=10 > VLMAX=4, vl is capped at VLMAX)
    # Expected vtype = 0x57

    # Check rd (x5) for vl
    li x6, 4            # Expected vl = 4
    bne x5, x6, fail    # Check x5 (rd)

    # Check vl CSR (0xC20)
    csrr x7, 0xC20      # Read vl CSR
    bne x7, x6, fail    # Check vl CSR

    # Check vtype CSR (0xC21)
    li x6, 0x57         # Expected vtype in CSR
    csrr x7, 0xC21      # Read vtype CSR
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