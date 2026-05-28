package de.ljunker.kasm

import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DebuggerTest {

    @Test
    fun stopsAtBreakpointsAndStepsFromSourceLines() {
        val commands = ArrayDeque(
            listOf(
                "br 2",
                "br 3",
                "run",
                "step",
                "run",
                "quit"
            )
        )
        val output = mutableListOf<String>()
        val debugProgram = Assembler().assembleWithDebugInfo(
            """
            MOV R0, 2
            loop:
            PRINT R0
            DEC R0
            JNZ R0, loop
            HALT
            """.trimIndent()
        )

        Debugger(
            debugProgram = debugProgram,
            sourceName = "loop.kasm",
            readCommand = {
                if (commands.isEmpty()) null else commands.removeFirst()
            },
            output = { line -> output += line }
        ).run()

        assertTrue("Line 2 has no executable instruction." in output)
        assertTrue("Breakpoint set at line 3 (address 3)." in output)
        assertEquals(2, output.count { it == "Breakpoint hit at line 3." })
        assertTrue(output.any { it.startsWith("Registers: R0=2 ") })
        assertTrue("2" in output)
        assertTrue(output.any { it.startsWith("Registers: R0=1 ") })
    }

    @Test
    fun printsConstantsAndNum64Variables() {
        val commands = ArrayDeque(listOf("quit"))
        val output = mutableListOf<String>()
        val debugProgram = Assembler().assembleWithDebugInfo(
            """
            .equ START_VALUE, 15
            counter:
              .num64 START_VALUE
            flag:
              .byte 1

              HALT
            """.trimIndent()
        )

        Debugger(
            debugProgram = debugProgram,
            sourceName = "counter.kasm",
            readCommand = {
                if (commands.isEmpty()) null else commands.removeFirst()
            },
            output = { line -> output += line }
        ).run()

        assertTrue("Constants: START_VALUE=15" in output)
        assertTrue("Variables:" in output)
        assertTrue("  counter@0x0000 .num64=15" in output)
        assertTrue("  flag@0x0008 .byte=1" in output)
    }

    @Test
    fun printsFilePointerPositions() {
        val baseDirectory = createTempDirectory("kasm-debugger-file-pointer")
        baseDirectory.resolve("input.bin").writeBytes(byteArrayOf(65))
        val commands = ArrayDeque(listOf("step", "state", "quit"))
        val output = mutableListOf<String>()
        val debugProgram = Assembler(baseDirectory = baseDirectory).assembleWithDebugInfo(
            """
            .file input, "input.bin"
              FREAD R0, input
              HALT
            """.trimIndent()
        )

        Debugger(
            debugProgram = debugProgram,
            sourceName = "file.kasm",
            readCommand = {
                if (commands.isEmpty()) null else commands.removeFirst()
            },
            output = { line -> output += line }
        ).run()

        assertTrue("Files: input@0" in output)
        assertTrue("Files: input@1" in output)
    }
}
