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
                hexDump = "01 00 05 06 00 0A 00 08 00 03 FF",
                output = listOf("5", "4", "3", "2", "1")
            ),
            Example(
                fileName = "compare-max.kasm",
                hexDump = "01 00 15 01 01 0D 0B 00 01 0E 10 07 02 01 04 13 07 02 00 06 02 FF",
                output = listOf("21")
            ),
            Example(
                fileName = "memory-swap.kasm",
                hexDump = "01 00 04 01 01 09 11 28 00 11 29 01 10 02 28 10 03 29 " +
                        "11 28 03 11 29 02 10 00 28 06 00 10 00 29 06 00 FF",
                output = listOf("9", "4")
            ),
            Example(
                fileName = "stack-calls.kasm",
                hexDump = "01 00 06 14 08 06 00 FF 12 01 07 01 00 14 15 02 00 01 13 01 " +
                        "15 12 01 07 01 00 02 00 01 13 01 15",
                output = listOf("18")
            )
        )
    }
}
