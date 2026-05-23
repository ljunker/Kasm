package de.ljunker.kasm

class VirtualMachine(
    private val outputLine: (String) -> Unit,
    private val outputText: (String) -> Unit
) {
    constructor() : this(::println, ::print)

    constructor(output: (String) -> Unit) : this(output, output)

    private val registers = IntArray(REGISTER_COUNT)
    private val addressRegisters = IntArray(ADDRESS_REGISTER_COUNT)
    private val memory = IntArray(MEMORY_SIZE)

    private var program: Program? = null
    private var ip: Int = 0
    private var running: Boolean = false
    private var zeroFlag: Boolean = false
    private var signFlag: Boolean = false
    private var carryFlag: Boolean = false
    private var overflowFlag: Boolean = false
    private var stackPointer: Int = MEMORY_SIZE

    val instructionPointer: Int
        get() = ip

    val isRunning: Boolean
        get() = running

    fun load(program: Program) {
        this.program = program
        ip = 0
        running = true
        registers.fill(0)
        addressRegisters.fill(0)
        memory.fill(0)
        program.initialMemory.forEach { (address, value) ->
            memory[address] = value
        }
        zeroFlag = false
        signFlag = false
        carryFlag = false
        overflowFlag = false
        stackPointer = MEMORY_SIZE
    }

    fun run(program: Program) {
        load(program)

        while (running) {
            step()
        }
    }

    fun step(): Boolean {
        if (!running) {
            return false
        }

        val program = program
            ?: throw VmException("No program is loaded")

        val opcodeAddress = ip
        val opcodeCode = readByte(program)

        val opcode = Opcode.fromCode(opcodeCode)
            ?: throw VmException("Unknown opcode 0x${opcodeCode.toHex()} at address $opcodeAddress")

        when (opcode) {
            Opcode.MOV -> {
                val register = readRegister(program)
                val value = readByte(program)

                registers[register] = value
            }

            Opcode.MOV_REGISTER -> {
                val target = readRegister(program)
                val source = readRegister(program)

                registers[target] = registers[source]
            }

            Opcode.ADD -> {
                val target = readRegister(program)
                val source = readRegister(program)

                addToRegister(target, registers[source])
            }

            Opcode.SUB -> {
                val target = readRegister(program)
                val source = readRegister(program)

                subtractFromRegister(target, registers[source])
            }

            Opcode.INC -> {
                val register = readRegister(program)

                addToRegister(register, 1)
            }

            Opcode.DEC -> {
                val register = readRegister(program)

                subtractFromRegister(register, 1)
            }

            Opcode.CMP -> {
                val left = readRegister(program)
                val right = readRegister(program)

                updateSubtractionFlags(registers[left], registers[right])
            }

            Opcode.JMP -> {
                val address = readAddress(program)

                jumpTo(program, address)
            }

            Opcode.JZ -> {
                val register = readRegister(program)
                val address = readAddress(program)

                if (registers[register] == 0) {
                    jumpTo(program, address)
                }
            }

            Opcode.JNZ -> {
                val register = readRegister(program)
                val address = readAddress(program)

                if (registers[register] != 0) {
                    jumpTo(program, address)
                }
            }

            Opcode.JE -> {
                val address = readAddress(program)

                if (zeroFlag) {
                    jumpTo(program, address)
                }
            }

            Opcode.JNE -> {
                val address = readAddress(program)

                if (!zeroFlag) {
                    jumpTo(program, address)
                }
            }

            Opcode.JG -> {
                val address = readAddress(program)

                if (!zeroFlag && signFlag == overflowFlag) {
                    jumpTo(program, address)
                }
            }

            Opcode.JL -> {
                val address = readAddress(program)

                if (signFlag != overflowFlag) {
                    jumpTo(program, address)
                }
            }

            Opcode.LOAD -> {
                val register = readRegister(program)
                val address = readAddress(program)

                registers[register] = readMemory(address)
            }

            Opcode.STORE -> {
                val address = readAddress(program)
                val register = readRegister(program)

                writeMemory(address, registers[register])
            }

            Opcode.LOAD_INDEXED -> {
                val register = readRegister(program)
                val baseAddress = readAddress(program)
                val indexRegister = readRegister(program)

                registers[register] = readMemory(indexedAddress(baseAddress, indexRegister))
            }

            Opcode.STORE_INDEXED -> {
                val baseAddress = readAddress(program)
                val indexRegister = readRegister(program)
                val register = readRegister(program)

                writeMemory(indexedAddress(baseAddress, indexRegister), registers[register])
            }

            Opcode.LOAD_ADDRESS_REGISTER -> {
                val register = readRegister(program)
                val addressRegister = readAddressRegister(program)

                registers[register] = readMemory(addressRegisters[addressRegister])
            }

            Opcode.STORE_ADDRESS_REGISTER -> {
                val addressRegister = readAddressRegister(program)
                val register = readRegister(program)

                writeMemory(addressRegisters[addressRegister], registers[register])
            }

            Opcode.PUSH -> {
                val register = readRegister(program)

                push(registers[register])
            }

            Opcode.POP -> {
                val register = readRegister(program)

                registers[register] = pop()
            }

            Opcode.CALL -> {
                val address = readAddress(program)

                pushAddress(ip)
                jumpTo(program, address)
            }

            Opcode.RET -> {
                jumpTo(program, popAddress())
            }

            Opcode.PRINT -> {
                val register = readRegister(program)

                outputLine(registers[register].toString())
            }

            Opcode.PRINTC -> {
                val register = readRegister(program)
                val value = registers[register]

                if (value > ASCII_MAX) {
                    throw VmException("PRINTC value is not ASCII: $value")
                }

                outputText(value.toChar().toString())
            }

            Opcode.MOVA -> {
                val addressRegister = readAddressRegister(program)
                val value = readAddress(program)

                addressRegisters[addressRegister] = value
            }

            Opcode.MOVA_REGISTER -> {
                val target = readAddressRegister(program)
                val source = readAddressRegister(program)

                addressRegisters[target] = addressRegisters[source]
            }

            Opcode.INCA -> {
                val addressRegister = readAddressRegister(program)

                addressRegisters[addressRegister] = Architecture.normalizeAddress(
                    addressRegisters[addressRegister] + 1
                )
            }

            Opcode.DECA -> {
                val addressRegister = readAddressRegister(program)

                addressRegisters[addressRegister] = Architecture.normalizeAddress(
                    addressRegisters[addressRegister] - 1
                )
            }

            Opcode.ADDI -> {
                val register = readRegister(program)
                val value = readByte(program)

                addToRegister(register, value)
            }

            Opcode.SUBI -> {
                val register = readRegister(program)
                val value = readByte(program)

                subtractFromRegister(register, value)
            }

            Opcode.MUL -> {
                val target = readRegister(program)
                val source = readRegister(program)

                val unsignedResult = registers[target] * registers[source]
                val signedResult = Architecture.toSignedWord(registers[target]) *
                        Architecture.toSignedWord(registers[source])

                registers[target] = Architecture.normalizeWord(unsignedResult)
                updateResultFlags(registers[target])
                carryFlag = unsignedResult > Architecture.WORD_MASK
                overflowFlag = signedResult !in SIGNED_WORD_RANGE
            }

            Opcode.DIV -> {
                val target = readRegister(program)
                val source = readRegister(program)

                if (registers[source] == 0) {
                    throw VmException("Division by zero")
                }

                val result = registers[target] / registers[source]
                registers[target] = Architecture.normalizeWord(result)
                updateResultFlags(registers[target])
                clearCarryOverflowFlags()
            }

            Opcode.MOD -> {
                val target = readRegister(program)
                val source = readRegister(program)

                if (registers[source] == 0) {
                    throw VmException("Modulo by zero")
                }

                val result = registers[target] % registers[source]
                registers[target] = Architecture.normalizeWord(result)
                updateResultFlags(registers[target])
                clearCarryOverflowFlags()
            }

            Opcode.NEG -> {
                val register = readRegister(program)
                val original = registers[register]

                val result = -original
                registers[register] = Architecture.normalizeWord(result)
                updateResultFlags(registers[register])
                carryFlag = original != 0
                overflowFlag = original == Architecture.WORD_SIGN_BIT
            }

            Opcode.AND -> {
                val target = readRegister(program)
                val source = readRegister(program)

                val result = registers[target] and registers[source]
                registers[target] = Architecture.normalizeWord(result)
                updateResultFlags(registers[target])
                clearCarryOverflowFlags()
            }

            Opcode.OR -> {
                val target = readRegister(program)
                val source = readRegister(program)

                val result = registers[target] or registers[source]
                registers[target] = Architecture.normalizeWord(result)
                updateResultFlags(registers[target])
                clearCarryOverflowFlags()
            }

            Opcode.XOR -> {
                val target = readRegister(program)
                val source = readRegister(program)

                val result = registers[target] xor registers[source]
                registers[target] = Architecture.normalizeWord(result)
                updateResultFlags(registers[target])
                clearCarryOverflowFlags()
            }

            Opcode.NOT -> {
                val register = readRegister(program)

                val result = registers[register].inv() and Architecture.WORD_MASK
                registers[register] = Architecture.normalizeWord(result)
                updateResultFlags(registers[register])
                clearCarryOverflowFlags()
            }

            Opcode.JGE -> {
                val address = readAddress(program)

                if (zeroFlag || signFlag == overflowFlag) {
                    jumpTo(program, address)
                }
            }

            Opcode.JLE -> {
                val address = readAddress(program)

                if (zeroFlag || signFlag != overflowFlag) {
                    jumpTo(program, address)
                }
            }

            Opcode.CLR -> {
                val register = readRegister(program)

                registers[register] = 0
                updateResultFlags(registers[register])
                clearCarryOverflowFlags()
            }

            Opcode.NOP -> {
                // No operation
            }

            Opcode.HALT -> {
                running = false
            }
        }

        return running
    }

    fun snapshot(): VmSnapshot =
        VmSnapshot(
            instructionPointer = ip,
            registers = registers.toList(),
            addressRegisters = addressRegisters.toList(),
            memory = memory.toList(),
            stackPointer = stackPointer,
            zeroFlag = zeroFlag,
            signFlag = signFlag,
            carryFlag = carryFlag,
            overflowFlag = overflowFlag,
            running = running
        )

    private fun readByte(program: Program): Int {
        if (ip !in 0 until program.size) {
            throw VmException("Instruction pointer out of bounds: $ip")
        }

        return program[ip++]
    }

    private fun readRegister(program: Program): Int {
        val register = readByte(program)

        if (register !in 0 until REGISTER_COUNT) {
            throw VmException("Invalid register R$register")
        }

        return register
    }

    private fun readAddressRegister(program: Program): Int {
        val register = readByte(program)

        if (register !in 0 until ADDRESS_REGISTER_COUNT) {
            throw VmException("Invalid address register A$register")
        }

        return register
    }

    private fun readAddress(program: Program): Int {
        val low = readByte(program)
        val high = readByte(program)

        return low or (high shl Architecture.WORD_BITS)
    }

    private fun readMemory(address: Int): Int {
        ensureMemoryAddress(address)

        return memory[address]
    }

    private fun writeMemory(address: Int, value: Int) {
        ensureMemoryAddress(address)
        memory[address] = Architecture.normalizeWord(value)
    }

    private fun ensureMemoryAddress(address: Int) {
        if (address !in memory.indices) {
            throw VmException("Memory address out of bounds: $address")
        }
    }

    private fun indexedAddress(baseAddress: Int, indexRegister: Int): Int =
        Architecture.normalizeAddress(baseAddress + registers[indexRegister])

    private fun push(value: Int) {
        if (stackPointer == 0) {
            throw VmException("Stack overflow")
        }
        if (value !in Architecture.wordRange) {
            throw VmException("Stack value out of range: $value")
        }

        stackPointer--
        memory[stackPointer] = value
    }

    private fun pushAddress(address: Int) {
        if (stackPointer < 2) {
            throw VmException("Stack overflow")
        }
        if (address !in Architecture.addressRange) {
            throw VmException("Stack address out of range: $address")
        }

        push((address ushr Architecture.WORD_BITS) and Architecture.WORD_MASK)
        push(address and Architecture.WORD_MASK)
    }

    private fun pop(): Int {
        if (stackPointer == MEMORY_SIZE) {
            throw VmException("Stack underflow")
        }

        return memory[stackPointer++]
    }

    private fun popAddress(): Int {
        if (stackPointer > MEMORY_SIZE - 2) {
            throw VmException("Stack underflow")
        }

        val low = pop()
        val high = pop()

        return low or (high shl Architecture.WORD_BITS)
    }

    private fun addToRegister(register: Int, value: Int) {
        val left = registers[register]
        val rawResult = left + value
        val result = Architecture.normalizeWord(rawResult)

        registers[register] = result
        updateResultFlags(result)
        carryFlag = rawResult > Architecture.WORD_MASK
        overflowFlag = Architecture.hasSignBit(left) == Architecture.hasSignBit(value) &&
                Architecture.hasSignBit(left) != Architecture.hasSignBit(result)
    }

    private fun subtractFromRegister(register: Int, value: Int) {
        val left = registers[register]

        registers[register] = updateSubtractionFlags(left, value)
    }

    private fun updateSubtractionFlags(left: Int, right: Int): Int {
        val rawResult = left - right
        val result = Architecture.normalizeWord(rawResult)

        updateResultFlags(result)
        carryFlag = rawResult < 0
        overflowFlag = Architecture.hasSignBit(left) != Architecture.hasSignBit(right) &&
                Architecture.hasSignBit(left) != Architecture.hasSignBit(result)

        return result
    }

    private fun updateResultFlags(result: Int) {
        zeroFlag = result == 0
        signFlag = Architecture.hasSignBit(result)
    }

    private fun clearCarryOverflowFlags() {
        carryFlag = false
        overflowFlag = false
    }

    private fun jumpTo(program: Program, address: Int) {
        if (address !in 0 until program.size) {
            throw VmException("Jump target out of bounds: $address")
        }

        ip = address
    }

    private fun Int.toHex(): String =
        "%02X".format(this)

    companion object {
        private const val REGISTER_COUNT = Architecture.REGISTER_COUNT
        private const val ADDRESS_REGISTER_COUNT = Architecture.ADDRESS_REGISTER_COUNT
        private const val MEMORY_SIZE = Architecture.MEMORY_SIZE
        private const val ASCII_MAX = 0x7F
        private val SIGNED_WORD_RANGE = -Architecture.WORD_SIGN_BIT until Architecture.WORD_SIGN_BIT
    }
}

data class VmSnapshot(
    val instructionPointer: Int,
    val registers: List<Int>,
    val addressRegisters: List<Int>,
    val memory: List<Int>,
    val stackPointer: Int,
    val zeroFlag: Boolean,
    val signFlag: Boolean,
    val carryFlag: Boolean,
    val overflowFlag: Boolean,
    val running: Boolean
)

class VmException(message: String) : RuntimeException(message)
