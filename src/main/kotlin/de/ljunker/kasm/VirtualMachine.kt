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
        zeroFlag = false
        signFlag = false
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

                registers[target] += registers[source]
                updateResultFlags(registers[target])
            }

            Opcode.SUB -> {
                val target = readRegister(program)
                val source = readRegister(program)

                registers[target] -= registers[source]
                updateResultFlags(registers[target])
            }

            Opcode.INC -> {
                val register = readRegister(program)

                registers[register]++
                updateResultFlags(registers[register])
            }

            Opcode.DEC -> {
                val register = readRegister(program)

                registers[register]--
                updateResultFlags(registers[register])
            }

            Opcode.CMP -> {
                val left = readRegister(program)
                val right = readRegister(program)

                updateResultFlags(registers[left] - registers[right])
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

                if (!zeroFlag && !signFlag) {
                    jumpTo(program, address)
                }
            }

            Opcode.JL -> {
                val address = readByte(program)

                if (signFlag) {
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
        memory[address] = value
    }

    private fun ensureMemoryAddress(address: Int) {
        if (address !in memory.indices) {
            throw VmException("Memory address out of bounds: $address")
        }
    }

    private fun push(value: Int) {
        if (stackPointer == 0) {
            throw VmException("Stack overflow")
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

    private fun updateResultFlags(result: Int) {
        zeroFlag = result == 0
        signFlag = result < 0
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
        private const val REGISTER_COUNT = 4
        private const val MEMORY_SIZE = 256
    }
}

data class VmSnapshot(
    val instructionPointer: Int,
    val registers: List<Int>,
    val memory: List<Int>,
    val stackPointer: Int,
    val zeroFlag: Boolean,
    val signFlag: Boolean,
    val running: Boolean
)

class VmException(message: String) : RuntimeException(message)
