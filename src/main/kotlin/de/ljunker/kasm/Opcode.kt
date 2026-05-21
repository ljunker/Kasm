package de.ljunker.kasm

enum class Opcode(
    val code: Int,
    val mnemonic: String,
    val operandCount: Int
) {
    MOV(0x01, "MOV", 2),    // MOV R0, 10
    ADD(0x02, "ADD", 2),    // ADD R0, R1
    SUB(0x03, "SUB", 2),    // SUB R0, R1
    JMP(0x04, "JMP", 1),    // JMP label
    JZ(0x05, "JZ", 2),      // JZ R0, label
    PRINT(0x06, "PRINT", 1),// PRINT R0
    HALT(0xFF, "HALT", 0);  // HALT

    companion object {
        private val byMnemonic = entries.associateBy { it.mnemonic }
        private val byCode = entries.associateBy { it.code }

        fun fromMnemonic(value: String): Opcode? =
            byMnemonic[value.uppercase()]

        fun fromCode(value: Int): Opcode? =
            byCode[value]
    }
}