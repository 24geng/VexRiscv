OUTPUT_ARCH( "riscv" )
ENTRY(_start)

MEMORY
{
  RAM (rwx) : ORIGIN = 0x80000000, LENGTH = 64K
}

SECTIONS
{
  .text : {
    *(.text)
  } > RAM
  
  .data : {
    *(.data)
    *(.data.*)
    *(.rodata)
    *(.rodata.*)
  } > RAM
  
  .bss : {
    *(.bss)
    *(.bss.*)
  } > RAM
 
  .debug : {
    *(.debug)
  } > RAM
} 