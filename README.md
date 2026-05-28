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
  breakpoints, backed by a headless debug session API.
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

The current language has four general-purpose 8-bit registers: `R0` through
`R3`, plus two 16-bit address registers: `A0` and `A1`. Numeric operands may be
decimal, hexadecimal with `0x`, or binary with `0b`; constants and labels can be
combined with small `+`/`-` expressions. Arithmetic on byte registers writes
wrapped byte results, so incrementing stored value `255` produces `0`.

Implemented instruction groups:

| Area                  | Instructions                                      |
|-----------------------|---------------------------------------------------|
| Data movement         | `MOV`, `MOVA`, `LOAD`, `STORE`, `CLR`             |
| Arithmetic            | `ADD`, `ADC`, `ADDI`, `SUB`, `SBC`, `SUBI`, `INC`, `DEC`, `MUL`, `DIV`, `MOD`, `NEG` |
| Bit operations        | `AND`, `OR`, `XOR`, `NOT`                         |
| Comparisons and flags | `CMP`, `JE`, `JNE`, `JG`, `JGE`, `JL`, `JLE`      |
| Register-based jumps  | `JMP`, `JZ`, `JNZ`                                |
| Stack and calls       | `PUSH`, `PUSHI`, `PUSHA`, `POP`, `DROP`, `PEEK`, `PEEKA`, `PUSHF`, `POPF`, `CALL`, `RET` |
| Address arithmetic    | `INCA`, `DECA`                                    |
| Output and stop       | `PRINT`, `PRINTC`, `NOP`, `HALT`                  |

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

The VM tracks Zero, Sign, Carry, and Overflow flags. `ADC` consumes Carry as a
carry-in bit, and `SBC` consumes it as a borrow-in bit, so byte-sized registers
can be chained into larger integer operations. `JG` and `JL` are signed flag
jumps; `JZ` and `JNZ` test a register value directly.

## Memory

The VM keeps program bytecode separate from data memory. Data memory has 65536
directly addressed 8-bit cells. Data directives can initialize named cells
before the first instruction executes:

```kasm
; Initialize two named cells, swap them, then print both values.
.org 40
left:
  .byte 4
right:
  .byte 9

  CALL swap
  CALL print
  HALT

swap:
  PUSH R2
  PUSH R3
  LOAD R2, [left]
  LOAD R3, [right]
  STORE [left], R3
  STORE [right], R2
  POP R3
  POP R2
  RET

print:
  LOAD R0, [left]
  PRINT R0
  LOAD R0, [right]
  PRINT R0
  RET
```

`.equ`, `.org`, `.byte`, `.num64`, `.ascii`, `.string`, `.incbin`, and
`.include` provide a small assembly-time language. Byte operands and direct
memory addresses accept expressions such as `copy - source` and `[source + 1]`.
Direct memory addresses, jump targets, and `.org` use 16-bit addresses, so data
can live above address `255`. `.num64 655361234` reserves eight data-memory
cells and stores the value as little-endian bytes. `.incbin "path"` embeds raw
file bytes at assembly time; relative paths are resolved from the source file's
directory in the CLI.

`.include "path"` expands another KASM source file at that point. Included
labels and constants share the same global namespace as the including file.
Included instructions are real bytecode, so library includes normally go after
the program's `HALT`:

```kasm
  PUSHA value
  CALL u64_print_decimal
  DROP 2
  HALT

  .include "../lib/u64-core.kasm"
  .include "../lib/u64-decimal.kasm"
```

The `lib/` directory contains reusable unsigned 64-bit helpers for `.num64`
storage: core copy/clear/add/sub routines, simple multiplication and division,
decimal parsing, and decimal printing.

`LOAD` and `STORE` also accept one-register indexed forms such as `[R2]`,
`[name + R2]`, and `[R2 + 4]`, plus address-register pointer forms such as
`[A0]`. Address registers are useful for iterating larger memory regions:

```kasm
; Print a zero-terminated string from high memory as ASCII characters.
  .org 0x1200
message:
  .string "KASM\n"

  MOVA A0, message
loop:
  LOAD R0, [A0]
  JZ R0, end
  PRINTC R0
  INCA A0
  JMP loop
end:
  HALT
```

## Stack And Calls

`CALL` stores a return address on the VM stack, and `RET` jumps back to it.
Programs can use `PUSH` and `POP` on the same stack to save register values
around nested calls.

