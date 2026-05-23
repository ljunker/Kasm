package de.ljunker.kasm

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

class ExampleProgramGoldenTest {

    @Test
    fun examplesHaveStableBytecode() {
        for (example in examples) {
            val program = Assembler().assemble(example.source())

            assertEquals(example.hexDump, program.hexDump(), example.fileName)
        }
    }

    @Test
    fun examplesHaveStableOutput() {
        for (example in examples) {
            val output = mutableListOf<String>()
            val program = Assembler().assemble(example.source())

            VirtualMachine { line -> output += line }.run(program)

            assertEquals(example.output, output, example.fileName)
        }
    }

    private data class Example(
        val fileName: String,
        val hexDump: String,
        val output: List<String>
    ) {
        fun source(): String =
            Path.of("examples", fileName).readText()
    }

    private companion object {
        val examples = listOf(
            Example(
                fileName = "countdown.kasm",
                hexDump = "01 00 05 06 00 0A 00 08 00 03 00 FF",
                output = listOf("5", "4", "3", "2", "1")
            ),
            Example(
                fileName = "compare-max.kasm",
                hexDump = "01 00 15 01 01 0D 0B 00 01 0E 12 00 07 02 01 04 15 00 " +
                        "07 02 00 06 02 FF",
                output = listOf("21")
            ),
            Example(
                fileName = "memory-swap.kasm",
                hexDump = "14 07 00 14 20 00 FF 12 02 12 03 " +
                        "10 02 28 00 10 03 29 00 11 28 00 03 11 29 00 02 13 03 13 02 " +
                        "15 10 00 28 00 06 00 10 00 29 00 06 00 15",
                output = listOf("9", "4")
            ),
            Example(
                fileName = "memory-layout.kasm",
                hexDump = "10 00 50 00 11 60 00 00 10 00 51 00 11 61 00 00 " +
                        "10 01 60 00 10 02 61 00 02 01 02 06 01 01 00 10 06 00 FF",
                output = listOf("18", "16")
            ),
            Example(
                fileName = "memory-strings.kasm",
                hexDump = "01 02 00 16 00 60 00 02 05 00 13 00 06 00 09 02 04 03 00 FF",
                output = listOf("75", "65", "83", "77")
            ),
            Example(
                fileName = "ascii-print.kasm",
                hexDump = "27 00 00 12 2B 00 00 05 00 12 00 26 00 29 00 04 04 00 FF",
                output = listOf("K", "A", "S", "M", "\n")
            ),
            Example(
                fileName = "aoc-2025-day1-sample.kasm",
                hexDump = "27 00 00 20 2B 00 00 05 00 17 00 14 1E 00 14 62 00 14 9E 00 " +
                        "04 04 00 10 00 2A 20 06 00 FF 2B 00 00 11 27 20 00 29 00 24 01 " +
                        "11 28 20 01 2B 00 00 05 00 61 00 01 02 0A 0B 00 02 0C 5F 00 " +
                        "01 02 30 03 00 02 10 01 28 20 01 02 0A 1A 01 02 02 01 00 " +
                        "01 02 64 1C 01 02 11 28 20 01 29 00 04 2D 00 29 00 15 " +
                        "10 00 27 20 01 02 4C 0B 00 02 0C 85 00 10 00 29 20 " +
                        "10 01 28 20 02 00 01 01 02 64 1C 00 02 11 29 20 00 15 " +
                        "10 00 29 20 10 01 28 20 0B 00 01 22 96 00 18 00 64 03 00 01 " +
                        "11 29 20 00 15 10 00 29 20 08 00 B0 00 10 01 2A 20 09 01 " +
                        "11 2A 20 01 15",
                output = listOf("3")
            ),
            Example(
                fileName = "stack-calls.kasm",
                hexDump = "01 00 06 14 09 00 06 00 FF 12 01 07 01 00 14 17 00 " +
                        "02 00 01 13 01 15 12 01 07 01 00 02 00 01 13 01 15",
                output = listOf("18")
            )
        )
    }
}
