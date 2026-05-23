package de.ljunker.kasm

object Architecture {
    const val REGISTER_COUNT = 4

    const val WORD_BITS = 8
    const val WORD_MASK = 0xFF
    const val WORD_SIGN_BIT = 0x80

    const val ADDRESS_SPACE_SIZE = 256
    const val MEMORY_SIZE = ADDRESS_SPACE_SIZE

    val wordRange: IntRange = 0..WORD_MASK

    fun normalizeWord(value: Int): Int =
        value and WORD_MASK

    fun hasSignBit(value: Int): Boolean =
        normalizeWord(value) and WORD_SIGN_BIT != 0

    fun toSignedWord(value: Int): Int {
        val normalized = normalizeWord(value)

        return if (hasSignBit(normalized)) {
            normalized - ADDRESS_SPACE_SIZE
        } else {
            normalized
        }
    }
}
