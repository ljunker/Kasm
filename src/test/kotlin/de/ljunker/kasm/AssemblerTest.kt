package de.ljunker.kasm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AssemblerTest {

    @Test
    fun assemblesImmediateAndRegisterMoves() {
        val program = Assembler().assemble(
            """
            MOV R0, 10
            MOV R1, R0
            INC R1
            DEC R0
            """.trimIndent()
        )

        assertEquals(
            Program.of(
                0x01, 0, 10,
                0x07, 1, 0,
                0x09, 1,
                0x0A, 0
            ),
            program
        )
    }

    @Test
    fun resolvesNonZeroJumpLabels() {
        val program = Assembler().assemble(
            """
            start:
            JNZ R0, start
            HALT
            """.trimIndent()
        )

        assertEquals(
            Program.of(
                0x08, 0, 0,
                0xFF
            ),
            program
        )
    }

    @Test
    fun assemblesCompareMemoryAndStackInstructions() {
        val program = Assembler().assemble(
            """
            CMP R0, R1
            JE done
            LOAD R2, [20]
            STORE [21], R2
            PUSH R2
            POP R3
            CALL done
            done:
            RET
            """.trimIndent()
        )

        assertEquals(
            Program.of(
                0x0B, 0, 1,
                0x0C, 17,
                0x10, 2, 20,
                0x11, 21, 2,
                0x12, 2,
                0x13, 3,
                0x14, 17,
                0x15
            ),
            program
        )
    }

    @Test
    fun requiresMemoryAddressBrackets() {
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble("LOAD R0, 10")
        }

        assertEquals("Line 1: memory address '10' must use [value]", exception.message)
    }

    @Test
    fun mapsExecutableLinesToBytecodeAddresses() {
        val debugProgram = Assembler().assembleWithDebugInfo(
            """
            MOV R0, 2
            loop:
            PRINT R0
            HALT
            """.trimIndent()
        )

        assertEquals(0, debugProgram.sourceMap.addressForLine(1))
        assertEquals(null, debugProgram.sourceMap.addressForLine(2))
        assertEquals(3, debugProgram.sourceMap.addressForLine(3))
        assertEquals("PRINT R0", debugProgram.sourceMap.locationForAddress(3)?.source?.trim())
    }

    @Test
    fun rejectsOutOfRangeRegisterMoveSources() {
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble("MOV R0, R4")
        }

        assertEquals("Line 1: register 'R4' is out of range", exception.message)
    }
}
