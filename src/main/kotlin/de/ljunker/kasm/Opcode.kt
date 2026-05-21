package de.ljunker.kasm

enum class Opcode(
    val code: Int,
    val mnemonic: String,
    vararg operandTypes: OperandType
) {
    MOV(0x01, "MOV", OperandType.REGISTER, OperandType.BYTE_VALUE),
    ADD(0x02, "ADD", OperandType.REGISTER, OperandType.REGISTER),
    SUB(0x03, "SUB", OperandType.REGISTER, OperandType.REGISTER),
    JMP(0x04, "JMP", OperandType.JUMP_TARGET),
    JZ(0x05, "JZ", OperandType.REGISTER, OperandType.JUMP_TARGET),
    PRINT(0x06, "PRINT", OperandType.REGISTER),
    MOV_REGISTER(0x07, "MOV", OperandType.REGISTER, OperandType.REGISTER),
    JNZ(0x08, "JNZ", OperandType.REGISTER, OperandType.JUMP_TARGET),
    INC(0x09, "INC", OperandType.REGISTER),
    DEC(0x0A, "DEC", OperandType.REGISTER),
    CMP(0x0B, "CMP", OperandType.REGISTER, OperandType.REGISTER),
    JE(0x0C, "JE", OperandType.JUMP_TARGET),
    JNE(0x0D, "JNE", OperandType.JUMP_TARGET),
    JG(0x0E, "JG", OperandType.JUMP_TARGET),
    JL(0x0F, "JL", OperandType.JUMP_TARGET),
    LOAD(0x10, "LOAD", OperandType.REGISTER, OperandType.MEMORY_ADDRESS),
    STORE(0x11, "STORE", OperandType.MEMORY_ADDRESS, OperandType.REGISTER),
    PUSH(0x12, "PUSH", OperandType.REGISTER),
    POP(0x13, "POP", OperandType.REGISTER),
    CALL(0x14, "CALL", OperandType.JUMP_TARGET),
    RET(0x15, "RET"),
    HALT(0xFF, "HALT");

    val operandTypes: List<OperandType> = operandTypes.toList()

    val operandCount: Int
        get() = operandTypes.size

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

enum class OperandType {
    REGISTER,
    BYTE_VALUE,
    JUMP_TARGET,
    MEMORY_ADDRESS
}
