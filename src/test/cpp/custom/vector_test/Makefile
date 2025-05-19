RISCV_PREFIX ?= riscv64-unknown-elf-
RISCV_GCC ?= $(RISCV_PREFIX)gcc
RISCV_OBJCOPY ?= $(RISCV_PREFIX)objcopy
RISCV_OBJDUMP ?= $(RISCV_PREFIX)objdump

SOURCEDIR = src
BUILDDIR = build

# 向量扩展支持
RISCV_ARCH = rv32imv
RISCV_ABI = ilp32

# RISC-V架构名称
MARCH = -march=$(RISCV_ARCH)
MABI = -mabi=$(RISCV_ABI)

# 编译器选项
CFLAGS = -g -O3 $(MARCH) $(MABI) -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles
LDFLAGS = -T $(SOURCEDIR)/vector_test.ld -nostdlib

SOURCES = $(SOURCEDIR)/vector_test.S
TARGET = vector_test

all: $(BUILDDIR) $(BUILDDIR)/$(TARGET).elf $(BUILDDIR)/$(TARGET).hex $(BUILDDIR)/$(TARGET).bin $(BUILDDIR)/$(TARGET).dump

$(BUILDDIR):
	mkdir -p $(BUILDDIR)

$(BUILDDIR)/$(TARGET).elf: $(SOURCES) $(SOURCEDIR)/vector_test.ld
	$(RISCV_GCC) $(CFLAGS) $(LDFLAGS) $(SOURCES) -o $@

$(BUILDDIR)/$(TARGET).bin: $(BUILDDIR)/$(TARGET).elf
	$(RISCV_OBJCOPY) -O binary $< $@

$(BUILDDIR)/$(TARGET).hex: $(BUILDDIR)/$(TARGET).elf
	$(RISCV_OBJCOPY) -O ihex $< $@

$(BUILDDIR)/$(TARGET).dump: $(BUILDDIR)/$(TARGET).elf
	$(RISCV_OBJDUMP) --disassemble-all --disassemble-zeroes --section=.text --section=.data $< > $@

clean:
	rm -rf $(BUILDDIR)

.PHONY: all clean 