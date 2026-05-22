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
    fun assemblesIndexedMemoryAddresses() {
        val program = Assembler().assemble(
            """
            MOV R1, 1
            LOAD R0, [40 + R1]
            STORE [R1 + 60], R0
            LOAD R2, [R3]
            HALT
            """.trimIndent()
        )

        assertEquals(
            Program.of(
                Opcode.MOV.code, 1, 1,
                Opcode.LOAD_INDEXED.code, 0, 40, 1,
                Opcode.STORE_INDEXED.code, 60, 1, 0,
                Opcode.LOAD_INDEXED.code, 2, 0, 3,
                Opcode.HALT.code
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
    fun rejectsOutOfRangeMemoryAddresses() {
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble("STORE [300], R0")
        }

        assertEquals(
            "Line 1: value '300' resolves to 300, but only 0..255 is allowed",
            exception.message
        )
    }

    @Test
    fun rejectsMissingOperands() {
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble("ADD R0")
        }

        assertEquals("Line 1: ADD expects 2 argument(s), got 1", exception.message)
    }

    @Test
    fun rejectsExtraOperands() {
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble("HALT R0")
        }

        assertEquals("Line 1: HALT expects 0 argument(s), got 1", exception.message)
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

    @Test
    fun initializesNamedDataWithConstantsAndExpressions() {
        val program = Assembler().assemble(
            """
            .equ BASE, 40

            .org BASE
            first:
              .byte 4, 9
            first_end:
            title:
              .ascii "OK"
            marker:
              .string "!"

              LOAD R0, [first + 1]
              MOV R1, first_end - first
              HALT
            """.trimIndent()
        )

        assertEquals(
            Program.of(
                Opcode.LOAD.code, 0, 41,
                Opcode.MOV.code, 1, 2,
                Opcode.HALT.code
            ).bytes,
            program.bytes
        )
        assertEquals(
            mapOf(
                40 to 4,
                41 to 9,
                42 to 'O'.code,
                43 to 'K'.code,
                44 to '!'.code,
                45 to 0
            ),
            program.initialMemory
        )
    }

    @Test
    fun rejectsOverlappingDataInitializers() {
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble(
                """
                .org 12
                  .byte 1, 2
                .org 13
                  .byte 3
                HALT
                """.trimIndent()
            )
        }

        assertEquals(
            "Line 4: data memory address 13 is initialized more than once",
            exception.message
        )
    }

    @Test
    fun rejectsMemoryAddressesWithTwoIndexRegisters() {
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble("LOAD R0, [R1 + R2]")
        }

        assertEquals(
            "Line 1: indexed memory address '[R1 + R2]' must use one register " +
                    "as [register] or [base + register]",
            exception.message
        )
    }
}
