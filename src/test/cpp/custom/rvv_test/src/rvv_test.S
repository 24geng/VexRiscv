# Test variables
# x5 (rd) - Holds the result of vsetvl/i (which should be the calculated vl)
# x6 (rs1) - Holds AVL (Application Vector Length) or is x0 for vsetvli VLMAX request
# x7 (rs2) - Holds VTYPE for vsetvl instruction
# x10 - Holds value read from vl CSR
# x11 - Holds value read from vtype CSR
# x12 - Temporary register for comparison
# x28 - Holds expected value for comparison
# x30 - Holds the current test case number for failure reporting

.section .text
.global _start

_start: # Standard entry point
rvv_test:
    # --- Test Setup ---
    li x28, 0 # Clear expected value register initially

    # === VSETVL Tests ===

    # Test Case 1: Legal, SEW=8, LMUL=m1 (VLMAX=16), AVL=10 < VLMAX -> vl=10
    li x30, 1          # Set current test case number
    li x6, 10          # rs1 = AVL = 10
    li x7, 0x00        # rs2 = VTYPE (vsew=8, vlmul=m1)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 10         # Expected vl = 10
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x00       # Expected vtype (vill=0)
    bne x11, x28, fail_test

    # Test Case 2: Legal, SEW=32, LMUL=m2 (VLMAX=8), AVL=10 < VLMAX -> vl=8
    li x30, 2          # Set current test case number
    li x6, 10          # rs1 = AVL = 10
    li x7, 0x0A        # rs2 = VTYPE (vsew=32, vlmul=m2: 0b001010)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 8          # Expected vl = min(10, VLMAX=(128/32)*2=8) = 8
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x0A       # Expected vtype (vill=0)
    bne x11, x28, fail_test

    # Test Case 3: Legal, SEW=16, LMUL=mf2 (VLMAX=4), AVL=3 < VLMAX -> vl=3
    # VTYPE: vsew=16 (001), lmul=mf2 (111), vta=0, vma=0 => 0b...00111001 = 0x39
    li x30, 3          # Set current test case number
    li x6, 3           # rs1 = AVL = 3
    li x7, 0x39        # Corrected VTYPE for SEW=16, LMUL=mf2
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 3          # Expected vl = 3
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x39       # Expected vtype (vill=0)
    bne x11, x28, fail_test

    # Test Case 4: Legal, SEW=8, LMUL=mf4 (VLMAX=4), AVL=5 > VLMAX -> vl=4
    # VTYPE: vsew=8 (000), lmul=mf4 (110), vta=0, vma=0 => 0b...110000 = 0x30
    li x30, 4          # Set current test case number
    li x6, 5           # rs1 = AVL = 5
    li x7, 0x30
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 4          # Expected vl = VLMAX = (128/8)*(1/4)=16/4=4
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x30       # Expected vtype (vill=0)
    bne x11, x28, fail_test

    # Test Case 5: Illegal, vill set in rs2
    li x30, 5          # Set current test case number
    li x6, 10
    li x7, 0x80000000  # Set vill bit (assuming XLEN=32)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 0          # Expected vl = 0
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x80000000 # Expected vtype (vill=1)
    bne x11, x28, fail_test

    # Test Case 6: Illegal, reserved bit set in rs2[8]
    li x30, 6          # Set current test case number
    li x6, 10
    li x7, 0x00000100  # Set reserved bit 8
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 0          # Expected vl = 0
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    srli x12, x11, 31   # Extract vill bit
    li x28, 1           # Expected vill = 1
    bne x12, x28, fail_test


    # Test Case 7: Illegal, LMUL reserved (100)
    # VTYPE: vsew=8 (000), lmul=res(100) => 0b...100000 = 0x20
    li x30, 7          # Set current test case number
    li x6, 10
    li x7, 0x20
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 0          # Expected vl = 0
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x80000020 # Expected vtype (vill=1, original lmul/sew)
    srli x12, x11, 31 # check vill bit only
    li x28, 1
    bne x12, x28, fail_test

    # Test Case 8: Legal, SEW=64 requested (vsew=011) on ELEN=64 hardware
    # VTYPE: vsew=64 (011), lmul=m1(000) => 0b...000011 = 0x03
    li x30, 8          # Set current test case number
    li x6, 10          # rs1 = AVL = 10
    li x7, 0x03        # VTYPE: SEW=64, LMUL=m1
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 2          # Expected vl = min(10, VLMAX=(128/64)*1=2) = 2
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x03       # Expected vtype (vill=0)
    bne x11, x28, fail_test

    # Test Case 9: Legal, Fractional LMUL: SEW=8, LMUL=mf8. Constraint SEW*LMUL_DEN < 8 (8*8 < 8) is FALSE.
    # VTYPE = 0x28. VLMAX = (128/8)/8 = 2. AVL=10. vl = min(10,2) = 2.
    li x30, 9          # Set current test case number
    li x6, 10          # AVL = 10
    li x7, 0x28        # VTYPE: vsew=8, vlmul=mf8
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 2          # Expected vl = 2
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x28       # Expected vtype (vill=0, from 0x28)
    bne x11, x28, fail_test

    # Test Case 10: vta=1, vma=1 (should be legal, check if bits are preserved)
    # VTYPE: vsew=32(010), lmul=m1(000), vta=1, vma=1 => 0b...110010 = 0xC2
    li x30, 10         # Set current test case number
    li x6, 5           # AVL = 5
    li x7, 0xC2
    vsetvl x5, x6, x7  # VLMAX = (128/32)*1 = 4
    csrr x10, vl
    csrr x11, vtype
    li x28, 4          # Expected vl = min(5, VLMAX=4) = 4
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0xC2       # Expected vtype (vill=0, vta=1, vma=1)
    bne x11, x28, fail_test

    # Test Case 11: Legal, SEW=8, LMUL=m4 (VLMAX=64), AVL=70 > VLMAX -> vl=64
    # VTYPE: vsew=8 (000), lmul=m4 (010) => 0b...00010000 = 0x10
    li x30, 11         # Set current test case number
    li x6, 70          # rs1 = AVL = 70
    li x7, 0x10        # rs2 = VTYPE (SEW=8, LMUL=m4)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 64         # Expected vl = min(70, VLMAX=(128/8)*4=64) = 64
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x10       # Expected vtype (vill=0)
    bne x11, x28, fail_test

    # Test Case 12: Legal, SEW=8, LMUL=m8 (VLMAX=128), AVL=130 > VLMAX -> vl=128 (Max VL)
    # VTYPE: vsew=8 (000), lmul=m8 (011) => 0b...00011000 = 0x18
    li x30, 12         # Set current test case number
    li x6, 130         # rs1 = AVL = 130
    li x7, 0x18        # rs2 = VTYPE (SEW=8, LMUL=m8)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 128        # Expected vl = min(130, VLMAX=(128/8)*8=128) = 128
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x18       # Expected vtype (vill=0)
    bne x11, x28, fail_test

    # Test Case 13: Legal, SEW=16, LMUL=m1 (VLMAX=8), AVL=5 < VLMAX -> vl=5
    # VTYPE: vsew=16 (001), lmul=m1 (000) => 0x01
    li x30, 13         # Set current test case number
    li x6, 5           # rs1 = AVL = 5
    li x7, 0x01        # rs2 = VTYPE (SEW=16, LMUL=m1)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 5          # Expected vl = min(5, VLMAX=(128/16)*1=8) = 5
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x01       # Expected vtype (vill=0)
    bne x11, x28, fail_test

    # Test Case 14: Legal, SEW=16, LMUL=m2 (VLMAX=16), AVL=20 > VLMAX -> vl=16
    # VTYPE: vsew=16 (001), lmul=m2 (001) => 0x09
    li x30, 14         # Set current test case number
    li x6, 20          # rs1 = AVL = 20
    li x7, 0x09        # rs2 = VTYPE (SEW=16, LMUL=m2)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 16         # Expected vl = min(20, VLMAX=(128/16)*2=16) = 16
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x09       # Expected vtype (vill=0)
    bne x11, x28, fail_test

    # Test Case 15: Legal, SEW=32, LMUL=m1 (VLMAX=4), AVL=3 < VLMAX -> vl=3
    # VTYPE: vsew=32 (010), lmul=m1 (000) => 0x02
    li x30, 15         # Set current test case number
    li x6, 3           # rs1 = AVL = 3
    li x7, 0x02        # rs2 = VTYPE (SEW=32, LMUL=m1)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 3          # Expected vl = min(3, VLMAX=(128/32)*1=4) = 3
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x02       # Expected vtype (vill=0)
    bne x11, x28, fail_test

    # Test Case 16: Legal, SEW=64, LMUL=m1 (VLMAX=2), AVL=1 < VLMAX -> vl=1
    # VTYPE: vsew=64 (011), lmul=m1 (000) => 0x03
    li x30, 16         # Set current test case number
    li x6, 1           # rs1 = AVL = 1
    li x7, 0x03        # rs2 = VTYPE (SEW=64, LMUL=m1)
    vsetvl x5, x6, x7
    csrr x10, vl
    csrr x11, vtype
    li x28, 1          # Expected vl = min(1, VLMAX=(128/64)*1=2) = 1
    bne x5, x28, fail_test
    bne x10, x28, fail_test
    li x28, 0x03       # Expected vtype (vill=0)
    bne x11, x28, fail_test

    # === VSETVLI Tests ===

    # Test Case 11: Legal, SEW=16(001), LMUL=m4(010), AVL=20 -> vl=min(20, VLMAX)
    # imm[10:0] = VTYPE: vill=0, vma=0, vta=0, vsew=16(001), lmul=m4(010) => 0b000010001 = 0x11
    # VLMAX = (128/16)*8 = 8*8 = 64