```kasm
; Print triple(6) with explicit stack arguments.
  PUSHI 6
  CALL triple
  DROP 1
  PRINT R0
  HALT

triple:
  PEEK R0, 2
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
65536-cell memory space. The stack pointer is debugger-visible VM state, not a
KASM register. The examples use `R0` for the primary input and return value;
functions save and restore `R1` through `R3` when they modify them.
Stack arguments are explicit: push them before `CALL`, read them in the callee
with `PEEK` or `PEEKA`, and clean them up in the caller with `DROP` after
`RET`. `PUSHA` pushes a 16-bit address argument, so a function can receive
memory pointers or any variable number of pointer parameters. `PUSHF` and
`POPF` save and restore Zero, Sign, Carry, and Overflow, which is useful for
loops around `ADC`/`SBC` carry chains.

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
| `state`       | Print registers, flags, symbols, stack, and non-zero memory cells. |
| `breakpoints` | List active breakpoints.                                  |
| `help`        | Print debugger commands.                                  |
| `quit`        | Leave the debugger.                                       |

When a breakpoint is hit, the debugger prints the next source instruction,
instruction pointer, stack pointer, register values, flags, constants,
debugger-visible data variables, active stack cells, and non-zero memory cells
outside the active stack. `.num64` variables are decoded from their current
little-endian memory bytes and shown as unsigned decimal 64-bit values.

### Headless Debug Sessions

The CLI debugger is an adapter over `DebugSession`. Editor tooling can assemble
source with debug information, set source-line breakpoints, run or step without
a terminal, and read typed stop results plus VM snapshots:

```kotlin
val debugProgram = Assembler().assembleWithDebugInfo(source)
val session = DebugSession(debugProgram) { printedLine ->
    console.print(printedLine)
}

session.setBreakpoint(5)

when (val stop = session.run()) {
    is DebugStop.BreakpointHit -> {
        val vm = stop.snapshot.vm
        val nextLine = stop.snapshot.nextLocation?.lineNumber
        toolWindow.showState(vm, nextLine)
    }
    is DebugStop.Halted -> toolWindow.showHalted(stop.snapshot.vm)
    is DebugStop.VmError -> toolWindow.showError(stop.error.message)
    is DebugStop.Stepped -> toolWindow.showState(stop.snapshot.vm, null)
}
```

`DebugSnapshot.vm` exposes the instruction pointer, stack pointer, flags,
byte registers, address registers, complete memory, and running state.
`DebugSnapshot.nextLocation`
maps the next instruction back to its KASM source line when the source map has a
location for it. `DebugSnapshot.symbols` exposes evaluated `.equ` constants and
current values for labels attached to data directives. `DebugSnapshot.symbolLines`
contains the same formatted constants and newline-separated variable lines that
the CLI debugger prints.

## Examples

| Program                        | Demonstrates                                 | Output                 |
|--------------------------------|----------------------------------------------|------------------------|
| `examples/countdown.kasm`      | `DEC`, `JNZ`, loops                          | `5` through `1`        |
| `examples/compare-max.kasm`    | `CMP`, flags, register moves                 | `21`                   |
| `examples/memory-swap.kasm`    | named initialized memory and calls           | `9`, then `4`          |
| `examples/memory-layout.kasm`  | `.equ`, `.org`, `.byte`, address expressions | `18`, then `16`        |
| `examples/memory-strings.kasm` | `.string` and indexed string iteration       | `75`, `65`, `83`, `77` |
| `examples/ascii-print.kasm`    | `A0`, high memory, and `PRINTC`              | `KASM`                 |
| `examples/incbin-print.kasm`   | `.incbin` file data and `PRINTC`             | `INCBIN`               |
| `examples/wide-add64.kasm`     | 64-bit addition with `ADC`                   | bytes of `0x0000000100000101` |
| `examples/wide-sub64.kasm`     | 64-bit subtraction with `SBC`                | bytes of `0x00000001000000FF` |
| `examples/wide-incdec64.kasm`  | 64-bit increment and decrement               | bytes before and after borrow |
| `examples/wide-mul8x64.kasm`   | 64-bit repeated addition with `ADC`          | bytes of `1500`        |
| `examples/num64-arithmetic.kasm` | `.num64`, stack arguments, 64-bit add/sub/mul/div/mod | bytes of each result |
| `examples/num64-parse-decimal-file.kasm` | `.incbin` text input parsed into `.num64` | bytes of `65535` |
| `examples/num64-print-decimal.kasm` | binary `.num64` converted to decimal ASCII with `PRINTC` | `655361234` |
| `examples/num64-parse-print-decimal.kasm` | `.incbin` decimal input printed back as decimal text | `65535` |
| `examples/num64-varargs.kasm` | variable argument count via `PUSHI`, `PUSHA`, `PEEK`, `PEEKA` | bytes of `342` |
| `examples/aoc-2025-day1-sample.kasm` | parsing ASCII data with `CALL`/`RET`  | `3`                    |
| `examples/aoc-2025-day1-part2.kasm` | AoC Day 1 part 2 with `.include` U64 helpers | `6` |
| `examples/stack-calls.kasm`    | nested calls and saved registers             | `18`                   |

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
| `lib`                             | Reusable KASM library routines included with `.include`.   |
| `docs/language-reference.md`      | Current language and VM reference.                         |
| `kasm-textmate`                   | TextMate grammar for `.kasm` files.                        |
