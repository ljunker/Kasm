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

## Instructions

| Instruction | Operands                   | Effect                                                   |
|-------------|----------------------------|----------------------------------------------------------|
| `MOV`       | `register, byte-value`     | Copy a value into a register.                            |
| `MOV`       | `register, register`       | Copy one register into another register.                 |
| `ADD`       | `register, register`       | Add the source register to the target register.          |
| `SUB`       | `register, register`       | Subtract the source register from the target register.   |
| `INC`       | `register`                 | Increment one register.                                  |
| `DEC`       | `register`                 | Decrement one register.                                  |
| `CMP`       | `register, register`       | Compare two registers by updating result flags.          |
| `JMP`       | `jump-target`              | Jump unconditionally.                                    |
| `JZ`        | `register, jump-target`    | Jump when the register value is zero.                    |
| `JNZ`       | `register, jump-target`    | Jump when the register value is non-zero.                |
| `JE`        | `jump-target`              | Jump when the Zero flag is set.                          |
| `JNE`       | `jump-target`              | Jump when the Zero flag is clear.                        |
| `JG`        | `jump-target`              | Jump when the last flagged result was greater than zero. |
| `JL`        | `jump-target`              | Jump when the last flagged result was less than zero.    |
| `LOAD`      | `register, memory-address` | Load one data-memory cell into a register.               |
| `STORE`     | `memory-address, register` | Store a register value into one data-memory cell.        |
| `PUSH`      | `register`                 | Push a register value on the stack.                      |
| `POP`       | `register`                 | Pop the stack top into a register.                       |
| `CALL`      | `jump-target`              | Push the return address and jump to a function.          |
| `RET`       | none                       | Pop a return address and jump back to it.                |
| `PRINT`     | `register`                 | Print the register value as one output line.             |
| `HALT`      | none                       | Stop the VM.                                             |

## Flags

The VM currently exposes two result flags:

| Flag | Meaning                                     |
|------|---------------------------------------------|
| Zero | The last flagged result was exactly zero.   |
| Sign | The last flagged result was less than zero. |

`CMP left, right` computes the flags from `left - right` without changing either
register. `ADD`, `SUB`, `INC`, and `DEC` also update the same result flags.

`JE` and `JNE` test the Zero flag. `JG` jumps when Zero and Sign are both clear.
`JL` jumps when Sign is set. `JZ` and `JNZ` are separate register-value tests and
do not depend on the result flags.

## Memory

Program bytecode and data memory are separate. The current VM provides 256
data-memory cells addressed as `0..255`.

`LOAD` and `STORE` use direct data-memory addresses only:

```kasm
MOV R0, 9
STORE [40], R0
LOAD R1, [40]
```

Indirect forms such as `[R1]` and `[R1 + 4]` are not implemented yet.

## Stack and Calls

The stack uses the same 256-cell data memory and grows downward from the high
end of memory. A `PUSH` writes one value at the new top. A `POP` removes the
current top value.

`CALL` pushes the return bytecode address before it jumps. `RET` pops that
address and jumps back to it. User `PUSH` and `POP` instructions therefore share
stack space with nested calls.

The VM rejects stack underflow and stack overflow. Programs that use fixed
high-memory cells with `LOAD` or `STORE` must avoid colliding with the active
stack until a more explicit memory layout exists.