#    li x30, 11         # Set current test case number
#    li x6, 20          # rs1 = AVL = 20
#    vsetvli x5, x6, 0x11 # vsew=16, lmul=m4
#    csrr x10, vl
#    csrr x11, vtype
#    li x28, 20         # Expected vl = min(20, 64) = 20
#    bne x5, x28, fail_test
#    bne x10, x28, fail_test
#    li x28, 0x11       # Expected vtype (vill=0)
#    bne x11, x28, fail_test

    # Test Case 12: Legal, SEW=32(010), LMUL=m1(000), Request VLMAX (rs1=x0)
    # imm[10:0] = VTYPE: vill=0, vma=0, vta=0, vsew=32(010), lmul=m1(000) => 0b000000010 = 0x02
    # VLMAX = (128/32)*1 = 4
#    li x30, 12         # Set current test case number
#    vsetvli x5, x0, 0x02 # rs1=x0 requests VLMAX
#    csrr x10, vl
#    csrr x11, vtype
#    li x28, 4          # Expected vl = VLMAX = 4
#    bne x5, x28, fail_test
#    bne x10, x28, fail_test
#    li x28, 0x02       # Expected vtype (vill=0)
#    bne x11, x28, fail_test

    # Test Case 13: Illegal, vill set in imm[10]
    # imm[10:0] = VTYPE: vill=1, vma=0, vta=0, vsew=8(000), lmul=m1(000) => 0b10000000000 = 0x400
