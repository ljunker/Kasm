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
    MOV_REGISTER(0x07, "MOV", 2), // MOV R0, R1
    JNZ(0x08, "JNZ", 2),    // JNZ R0, label
    INC(0x09, "INC", 1),    // INC R0
    DEC(0x0A, "DEC", 1),    // DEC R0
    CMP(0x0B, "CMP", 2),    // CMP R0, R1
    JE(0x0C, "JE", 1),      // JE label
    JNE(0x0D, "JNE", 1),    // JNE label
    JG(0x0E, "JG", 1),      // JG label
    JL(0x0F, "JL", 1),      // JL label
    LOAD(0x10, "LOAD", 2),  // LOAD R0, [10]
    STORE(0x11, "STORE", 2),// STORE [10], R0
    PUSH(0x12, "PUSH", 1),  // PUSH R0
    POP(0x13, "POP", 1),    // POP R0
    CALL(0x14, "CALL", 1),  // CALL label
    RET(0x15, "RET", 0),    // RET
    HALT(0xFF, "HALT", 0);  // HALT

    companion object {
        private val byMnemonic = entries
            .groupBy { it.mnemonic }
            .mapValues { (_, opcodes) -> opcodes.first() }
        private val byCode = entries.associateBy { it.code }

        fun fromMnemonic(value: String): Opcode? =
            byMnemonic[value.uppercase()]

        fun fromCode(value: Int): Opcode? =
            byCode[value]
    }
}
