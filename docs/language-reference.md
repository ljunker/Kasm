# KASM Language Reference

This document describes the currently implemented KASM language and VM model.

## Source Form

A KASM source file is a sequence of labels, directives, and instructions.

```kasm
; Comments start with semicolon.
.equ START_VALUE, 3

.org 40
counter:
  .byte START_VALUE

start:
  LOAD R0, [counter]

loop:
  PRINT R0
  DEC R0
  JNZ R0, loop
  HALT
```

Labels use `name:` and may appear before an instruction or directive on the
same line or on their own line. A label attached to an instruction resolves to
that bytecode address. A label attached to `.byte`, `.ascii`, or `.string` data
resolves to that data-memory address. Instruction, directive, and register
names are case-insensitive.

Numeric literals may be written in decimal, hexadecimal with `0x`, or binary
with `0b`.

## Operands

KASM currently uses these operand forms:

| Operand type     | Form                                             | Meaning                                       |
|------------------|--------------------------------------------------|-----------------------------------------------|
| Register         | `R0` through `R3`                                | One of the four general-purpose byte registers. |
| Address register | `A0` or `A1`                                    | One of the two 16-bit address registers.      |
| Byte value       | `42`, `COUNT`, or `end - start`                 | A value expression that resolves to `0..255`. |
| Address value    | `0x1234`, `buffer`, or `end - start`            | An address expression that resolves to `0..65535`. |
| Jump target      | An address-value expression                     | A bytecode address that the VM may jump to.   |
| Memory address   | `[40]`, `[buffer + R2]`, `[0x1200]`, or `[A0]`  | A direct, indexed, or pointer data-memory cell. |

Byte values are checked against the 8-bit data range. Jump targets, direct
memory addresses, `.org`, and the static base of indexed memory addresses are
checked against the 16-bit address range by the assembler. Jump and call
targets are also validated by the VM against the loaded program size when they
execute.

## Symbols, Expressions, And Data

`.equ` defines a named constant. Constants, code labels, and data labels share
one symbol namespace and can appear in byte-value and address expressions. Current
expressions support numeric literals, symbols, parentheses, unary `-`, `+`, and
`-`:

```kasm
.equ BUFFER_BASE, 64

.org BUFFER_BASE
buffer:
  .byte 4, 9
buffer_end:
  .byte 0

  LOAD R0, [buffer + 1]
  MOV R1, buffer_end - buffer
```

Data directives initialize the VM data memory before the first instruction
executes:

| Directive         | Effect                                                      |
|-------------------|-------------------------------------------------------------|
| `.equ NAME, expr` | Define a symbol whose value is computed from an expression. |
| `.org expr`       | Move the data-memory layout cursor to address `expr`.       |
| `.byte expr, ...` | Initialize one data-memory cell per byte expression.        |
| `.ascii "text"`   | Initialize one cell per ASCII byte without a terminator.    |
| `.string "text"`  | Initialize ASCII bytes followed by one zero byte.           |

`.org` affects only the data-memory layout cursor. It does not add bytecode or
change bytecode instruction addresses. Reinitializing the same data-memory
address through overlapping data directives is an assembly error. String
directives support ASCII text and the escapes `\0`, `\n`, `\r`, `\t`, `\"`,
and `\\`.

There is no `.word` directive yet because the current stored word size is one
8-bit cell.

## Architecture

KASM currently uses 8-bit data with 16-bit addresses:

- Program bytes, byte-register values, and data-memory cells use values in
  `0..255`.
- Bytecode addresses, data-memory addresses, and address-register values use
  values in `0..65535`.
- A program image must fit in the 65536-byte bytecode address space.
- Data memory has 65536 8-bit cells.
- Registers and data-memory cells expose their stored unsigned byte values.
  `PRINT` therefore prints values in `0..255`.
- `ADD`, `SUB`, `INC`, and `DEC` write wrapped 8-bit results. For example,
  incrementing `255` stores `0`.
