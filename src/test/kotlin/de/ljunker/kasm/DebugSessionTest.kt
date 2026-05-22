package de.ljunker.kasm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DebugSessionTest {

    @Test
    fun runsHeadlessUntilBreakpointsAndExposesSnapshots() {
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
        val session = DebugSession(
            debugProgram = debugProgram,
            output = { line -> output += line }
        )

        assertEquals(null, session.setBreakpoint(2))
        assertEquals(LineBreakpoint(lineNumber = 3, address = 3), session.setBreakpoint(3))

        val firstHit = assertIs<DebugStop.BreakpointHit>(session.run())

        assertEquals(LineBreakpoint(lineNumber = 3, address = 3), firstHit.breakpoint)
        assertEquals(2, firstHit.snapshot.vm.registers[0])
        assertEquals(3, firstHit.snapshot.nextLocation?.lineNumber)
        assertEquals(emptyList(), output)

        val step = assertIs<DebugStop.Stepped>(session.step())

        assertEquals(listOf("2"), output)
        assertEquals(4, step.snapshot.nextLocation?.lineNumber)

        val secondHit = assertIs<DebugStop.BreakpointHit>(session.run())

        assertEquals(1, secondHit.snapshot.vm.registers[0])
        assertEquals(listOf(LineBreakpoint(lineNumber = 3, address = 3)), session.breakpoints())
        assertEquals(true, session.removeBreakpoint(3))

        val halt = assertIs<DebugStop.Halted>(session.run())

        assertEquals(false, halt.snapshot.vm.running)
        assertEquals(listOf("2", "1"), output)
    }

    @Test
    fun reportsVmErrorsAsHeadlessStops() {
        val session = DebugSession(
            debugProgram = Assembler().assembleWithDebugInfo(
                """
                POP R0
                HALT
                """.trimIndent()
            )
        )

        val error = assertIs<DebugStop.VmError>(session.step())

        assertEquals("Stack underflow", error.error.message)
    }
}
