# RISC-V 向量扩展测试程序

本程序测试 RISC-V 向量扩展 (V) 的基本功能，主要集中在 `vsetvl` 指令族上。

## 测试内容

测试程序验证了以下向量指令：

1. `vsetvli` - 设置向量长度，带立即数vtype
2. `vsetvl` - 设置向量长度，寄存器vtype

测试覆盖了不同的情况：
- 各种元素宽度 (VSEW: 8/16/32位)
- 各种寄存器组乘数 (LMUL: 1/2, 1, 2)
- 不同的应用向量长度 (AVL)
- 特殊情况 (AVL=0)

## 编译测试程序

确保您有RISC-V工具链且支持向量扩展，然后执行：

```bash
cd src/test/cpp/custom/vector_test
make
```

这将生成：
- `build/vector_test.elf` - 可执行文件
- `build/vector_test.hex` - HEX格式文件，用于加载到模拟器
- `build/vector_test.bin` - 二进制文件
- `build/vector_test.dump` - 反汇编文件

## 运行测试

有两种方式运行测试：

### 1. 使用内置的VectorTest类

```bash
cd src/test/cpp/regression
make CFLAGS="-DVECTOR_TEST -DVLEN=128 -DIBUS_SIMPLE -DDBUS_SIMPLE"
```

### 2. 运行自定义汇编测试程序

首先编译测试程序：

```bash
cd src/test/cpp/custom/vector_test
make
```

然后编译并运行回归测试框架：

```bash
cd src/test/cpp/regression
make CFLAGS="-DCUSTOM_VECTOR_TEST -DVLEN=128 -DIBUS_SIMPLE -DDBUS_SIMPLE"
```

## 仿真波形调试

如需查看波形进行调试，可添加TRACE参数：

```bash
make CFLAGS="-DCUSTOM_VECTOR_TEST -DVLEN=128 -DIBUS_SIMPLE -DDBUS_SIMPLE -DTRACE -DTRACE_START=100"
```

## 测试结果

自定义汇编测试程序会将结果写入内存地址`0xF00FFF20`：
- 0: 所有测试通过
- 1: vsetvli测试失败
- 2: vsetvl测试失败  

## 依赖

- RISC-V GCC工具链，支持V扩展（版本10.0以上）
- Verilator（用于仿真VexRiscv处理器） 