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
}
