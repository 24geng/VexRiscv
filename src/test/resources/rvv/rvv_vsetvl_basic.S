# Basic test for vsetvl and vsetvli
# We assume XLEN=32 for register setup

.global _start

_start:
  # Test 1: vsetvli with valid parameters
  # Request avl=10, vsew=32b (010), lmul=1 (000), tail agnostic, mask agnostic
  # vtype immediate = 0b0_00_1_1_000_010 = 0x182
  # Expected vl = 10 (since 10 < vlmax = 128/4 = 32)
  li x1, 10  # AVL (ignored by vsetvli)
  li x2, 0x182 # vtype immediate
  vsetvli x3, x0, e32, m1, ta, ma # rd=x3, rs1=x0 (AVL), imm=vtype
  # Expected: x3 = 10, vl = 10, vtype = 0x182

  # Test 2: vsetvl with requested AVL > vlmax
  # Use same vtype as above (e32, m1 => vlmax = 32)
  # Request avl=40
  li x1, 40
  # x2 still holds 0x182
  vsetvl x4, x1, x2 # rd=x4, rs1=AVL, rs2=vtype
  # Expected: x4 = 32, vl = 32, vtype = 0x182

  # Test 3: vsetvli with invalid LMUL
  # Request lmul = reserved (100), vsew=8b (000)
  # vtype immediate = 0b0_00_1_1_100_000 = 0x1C0
  li x1, 5 # AVL (ignored)
  li x2, 0x1C0
  vsetvli x5, x0, e8, m4, ta, ma # rd=x5, This encoding uses 'm4' but vtype has lmul=100
  # Expected: x5 = 0, vl = 0, vtype.vill = 1

  # Test 4: vsetvl with unsupported SEW (assuming ELEN=64)
  # Request vsew=128b (invalid), lmul=1
  li x1, 20
  li x2, 0x183 # vsew=111(reserved), lmul=000
  vsetvl x6, x1, x2
  # Expected: x6 = 0, vl = 0, vtype.vill = 1


  # Infinite loop to end simulation
end_loop:
  j end_loop 