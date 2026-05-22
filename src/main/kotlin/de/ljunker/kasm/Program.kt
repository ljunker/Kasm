package de.ljunker.kasm

data class Program(
    val bytes: List<Int>,
    val initialMemory: Map<Int, Int> = emptyMap()
) {
    init {
        require(bytes.all { it in Architecture.wordRange }) {
            "Program bytes must be in range ${Architecture.wordRange}"
        }
        require(bytes.size <= Architecture.ADDRESS_SPACE_SIZE) {
            "Program size must not exceed ${Architecture.ADDRESS_SPACE_SIZE} bytes"
        }
        require(initialMemory.keys.all { it in 0 until Architecture.MEMORY_SIZE }) {
            "Initial memory addresses must be in range 0 until ${Architecture.MEMORY_SIZE}"
        }
        require(initialMemory.values.all { it in Architecture.wordRange }) {
            "Initial memory values must be in range ${Architecture.wordRange}"
        }
    }

    val size: Int
        get() = bytes.size

    operator fun get(address: Int): Int =
        bytes[address]

    fun toByteArray(): ByteArray =
        bytes.map { it.toByte() }.toByteArray()

    fun hexDump(): String =
        bytes.joinToString(" ") { "%02X".format(it) }

    companion object {
        fun of(vararg bytes: Int): Program =
            Program(bytes.toList())
    }
}
