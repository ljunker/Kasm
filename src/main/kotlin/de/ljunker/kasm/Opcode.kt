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
    LOAD_INDEXED(0x16, "LOAD", OperandType.REGISTER, OperandType.INDEXED_MEMORY_ADDRESS),
    STORE_INDEXED(0x17, "STORE", OperandType.INDEXED_MEMORY_ADDRESS, OperandType.REGISTER),
    ADDI(0x18, "ADDI", OperandType.REGISTER, OperandType.BYTE_VALUE),
    SUBI(0x19, "SUBI", OperandType.REGISTER, OperandType.BYTE_VALUE),
    MUL(0x1A, "MUL", OperandType.REGISTER, OperandType.REGISTER),
    DIV(0x1B, "DIV", OperandType.REGISTER, OperandType.REGISTER),
    MOD(0x1C, "MOD", OperandType.REGISTER, OperandType.REGISTER),
    NEG(0x1D, "NEG", OperandType.REGISTER),
    AND(0x1E, "AND", OperandType.REGISTER, OperandType.REGISTER),
    OR(0x1F, "OR", OperandType.REGISTER, OperandType.REGISTER),
    XOR(0x20, "XOR", OperandType.REGISTER, OperandType.REGISTER),
    NOT(0x21, "NOT", OperandType.REGISTER),
    JGE(0x22, "JGE", OperandType.JUMP_TARGET),
    JLE(0x23, "JLE", OperandType.JUMP_TARGET),
    CLR(0x24, "CLR", OperandType.REGISTER),
    NOP(0x25, "NOP"),
    PRINTC(0x26, "PRINTC", OperandType.REGISTER),
    MOVA(0x27, "MOVA", OperandType.ADDRESS_REGISTER, OperandType.ADDRESS_VALUE),
    MOVA_REGISTER(0x28, "MOVA", OperandType.ADDRESS_REGISTER, OperandType.ADDRESS_REGISTER),
    INCA(0x29, "INCA", OperandType.ADDRESS_REGISTER),
    DECA(0x2A, "DECA", OperandType.ADDRESS_REGISTER),
    LOAD_ADDRESS_REGISTER(0x2B, "LOAD", OperandType.REGISTER, OperandType.ADDRESS_REGISTER),
    STORE_ADDRESS_REGISTER(0x2C, "STORE", OperandType.ADDRESS_REGISTER, OperandType.REGISTER),
    HALT(0xFF, "HALT");

    val operandTypes: List<OperandType> = operandTypes.toList()

    val operandCount: Int
        get() = operandTypes.size

    val operandByteCount: Int
        get() = operandTypes.sumOf(OperandType::byteCount)

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

enum class OperandType(
    val byteCount: Int
) {
    REGISTER(1),
    ADDRESS_REGISTER(1),
    BYTE_VALUE(1),
    ADDRESS_VALUE(2),
    JUMP_TARGET(2),
    MEMORY_ADDRESS(2),
    INDEXED_MEMORY_ADDRESS(3)
}
