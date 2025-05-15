PROJ_NAME ?= vsetvli_basic_01 # Default project name, can be overridden from command line

RISCV_PATH ?= /Users/liupeng/sifive_toolchain_v2020.12
CFLAGS += -march=rv32imv -mabi=ilp32 # Enable M and V extensions
# If your V extension is a custom one not recognized by standard -march flags, 
# you might need to only use -march=rv32im and ensure your .word encodings are correct.

RISCV_NAME = riscv64-unknown-elf
RISCV_OBJCOPY = $(RISCV_PATH)/bin/$(RISCV_NAME)-objcopy
RISCV_OBJDUMP = $(RISCV_PATH)/bin/$(RISCV_NAME)-objdump
RISCV_CC = $(RISCV_PATH)/bin/$(RISCV_NAME)-gcc
LDSCRIPT = src/ld

# 找到所有的测试源文件
ALL_SRCS = $(wildcard src/*.S)
# 从源文件路径中提取基本文件名（不含路径和扩展名）
ALL_TESTS = $(basename $(notdir $(ALL_SRCS)))

CFLAGS += -static
LDFLAGS += -e_start -T $(LDSCRIPT) -nostartfiles -Wl,-Map,$(OBJDIR)/$*.map -Wl,--print-memory-usage

OBJDIR = build

# 为每个测试文件定义对应的.o, .elf, .hex, .asm文件
ALL_OBJS = $(addprefix $(OBJDIR)/, $(addsuffix .o, $(ALL_TESTS)))
ALL_ELFS = $(addprefix $(OBJDIR)/, $(addsuffix .elf, $(ALL_TESTS)))
ALL_HEXS = $(addprefix $(OBJDIR)/, $(addsuffix .hex, $(ALL_TESTS)))
ALL_ASMS = $(addprefix $(OBJDIR)/, $(addsuffix .asm, $(ALL_TESTS)))

# 默认目标：构建所有测试
all: $(ALL_HEXS) $(ALL_ASMS)
	@echo "所有测试文件编译完成"
	@echo "编译的测试文件: $(ALL_TESTS)"

# 从.elf生成.hex文件的规则
$(OBJDIR)/%.hex: $(OBJDIR)/%.elf
	@echo "生成HEX文件: $@"
	$(RISCV_OBJCOPY) -O ihex $< $@

# 从.o文件链接生成.elf文件的规则
$(OBJDIR)/%.elf: $(OBJDIR)/%.o $(LDSCRIPT) | $(OBJDIR)
	@echo "链接ELF文件: $@"
	$(RISCV_CC) $(CFLAGS) -o $@ $< $(LDFLAGS)

# 生成汇编列表的规则
$(OBJDIR)/%.asm: $(OBJDIR)/%.elf
	@echo "生成汇编列表: $@"
	$(RISCV_OBJDUMP) -S -d $< > $@

# 编译.S文件为.o文件的规则
$(OBJDIR)/%.o: src/%.S | $(OBJDIR)
	@echo "编译汇编文件: $< -> $@"
	$(RISCV_CC) -c $(CFLAGS) -o $@ $< -D__ASSEMBLY__=1

# 创建build目录
$(OBJDIR):
	@mkdir -p $@

# 添加一个单独编译特定测试的目标
test: $(OBJDIR)/$(TEST).hex $(OBJDIR)/$(TEST).asm
	@echo "编译完成: $(TEST)"

# 清理生成的文件
clean:
	@echo "清理所有生成的文件"
	rm -f $(ALL_OBJS)
	rm -f $(ALL_ELFS)
	rm -f $(ALL_HEXS)
	rm -f $(ALL_ASMS)
	rm -f $(OBJDIR)/*.map

.PHONY: all clean test

# 显示帮助信息
help:
	@echo "可用的make目标:"
	@echo "  all       - 编译所有测试文件（默认）"
	@echo "  test TEST=文件名 - 编译单个测试文件（不含.S扩展名）"
	@echo "  clean     - 清理所有生成的文件"
	@echo "  help      - 显示此帮助信息"

.PHONY: help

# Use .SECONDARY to keep intermediate .o files if not explicitly listed as prerequisites of .elf
# However, with explicit $(OBJS) in elf rule, it's fine.
# .SECONDARY: 