#    li x30, 13         # Set current test case number
#    li x6, 10
#    vsetvli x5, x6, 0x400
#    csrr x10, vl
#    csrr x11, vtype
#    li x28, 0          # Expected vl = 0
#    bne x5, x28, fail_test
#    bne x10, x28, fail_test
#    srli x12, x11, 31
#    li x28, 1
#    bne x12, x28, fail_test

    # Test Case 14: Illegal, reserved bit set in imm[9]
    # imm[10:0] = VTYPE: vill=0, res=1, res=0, vma=0, vta=0, vsew=8(000), lmul=m1(000) => 0b01000000000 = 0x200
#    li x30, 14         # Set current test case number
#    li x6, 10
#    vsetvli x5, x6, 0x200
#    csrr x10, vl
#    csrr x11, vtype
#    li x28, 0          # Expected vl = 0
#    bne x5, x28, fail_test
#    bne x10, x28, fail_test
#    srli x12, x11, 31
#    li x28, 1
#    bne x12, x28, fail_test

    # Test Case 15: Illegal, SEW=64 requested (vsew=011) on ELEN=32
    # imm[10:0] = VTYPE: vill=0, vma=0, vta=0, vsew=64(011), lmul=m1(000) => 0b000000011 = 0x03
#    li x30, 15         # Set current test case number
#    li x6, 10
#    vsetvli x5, x6, 0x03
#    csrr x10, vl
#    csrr x11, vtype
#    li x28, 0          # Expected vl = 0
#    bne x5, x28, fail_test
#    bne x10, x28, fail_test
#    srli x12, x11, 31
#    li x28, 1
#    bne x12, x28, fail_test


    # === Final Success ===
    li a0, 1           # Success code
    j end_test

fail_test:
    # Load the test case number into a0 (high bit indicates failure: test_num << 1)
    # Test case number is expected in x30 before jumping here
    slli a0, x30, 1
    # Fall through to end_test

end_test:
    # Write result code to tohost address expected by WorkspaceRegression
    # Ensure RVVTest::dBusAccess uses TOHOST_ADDR correctly
    li x1, 0xF0010000 # Use address expected by WorkspaceRegression
    sw a0, 0(x1)

    # Loop forever
1:  j 1b


.section .data
.align 4
tohost: .word 0
fromhost: .word 0