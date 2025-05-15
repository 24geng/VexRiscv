.section .text
.globl _start
_start:
    # Test case: vsetvl with vill bit set in vtype
    # When vill bit is set in vtype, vl should be set to 0
    # Configuration with vill=1 should be preserved
    
    li x28, 0x8113      # Test ID for vsetvl_vill

    # Setup inputs
    li x10, 10                  # AVL = 10
    li x11, 0x80000048          # vtype value with vill bit set: 
                                # vill(bit31)=1, other bits don't matter
                                # but we use standard valid setting otherwise:
                                # vma(bit7)=0, vta(bit6)=1, vsew(5:3)=001 (e16), vlmul(2:0)=000 (m1)
                                # vtype = 0x80000048

    # Execute vsetvl instruction
    .word 0x80B572D7            # vsetvl x5, x10, x11

    # --- Checks ---
    # Expected vl = 0 (vill=1)
    # Expected vtype.vill = 1, and preserved vtype value

    # Check rd (x5) for vl
    li x6, 0                    # Expected vl = 0
    bne x5, x6, fail            # Check x5 (rd)

    # Check vl CSR (0xC20)
    csrr x7, 0xC20              # Read vl CSR
    bne x7, x6, fail            # Check vl CSR

    # Check vtype CSR (0xC21)
    csrr x7, 0xC21              # Read vtype CSR
    srli x8, x7, 31             # Extract vill bit (bit 31)
    li x6, 1                    # Expected vill = 1
    bne x8, x6, fail            # Check vtype.vill

    j pass

fail:
    li x2, 0xF00FFF24           # fail address (consistent with other tests)
    sw x28, 0(x2)               # Store Test ID on failure
    j end_test

pass:
    li x2, 0xF00FFF20           # pass address (consistent with other tests)
    sw x0, 0(x2)                # Store 0 on pass
    j end_test

end_test:
    # Loop forever
    j end_test 