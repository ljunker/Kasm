# KASM

KASM is a small assembly language with its own assembler, bytecode virtual
machine, and source-level CLI debugger. It is built as a compact playground for
machine-level language features: registers, jumps, flags, direct memory access,
and a stack for function calls.

## What Is Included

- A Kotlin assembler that turns `.kasm` source files into bytecode.
- A bytecode VM with four general-purpose registers, result flags, data memory,
  stack operations, and call/return support.
- A CLI that can run source programs and debug them with source-line
  breakpoints.
- Example programs for loops, comparisons, memory operations, and nested calls.
- A TextMate grammar under `kasm-textmate` for editor syntax highlighting.

## Quick Start

Run the countdown example during development:

```sh
./gradlew run --args='run examples/countdown.kasm'
```

Run the test suite:

```sh
./gradlew test
```

The normal run mode prints the assembled bytecode and then the program output:

```text
Bytecode:
01 00 05 06 00 0A 00 08 00 03 FF

Output:
5
4
3
2
1
```

## A First Program

`examples/countdown.kasm` prints a countdown with a register loop:

```kasm
  MOV R0, 5

loop:
  PRINT R0
  DEC R0
  JNZ R0, loop
  HALT
```

KASM labels stay at the left edge. Instructions in the examples are indented by
two spaces to keep the control-flow structure easy to scan.

## Language Snapshot

The current language has four general-purpose registers: `R0` through `R3`.
Numeric operands may be decimal, hexadecimal with `0x`, or binary with `0b`.

Implemented instruction groups:

| Area                  | Instructions                   |
|-----------------------|--------------------------------|
| Data movement         | `MOV`, `LOAD`, `STORE`         |
| Arithmetic            | `ADD`, `SUB`, `INC`, `DEC`     |
| Comparisons and flags | `CMP`, `JE`, `JNE`, `JG`, `JL` |
| Register-based jumps  | `JMP`, `JZ`, `JNZ`             |
| Stack and calls       | `PUSH`, `POP`, `CALL`, `RET`   |
| Output and stop       | `PRINT`, `HALT`                |

The [language reference](docs/language-reference.md) describes the operand
forms, instruction effects, flag rules, memory model, and stack behavior in more
detail.

## Comparisons And Flags

`CMP` compares two registers by updating result flags. The registers themselves
are left unchanged.

```kasm
; Print the larger value with CMP and flag-based jumps.
  MOV R0, 21
  MOV R1, 13

  CMP R0, R1
  JG use_left

  MOV R2, R1
  JMP print_result

use_left:
  MOV R2, R0

print_result:
  PRINT R2
  HALT
```

The VM currently tracks Zero and Sign flags. Flag jumps use the most recent
flagged result, while `JZ` and `JNZ` test a register value directly.

## Memory

The VM keeps program bytecode separate from data memory. Data memory currently
has 256 directly addressed cells.

```kasm
; Store two values in memory, swap them, then print both cells.
  MOV R0, 4
  MOV R1, 9
  STORE [40], R0
  STORE [41], R1
  CALL swap
  CALL print
  HALT

swap:
  LOAD R2, [40]
  LOAD R3, [41]
  STORE [40], R3
  STORE [41], R2
  RET

print:
  LOAD R0, [40]
  PRINT R0
  LOAD R0, [41]
  PRINT R0
  RET
```

`LOAD` and `STORE` currently use direct memory addresses such as `[40]`.
Indirect address forms like `[R1]` are not implemented yet.

## Stack And Calls

`CALL` stores a return address on the VM stack, and `RET` jumps back to it.
Programs can use `PUSH` and `POP` on the same stack to save register values
around nested calls.

```kasm
; Print triple(6) with nested CALL/RET and saved registers.
  MOV R0, 6
  CALL triple
  PRINT R0
  HALT

triple:
  PUSH R1
  MOV R1, R0
  CALL double
  ADD R0, R1
  POP R1
  RET

double:
  PUSH R1
  MOV R1, R0
  ADD R0, R1
  POP R1
  RET
```

The stack lives in data memory and grows downward from the high end of the
256-cell memory space.

## Install The CLI

The Gradle application distribution is the runnable KASM installation. Its
generated launcher needs the sibling `lib` directory from the same distribution,
so do not copy only the `bin/kasm` script into another directory.

Build the local distribution:

```sh
./gradlew installDist
```

Install that distribution into a stable location and expose only a symlink from
your personal `bin` directory:

```sh
mkdir -p "$HOME/opt"
rsync -a --delete build/install/kasm/ "$HOME/opt/kasm/"
mkdir -p "$HOME/bin"
ln -sfn "$HOME/opt/kasm/bin/kasm" "$HOME/bin/kasm"
```

Ensure `~/bin` is on your shell `PATH`. For zsh, put this in `~/.zshrc` if it is
not already there:

```sh
export PATH="$HOME/bin:$PATH"
```

After opening a new shell or reloading the shell config, KASM can run source
files from any working directory:

```sh
kasm run /path/to/program.kasm
kasm debug /path/to/program.kasm
```

`kasm /path/to/program.kasm` is kept as a short form of `kasm run`.

## Debugger

Start the source debugger with the `debug` CLI mode:

```sh
kasm debug examples/stack-calls.kasm
```

Useful debugger commands:

| Command       | Effect                                                    |
|---------------|-----------------------------------------------------------|
| `br 5`        | Set a breakpoint on executable source line 5.             |
| `run`         | Start or continue until the next breakpoint or `HALT`.    |
| `step`        | Execute the next source instruction.                      |
| `state`       | Print registers, flags, stack, and non-zero memory cells. |
| `breakpoints` | List active breakpoints.                                  |
| `help`        | Print debugger commands.                                  |
| `quit`        | Leave the debugger.                                       |

When a breakpoint is hit, the debugger prints the next source instruction,
instruction pointer, stack pointer, register values, flags, active stack cells,
and non-zero memory cells outside the active stack.

## Examples

| Program                     | Demonstrates                     | Output          |
|-----------------------------|----------------------------------|-----------------|
| `examples/countdown.kasm`   | `DEC`, `JNZ`, loops              | `5` through `1` |
| `examples/compare-max.kasm` | `CMP`, flags, register moves     | `21`            |
| `examples/memory-swap.kasm` | direct memory access and calls   | `9`, then `4`   |
| `examples/stack-calls.kasm` | nested calls and saved registers | `18`            |

Run any example by passing its source path to the CLI:

```sh
kasm run examples/memory-swap.kasm
```

## Project Layout

| Path                              | Purpose                                                    |
|-----------------------------------|------------------------------------------------------------|
| `src/main/kotlin/de/ljunker/kasm` | Assembler, VM, debugger, opcode model, and CLI entrypoint. |
| `src/test/kotlin/de/ljunker/kasm` | Unit tests, debugger tests, and example golden tests.      |
| `examples`                        | KASM source programs.                                      |
| `docs/language-reference.md`      | Current language and VM reference.                         |
| `kasm-textmate`                   | TextMate grammar for `.kasm` files.                        |
