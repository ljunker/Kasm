package de.ljunker.kasm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProgramTest {

    @Test
    fun rejectsProgramsThatDoNotFitTheByteAddressSpace() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Program(List(Architecture.ADDRESS_SPACE_SIZE + 1) { Opcode.HALT.code })
        }

        assertEquals("Program size must not exceed 256 bytes", exception.message)
    }

    @Test
    fun rejectsInitialMemoryOutsideDataAddressSpace() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Program(
                bytes = listOf(Opcode.HALT.code),
                initialMemory = mapOf(Architecture.MEMORY_SIZE to 1)
            )
        }

        assertEquals(
            "Initial memory addresses must be in range 0 until 256",
            exception.message
        )
    }
}
