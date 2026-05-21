package de.ljunker.kasm

import kotlin.test.Test
import kotlin.test.assertEquals

class VirtualMachineTest {

    @Test
    fun runsRegisterMovesAndCounterInstructions() {
        val output = mutableListOf<String>()
        val program = Assembler().assemble(
            """
            MOV R0, 2
            MOV R1, R0

            loop:
            PRINT R1
            DEC R1
            JNZ R1, loop

            INC R0
            PRINT R0
            HALT
            """.trimIndent()
        )

        VirtualMachine { line -> output += line }.run(program)

        assertEquals(listOf("2", "1", "3"), output)
    }

    @Test
    fun comparesRegistersWithFlagJumps() {
        val output = mutableListOf<String>()
        val program = Assembler().assemble(
            """
            MOV R0, 7
            MOV R1, 4

            CMP R0, R1
            JG greater
            JMP less_check
            greater:
            MOV R2, 1
            PRINT R2

            less_check:
            CMP R1, R0
            JL less
            JMP equal_check
            less:
            MOV R2, 2
            PRINT R2

            equal_check:
            CMP R0, R0
            JE equal
            JMP different_check
            equal:
            MOV R2, 3
            PRINT R2

            different_check:
            CMP R0, R1
            JNE different
            JMP end
            different:
            MOV R2, 4
            PRINT R2

            end:
            HALT
            """.trimIndent()
        )

        VirtualMachine { line -> output += line }.run(program)

        assertEquals(listOf("1", "2", "3", "4"), output)
    }

    @Test
    fun loadsStoresAndCallsThroughTheStack() {
        val output = mutableListOf<String>()
        val program = Assembler().assemble(
            """
            MOV R0, 5
            STORE [20], R0
            MOV R0, 0
            LOAD R1, [20]
            PUSH R1
            CALL double
            POP R2
            PRINT R0
            PRINT R2
            HALT

            double:
            MOV R3, R1
            ADD R1, R3
            MOV R0, R1
            RET
            """.trimIndent()
        )

        VirtualMachine { line -> output += line }.run(program)

        assertEquals(listOf("10", "5"), output)
    }
}