- Jump targets, direct memory addresses, indexed-memory bases, and `MOVA`
  address immediates are encoded as two little-endian operand bytes.
- Indexed memory forms add a 16-bit static base and one 8-bit byte-register
  offset; the effective data-memory address wraps to `0..65535`.
- `A0` and `A1` hold 16-bit addresses for pointer-style memory access through
  `[A0]` and `[A1]`.

Signed interpretation is used by the signed flag jumps after a flagged
operation. In that interpretation the same stored byte range represents
`-128..127`; for example, stored value `255` is signed `-1`.

## Instructions

| Instruction | Operands                   | Effect                                                   |
|-------------|----------------------------|----------------------------------------------------------|
| `MOV`       | `register, byte-value`     | Copy a value into a register.                            |
| `MOV`       | `register, register`       | Copy one register into another register.                 |
| `MOVA`      | `address-register, address-value` | Copy a 16-bit address into an address register.    |
| `MOVA`      | `address-register, address-register` | Copy one address register into another.          |
| `ADD`       | `register, register`       | Add into the target register with an 8-bit wrapped result. |
| `ADDI`      | `register, byte-value`     | Add an immediate byte value into the target register.    |
| `SUB`       | `register, register`       | Subtract into the target register with an 8-bit wrapped result. |
| `SUBI`      | `register, byte-value`     | Subtract an immediate byte value from the target register. |
| `INC`       | `register`                 | Increment one register with an 8-bit wrapped result.     |
| `DEC`       | `register`                 | Decrement one register with an 8-bit wrapped result.     |
| `MUL`       | `register, register`       | Multiply into the target register with an 8-bit wrapped result. |
| `DIV`       | `register, register`       | Divide the target register by the source register.       |
| `MOD`       | `register, register`       | Store the target modulo the source register.             |
| `NEG`       | `register`                 | Two's-complement negate one register.                    |
| `AND`       | `register, register`       | Bitwise AND into the target register.                    |
| `OR`        | `register, register`       | Bitwise OR into the target register.                     |
| `XOR`       | `register, register`       | Bitwise XOR into the target register.                    |
| `NOT`       | `register`                 | Bitwise invert one register.                             |
| `CMP`       | `register, register`       | Compare two registers by updating result flags.          |
| `JMP`       | `jump-target`              | Jump unconditionally.                                    |
| `JZ`        | `register, jump-target`    | Jump when the register value is zero.                    |
| `JNZ`       | `register, jump-target`    | Jump when the register value is non-zero.                |
| `JE`        | `jump-target`              | Jump when the Zero flag is set.                          |
| `JNE`       | `jump-target`              | Jump when the Zero flag is clear.                        |
| `JG`        | `jump-target`              | Signed jump when the last comparison was greater.        |
| `JGE`       | `jump-target`              | Signed jump when the last comparison was greater or equal. |
| `JL`        | `jump-target`              | Signed jump when the last comparison was less.           |
| `JLE`       | `jump-target`              | Signed jump when the last comparison was less or equal.  |
| `LOAD`      | `register, memory-address` | Load one data-memory cell into a register.               |
| `STORE`     | `memory-address, register` | Store a register value into one data-memory cell.        |
| `INCA`      | `address-register`         | Increment one address register with 16-bit wrapping.     |
| `DECA`      | `address-register`         | Decrement one address register with 16-bit wrapping.     |
| `PUSH`      | `register`                 | Push a register value on the stack.                      |
| `POP`       | `register`                 | Pop the stack top into a register.                       |
| `CALL`      | `jump-target`              | Push the return address and jump to a function.          |
| `RET`       | none                       | Pop a return address and jump back to it.                |
| `CLR`       | `register`                 | Clear one register to zero.                              |
| `NOP`       | none                       | Do nothing.                                              |
| `PRINT`     | `register`                 | Print the register value as one output line.             |
| `PRINTC`    | `register`                 | Print the register value as one ASCII character.         |
| `HALT`      | none                       | Stop the VM.                                             |

