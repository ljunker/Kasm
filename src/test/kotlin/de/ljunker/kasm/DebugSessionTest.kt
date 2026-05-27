package de.ljunker.kasm

import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
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

    @Test
    fun setsFileAwareBreakpointsInIncludedSources() {
        val baseDirectory = createTempDirectory("kasm-debug-session-include")
        baseDirectory.resolve("lib").createDirectories()
        val includePath = baseDirectory.resolve("lib/printer.kasm")
        val mainPath = baseDirectory.resolve("main.kasm")
        includePath.writeText(
            """
            included_print:
              PRINT R0
              RET
            """.trimIndent()
        )
        mainPath.writeText(
            """
            MOV R0, 5
            CALL included_print
            HALT

            .include "lib/printer.kasm"
            """.trimIndent()
        )
        val output = mutableListOf<String>()
        val session = DebugSession(
            debugProgram = Assembler().assembleFileWithDebugInfo(mainPath),
            output = { line -> output += line }
        )
        val normalizedIncludePath = includePath.toAbsolutePath().normalize()

        assertEquals(
            LineBreakpoint(
                lineNumber = 2,
                address = 7,
                sourcePath = normalizedIncludePath
            ),
            session.setBreakpoint(includePath, 2)
        )

        val firstHit = assertIs<DebugStop.BreakpointHit>(session.run())

        assertEquals(normalizedIncludePath, firstHit.breakpoint.sourcePath)
        assertEquals(2, firstHit.snapshot.nextLocation?.lineNumber)
        assertEquals(normalizedIncludePath, firstHit.snapshot.nextLocation?.sourcePath)
        assertEquals(emptyList(), output)
        assertEquals(true, session.removeBreakpoint(includePath, 2))
    }
}
