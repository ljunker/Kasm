package de.ljunker.kasm

class VirtualMachine(
    private val output: (String) -> Unit = ::println
) {
    private val registers = IntArray(REGISTER_COUNT)

    private var ip: Int = 0
    private var running: Boolean = false

    fun run(program: Program) {
        ip = 0
        running = true
        registers.fill(0)

        while (running) {
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

                Opcode.ADD -> {
                    val target = readRegister(program)
                    val source = readRegister(program)

                    registers[target] += registers[source]
                }

                Opcode.SUB -> {
                    val target = readRegister(program)
                    val source = readRegister(program)

                    registers[target] -= registers[source]
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

                Opcode.PRINT -> {
                    val register = readRegister(program)

                    output(registers[register].toString())
                }

                Opcode.HALT -> {
                    running = false
                }
            }
        }
    }

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
    }
}

class VmException(message: String) : RuntimeException(message)