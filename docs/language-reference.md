# KASM Language Reference

This document describes the currently implemented KASM language and VM model.

## Source Form

A KASM source file is a sequence of labels and instructions.

```kasm
; Comments start with semicolon.
start:
  MOV R0, 3

loop:
  PRINT R0
  DEC R0
  JNZ R0, loop
  HALT
```

Labels use `name:` and may appear before an instruction on the same line or on
their own line. Instruction and register names are case-insensitive.

Numeric literals may be written in decimal, hexadecimal with `0x`, or binary
with `0b`.

## Operands

KASM currently uses these operand forms:

| Operand type   | Form                                 | Meaning                                              |
|----------------|--------------------------------------|------------------------------------------------------|
| Register       | `R0` through `R3`                    | One of the four general-purpose registers.           |
| Byte value     | `42`, `0x2A`, `0b101010`, or a label | A value that resolves to `0..255`.                   |
| Jump target    | A byte value or label                | A bytecode address that the VM may jump to.          |
| Memory address | `[40]`, `[0x28]`, or `[label]`       | A direct data-memory cell that resolves to `0..255`. |

Byte values and direct memory addresses are checked by the assembler. Jump and
call targets are also validated by the VM against the loaded program size when
they execute.

## Architecture

KASM currently uses an explicit 8-bit word model:

- Program bytes, bytecode addresses, register values, and data-memory cells use
  values in `0..255`.
- A program image must fit in the 256-byte bytecode address space.
- Registers and data-memory cells expose their stored unsigned byte values.
  `PRINT` therefore prints values in `0..255`.
- `ADD`, `SUB`, `INC`, and `DEC` write wrapped 8-bit results. For example,
  incrementing `255` stores `0`.

Signed interpretation is used by the signed flag jumps after a flagged
operation. In that interpretation the same stored byte range represents
`-128..127`; for example, stored value `255` is signed `-1`.

## Instructions

| Instruction | Operands                   | Effect                                                   |
|-------------|----------------------------|----------------------------------------------------------|
| `MOV`       | `register, byte-value`     | Copy a value into a register.                            |
| `MOV`       | `register, register`       | Copy one register into another register.                 |
| `ADD`       | `register, register`       | Add into the target register with an 8-bit wrapped result. |
| `SUB`       | `register, register`       | Subtract into the target register with an 8-bit wrapped result. |
| `INC`       | `register`                 | Increment one register with an 8-bit wrapped result.     |
| `DEC`       | `register`                 | Decrement one register with an 8-bit wrapped result.     |
| `CMP`       | `register, register`       | Compare two registers by updating result flags.          |
| `JMP`       | `jump-target`              | Jump unconditionally.                                    |
| `JZ`        | `register, jump-target`    | Jump when the register value is zero.                    |
| `JNZ`       | `register, jump-target`    | Jump when the register value is non-zero.                |
| `JE`        | `jump-target`              | Jump when the Zero flag is set.                          |
| `JNE`       | `jump-target`              | Jump when the Zero flag is clear.                        |
| `JG`        | `jump-target`              | Signed jump when the last flagged result was greater than zero. |
| `JL`        | `jump-target`              | Signed jump when the last flagged result was less than zero. |
| `LOAD`      | `register, memory-address` | Load one data-memory cell into a register.               |
| `STORE`     | `memory-address, register` | Store a register value into one data-memory cell.        |
| `PUSH`      | `register`                 | Push a register value on the stack.                      |
| `POP`       | `register`                 | Pop the stack top into a register.                       |
| `CALL`      | `jump-target`              | Push the return address and jump to a function.          |
| `RET`       | none                       | Pop a return address and jump back to it.                |
| `PRINT`     | `register`                 | Print the register value as one output line.             |
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
register. `ADD`, `SUB`, `INC`, and `DEC` also update the same result flags.

`JE` and `JNE` test the Zero flag. `JG` and `JL` are signed comparisons: `JG`
jumps when Zero is clear and Sign equals Overflow; `JL` jumps when Sign differs
from Overflow. `JZ` and `JNZ` are separate register-value tests and do not
depend on the result flags.

## Memory

Program bytecode and data memory are separate. The current VM provides 256
8-bit data-memory cells addressed as `0..255`.

`LOAD` and `STORE` use direct data-memory addresses only:

```kasm
  MOV R0, 9
  STORE [40], R0
  LOAD R1, [40]
```

Indirect forms such as `[R1]` and `[R1 + 4]` are not implemented yet.

## Stack and Calls

The stack uses the same 256-cell data memory and grows downward from the high
end of memory. A `PUSH` writes one byte value at the new top. A `POP` removes
the current top value. The stack pointer is VM state: the debugger shows `SP`,
but KASM programs do not have an `SP` register or direct stack-pointer
instruction yet.

`CALL` pushes the return bytecode address before it jumps. `RET` pops that
address and jumps back to it. User `PUSH` and `POP` instructions therefore share
stack space with nested calls.

The VM rejects stack underflow and stack overflow. Programs that use fixed
high-memory cells with `LOAD` or `STORE` must avoid colliding with the active
stack until a more explicit memory layout exists.

## Call Convention

KASM does not enforce an ABI in bytecode, but examples and documentation use
this convention:

- `R0` holds the primary input value and return value.
- A callee may overwrite `R0` and all result flags.
- A callee that modifies `R1`, `R2`, or `R3` saves and restores those registers
  with `PUSH` and `POP`.
- A function balances its own saved stack values before `RET`, so the return
  address pushed by `CALL` is the value consumed by `RET`.

Additional arguments can be assigned by a routine-specific contract until KASM
gains a wider calling convention.
