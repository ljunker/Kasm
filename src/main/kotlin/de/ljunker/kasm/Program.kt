package de.ljunker.kasm

import java.nio.file.Path

data class Program(
    val bytes: List<Int>,
    val initialMemory: Map<Int, Int> = emptyMap(),
    val fileResources: List<ProgramFile> = emptyList()
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
        require(fileResources.size <= Architecture.WORD_VALUE_COUNT) {
            "Program file resource count must not exceed ${Architecture.WORD_VALUE_COUNT}"
        }
        require(fileResources.map(ProgramFile::id).all { it in Architecture.wordRange }) {
            "Program file resource ids must be in range ${Architecture.wordRange}"
        }
        require(fileResources.map(ProgramFile::id).toSet().size == fileResources.size) {
            "Program file resource ids must be unique"
        }
        require(fileResources.map(ProgramFile::id) == fileResources.indices.toList()) {
            "Program file resource ids must match their list index"
        }
        require(fileResources.map(ProgramFile::name).toSet().size == fileResources.size) {
            "Program file resource names must be unique"
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

data class ProgramFile(
    val id: Int,
    val name: String,
    val path: Path
)