## Flags

The VM exposes four result flags:

| Flag | Meaning |
| --- | --- |
| Zero | The wrapped 8-bit result was zero. |
| Sign | Bit 7 of the wrapped 8-bit result is set. |
| Carry | Addition carried past `255`, or subtraction borrowed below `0`. |
| Overflow | The signed 8-bit result overflowed `-128..127`. |

`CMP left, right` computes the flags from `left - right` without changing either
register. `ADD`, `ADDI`, `SUB`, `SUBI`, `INC`, and `DEC` update the same
addition/subtraction flags. `MUL` stores the wrapped unsigned-byte product,
sets Carry when the unsigned product exceeds `255`, and sets Overflow when the
signed product does not fit in `-128..127`.

`DIV` and `MOD` use the stored unsigned byte values and reject a zero divisor
with a VM error. Successful `DIV`, `MOD`, `AND`, `OR`, `XOR`, `NOT`, and `CLR`
update Zero and Sign, and clear Carry and Overflow. `NEG` sets Carry when the
input was non-zero and sets Overflow when negating stored value `128`.

`MOVA`, `INCA`, `DECA`, `LOAD`, `STORE`, `PUSH`, `POP`, jumps, calls, output
instructions, `NOP`, and `HALT` do not update result flags.

`JE` and `JNE` test the Zero flag. `JG`, `JGE`, `JL`, and `JLE` are signed
comparisons based on Zero, Sign, and Overflow. `JZ` and `JNZ` are separate
register-value tests and do not depend on the result flags.

## Memory

Program bytecode and data memory are separate. The current VM provides 65536
8-bit data-memory cells addressed as `0..65535`.

`LOAD` and `STORE` accept direct data-memory addresses, one-register indexed
addresses, and address-register pointer forms. Literals still work, but data
labels make a source layout explicit:

```kasm
.org 0x1200
name:
  .string "OK\n"

  MOVA A0, name
loop:
  LOAD R0, [A0]
  JZ R0, end
  PRINTC R0
  INCA A0
  JMP loop
end:
  HALT
```

Direct forms such as `[name]` and `[name + 1]` are resolved completely by the
assembler. Runtime memory forms include one byte-register offset with a static
base, or one address register:

- `[R2]`
- `[name + R2]`
- `[R2 + name]`
- `[R2 + 4]`
- `[A0]`
- `[A1]`

Address forms with two runtime byte registers such as `[R1 + R2]` and mixed
address-register plus byte-register forms such as `[A0 + R1]` are not
implemented yet.

## Stack and Calls

The stack uses the same 65536-cell data memory and grows downward from the high
end of memory. A `PUSH` writes one byte value at the new top. A `POP` removes
the current top value. The stack pointer is VM state: the debugger shows `SP`,
but KASM programs do not have an `SP` register or direct stack-pointer
instruction yet.

`CALL` pushes the 16-bit return bytecode address as two bytes before it jumps;
the low byte is at the stack top. `RET` pops those two bytes and jumps back to
that address. User `PUSH` and `POP` instructions therefore share stack space
with nested calls.

The VM rejects stack underflow and stack overflow. Programs that lay out fixed
high-memory data must avoid colliding with the active stack.

## Call Convention

KASM does not enforce an ABI in bytecode, but examples and documentation use
this convention:

- `R0` holds the primary input value and return value.
- A callee may overwrite `R0` and all result flags.
- A callee that modifies `R1`, `R2`, or `R3` saves and restores those registers
  with `PUSH` and `POP`.
- `A0` and `A1` are scratch pointer registers until KASM gains address-register
  save/restore instructions.
- A function balances its own saved stack values before `RET`, so the return
  address pushed by `CALL` is the value consumed by `RET`.

Additional arguments can be assigned by a routine-specific contract until KASM
gains a wider calling convention.
