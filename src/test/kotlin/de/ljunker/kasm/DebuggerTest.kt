package de.ljunker.kasm

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
}
