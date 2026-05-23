package de.ljunker.kasm

class VirtualMachine(
    private val output: (String) -> Unit = ::println
) {
    private val registers = IntArray(REGISTER_COUNT)
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
                val address = readByte(program)

                jumpTo(program, address)
            }

            Opcode.JZ -> {
                val register = readRegister(program)
                val address = readByte(program)

                if (registers[register] == 0) {
                    jumpTo(program, address)
                }
            }

            Opcode.JNZ -> {
                val register = readRegister(program)
                val address = readByte(program)

                if (registers[register] != 0) {
                    jumpTo(program, address)
                }
            }

            Opcode.JE -> {
                val address = readByte(program)

                if (zeroFlag) {
                    jumpTo(program, address)
                }
            }

            Opcode.JNE -> {
                val address = readByte(program)

                if (!zeroFlag) {
                    jumpTo(program, address)
                }
            }

            Opcode.JG -> {
                val address = readByte(program)

                if (!zeroFlag && signFlag == overflowFlag) {
                    jumpTo(program, address)
                }
            }

            Opcode.JL -> {
                val address = readByte(program)

                if (signFlag != overflowFlag) {
                    jumpTo(program, address)
                }
            }

            Opcode.LOAD -> {
                val register = readRegister(program)
                val address = readByte(program)

                registers[register] = readMemory(address)
            }

            Opcode.STORE -> {
                val address = readByte(program)
                val register = readRegister(program)

                writeMemory(address, registers[register])
            }

            Opcode.LOAD_INDEXED -> {
                val register = readRegister(program)
                val baseAddress = readByte(program)
                val indexRegister = readRegister(program)

                registers[register] = readMemory(indexedAddress(baseAddress, indexRegister))
            }

            Opcode.STORE_INDEXED -> {
                val baseAddress = readByte(program)
                val indexRegister = readRegister(program)
                val register = readRegister(program)

                writeMemory(indexedAddress(baseAddress, indexRegister), registers[register])
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
                val address = readByte(program)

                push(ip)
                jumpTo(program, address)
            }

            Opcode.RET -> {
                jumpTo(program, pop())
            }

            Opcode.PRINT -> {
                val register = readRegister(program)

                output(registers[register].toString())
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

                val result = registers[target] * registers[source]
                registers[target] = Architecture.normalizeWord(result)
                updateResultFlags(registers[target])
                carryFlag = result > Architecture.WORD_MASK
                overflowFlag = Architecture.hasSignBit(registers[target]) != Architecture.hasSignBit(result)
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
            }

            Opcode.NEG -> {
                val register = readRegister(program)

                val result = -registers[register]
                registers[register] = Architecture.normalizeWord(result)
                updateResultFlags(registers[register])
                carryFlag = result < 0
                overflowFlag = result != 0 && result != -registers[register]
            }

            Opcode.AND -> {
                val target = readRegister(program)
                val source = readRegister(program)

                val result = registers[target] and registers[source]
                registers[target] = Architecture.normalizeWord(result)
                updateResultFlags(registers[target])
            }

            Opcode.OR -> {
                val target = readRegister(program)
                val source = readRegister(program)

                val result = registers[target] or registers[source]
                registers[target] = Architecture.normalizeWord(result)
                updateResultFlags(registers[target])
            }

            Opcode.XOR -> {
                val target = readRegister(program)
                val source = readRegister(program)

                val result = registers[target] xor registers[source]
                registers[target] = Architecture.normalizeWord(result)
                updateResultFlags(registers[target])
            }

            Opcode.NOT -> {
                val register = readRegister(program)

                val result = registers[register].inv() and Architecture.WORD_MASK
                registers[register] = Architecture.normalizeWord(result)
                updateResultFlags(registers[register])
            }

            Opcode.JGE -> {
                val address = readByte(program)

                if (zeroFlag || signFlag == overflowFlag) {
                    jumpTo(program, address)
                }
            }

            Opcode.JLE -> {
                val address = readByte(program)

                if (zeroFlag || signFlag != overflowFlag) {
                    jumpTo(program, address)
                }
            }

            Opcode.CLR -> {
                val register = readRegister(program)

                registers[register] = 0
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
        Architecture.normalizeWord(baseAddress + registers[indexRegister])

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

    private fun pop(): Int {
        if (stackPointer == MEMORY_SIZE) {
            throw VmException("Stack underflow")
        }

        return memory[stackPointer++]
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
        private const val MEMORY_SIZE = Architecture.MEMORY_SIZE
    }
}

data class VmSnapshot(
    val instructionPointer: Int,
    val registers: List<Int>,
    val memory: List<Int>,
    val stackPointer: Int,
    val zeroFlag: Boolean,
    val signFlag: Boolean,
    val carryFlag: Boolean,
    val overflowFlag: Boolean,
    val running: Boolean
)

class VmException(message: String) : RuntimeException(message)
