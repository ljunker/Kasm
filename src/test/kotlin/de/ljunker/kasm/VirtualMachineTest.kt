package de.ljunker.kasm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @Test
    fun wrapsWordArithmeticAndSetsCarryFlags() {
        val output = mutableListOf<String>()
        val vm = VirtualMachine { line -> output += line }
        val program = Assembler().assemble(
            """
            MOV R0, 255
            INC R0
            STORE [20], R0
            PRINT R0
            HALT
            """.trimIndent()
        )

        vm.run(program)

        val snapshot = vm.snapshot()

        assertEquals(listOf("0"), output)
        assertEquals(0, snapshot.registers[0])
        assertEquals(0, snapshot.memory[20])
        assertEquals(true, snapshot.zeroFlag)
        assertEquals(false, snapshot.signFlag)
        assertEquals(true, snapshot.carryFlag)
        assertEquals(false, snapshot.overflowFlag)
    }

    @Test
    fun marksSignedOverflowOnWordArithmetic() {
        val vm = VirtualMachine()
        val program = Assembler().assemble(
            """
            MOV R0, 127
            MOV R1, 1
            ADD R0, R1
            HALT
            """.trimIndent()
        )

        vm.run(program)

        val snapshot = vm.snapshot()

        assertEquals(128, snapshot.registers[0])
        assertEquals(false, snapshot.zeroFlag)
        assertEquals(true, snapshot.signFlag)
        assertEquals(false, snapshot.carryFlag)
        assertEquals(true, snapshot.overflowFlag)
    }

    @Test
    fun comparesWordValuesAsSignedForGreaterAndLessJumps() {
        val output = mutableListOf<String>()
        val program = Assembler().assemble(
            """
            MOV R0, 127
            MOV R1, 255

            CMP R0, R1
            JG greater
            JMP end

            greater:
            PRINT R0

            end:
            HALT
            """.trimIndent()
        )

        VirtualMachine { line -> output += line }.run(program)

        assertEquals(listOf("127"), output)
    }

    @Test
    fun rejectsStackUnderflow() {
        val exception = assertFailsWith<VmException> {
            VirtualMachine().run(
                Program.of(
                    Opcode.POP.code, 0,
                    Opcode.HALT.code
                )
            )
        }

        assertEquals("Stack underflow", exception.message)
    }

    @Test
    fun rejectsStackOverflow() {
        val exception = assertFailsWith<VmException> {
            VirtualMachine().run(
                Program.of(
                    Opcode.PUSH.code, 0,
                    Opcode.JMP.code, 0
                )
            )
        }

        assertEquals("Stack overflow", exception.message)
    }

    @Test
    fun rejectsOutOfBoundsJumpTargets() {
        val exception = assertFailsWith<VmException> {
            VirtualMachine().run(
                Program.of(
                    Opcode.JMP.code, 42,
                    Opcode.HALT.code
                )
            )
        }

        assertEquals("Jump target out of bounds: 42", exception.message)
    }

    @Test
    fun rejectsOutOfBoundsCallTargets() {
        val exception = assertFailsWith<VmException> {
            VirtualMachine().run(
                Program.of(
                    Opcode.CALL.code, 42,
                    Opcode.HALT.code
                )
            )
        }

        assertEquals("Jump target out of bounds: 42", exception.message)
    }
}
