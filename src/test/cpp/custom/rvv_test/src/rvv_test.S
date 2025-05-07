.section .text
.globl _start

# Define constants for test results
.equ FAIL_VL, 2
.equ FAIL_VTYPE, 3
.equ FAIL_RD, 4

_start:
    # Test Case: AVL=10, req_vtype(sew=32, lmul=1)
    # Expected: vl=4, vtype=0x02, rd=4 (assuming VLEN=128, ELEN>=32)
    # Previous vtype value 0x08 was incorrect for sew=32, lmul=1.

    li x6, 10           # rs1 = AVL = 10
    # --- Use correct vtype encoding for sew=32, lmul=1 ---
    li x7, 0x02         # rs2 = requested vtype (sew=32, lmul=1)

    # Execute vsetvl
    vsetvl x5, x6, x7   # rd=x5, rs1=x6, rs2=x7

    # Read results
    csrr t0, vl         # Read vl CSR -> t0
    csrr t1, vtype    # Read vtype CSR -> t1
    mv   t2, x5         # Read rd -> t2

    # Load expected values
    li t3, 4            # Expected vl = 4
    # --- Use correct expected vtype --- 
    li t4, 0x02         # Expected vtype = 0x02
    # Expected rd is same as vl, use t3

    # Check results
    bne t0, t3, fail_vl      # Branch if vl != expected_vl
    bne t1, t4, fail_vtype   # Branch if vtype != expected_vtype
    bne t2, t3, fail_rd      # Branch if rd != expected_vl (expected_rd)

pass:
    li t0, 1            # Success code = 1
    j report_result

fail_vl:
    li t0, FAIL_VL      # Fail code for vl mismatch
    j report_result

fail_vtype:
    li t0, FAIL_VTYPE   # Fail code for vtype mismatch
    j report_result

fail_rd:
    li t0, FAIL_RD      # Fail code for rd mismatch
    # Fall through to report_result

report_result:
    # --- Load the address 0xF0010000 directly --- 
    # Load the address of TOHOST_ADDR (defined in linker script)
    # lui t5, %hi(TOHOST_ADDR)          # Load upper 20 bits
    # addi t5, t5, %lo(TOHOST_ADDR)     # Add lower 12 bits
    li t5, 0xF0010000         # Load the target address directly

    # Write result code to tohost
    sw t0, 0(t5)

    # Halt execution
    ebreak

# End of program