.section .text
.globl _start
_start:
    # Test case: vsetvl with illegal configuration
    # This tests several illegal configurations:
    # 1. Reserved VSEW encoding (100 = 0b100)
    # 2. Reserved VLMUL encoding (100 = 0b100)
    # When illegal configuration is detected, vl should be set to 0
    # and vtype.vill should be set to 1
    
    li x28, 0x8110      # Test ID for vsetvl_illegal_config

    #----------------------------------------------
    # Test 1: Illegal VSEW encoding (100)
    #----------------------------------------------
    li x10, 10                  # AVL = 10
    li x11, 0x60                # vtype value: 
                                # vma(bit7)=0, vta(bit6)=1, vsew(5:3)=100 (reserved), vlmul(2:0)=000 (m1)
                                # vtype = 0b01100000 = 0x60

    # Execute vsetvl instruction
    .word 0x80B572D7            # vsetvl x5, x10, x11

    # --- Checks ---
    # Expected vl = 0 (illegal config)
    # Expected vtype.vill = 1

    # Check rd (x5) for vl
    li x6, 0                    # Expected vl = 0
    bne x5, x6, test1_fail      # Check x5 (rd)

    # Check vl CSR (0xC20)
    csrr x7, 0xC20              # Read vl CSR
    bne x7, x6, test1_fail      # Check vl CSR

    # Check vtype CSR (0xC21)
    csrr x7, 0xC21              # Read vtype CSR
    srli x8, x7, 31             # Extract vill bit (bit 31)
    li x6, 1                    # Expected vill = 1
    bne x8, x6, test1_fail      # Check vtype.vill

    #----------------------------------------------
    # Test 2: Illegal VLMUL encoding (100)
    #----------------------------------------------
    li x10, 10                  # AVL = 10
    li x11, 0x44                # vtype value: 
                                # vma(bit7)=0, vta(bit6)=1, vsew(5:3)=000 (e8), vlmul(2:0)=100 (reserved)
                                # vtype = 0b01000100 = 0x44

    # Execute vsetvl instruction
    .word 0x80B572D7            # vsetvl x5, x10, x11

    # --- Checks ---
    # Expected vl = 0 (illegal config)
    # Expected vtype.vill = 1

    # Check rd (x5) for vl
    li x6, 0                    # Expected vl = 0
    bne x5, x6, test2_fail      # Check x5 (rd)

    # Check vl CSR (0xC20)
    csrr x7, 0xC20              # Read vl CSR
    bne x7, x6, test2_fail      # Check vl CSR

    # Check vtype CSR (0xC21)
    csrr x7, 0xC21              # Read vtype CSR
    srli x8, x7, 31             # Extract vill bit (bit 31)
    li x6, 1                    # Expected vill = 1
    bne x8, x6, test2_fail      # Check vtype.vill

    j pass

test1_fail:
    li x28, 0x8111              # Test1 ID (illegal VSEW)
    j fail

test2_fail:
    li x28, 0x8112              # Test2 ID (illegal VLMUL)
    j fail

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