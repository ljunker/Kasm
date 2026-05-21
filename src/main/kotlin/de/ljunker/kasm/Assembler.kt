package de.ljunker.kasm

class Assembler {

    fun assemble(source: String): Program {
        val statements = parseStatements(source)

        val labels = collectLabels(statements)
        val bytes = encode(statements, labels)

        return Program(bytes)
    }

    private fun parseStatements(source: String): List<Statement> {
        val statements = mutableListOf<Statement>()

        source.lines().forEachIndexed { index, rawLine ->
            val lineNumber = index + 1

            var line = rawLine
                .substringBefore(";")
                .trim()

            if (line.isBlank()) {
                return@forEachIndexed
            }

            while (line.isNotBlank()) {
                val labelMatch = LABEL_REGEX.matchEntire(line)

                if (labelMatch != null) {
                    val labelName = labelMatch.groupValues[1]
                    val rest = labelMatch.groupValues[2].trim()

                    statements += Statement.Label(
                        name = labelName,
                        lineNumber = lineNumber
                    )

                    line = rest
                    continue
                }

                val parts = line.split(Regex("\\s+"), limit = 2)
                val mnemonic = parts[0].uppercase()

                val arguments = parts
                    .getOrNull(1)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()

                statements += Statement.Instruction(
                    mnemonic = mnemonic,
                    arguments = arguments,
                    lineNumber = lineNumber,
                    original = rawLine
                )

                line = ""
            }
        }

        return statements
    }

    private fun collectLabels(statements: List<Statement>): Map<String, Int> {
        val labels = mutableMapOf<String, Int>()
        var address = 0

        for (statement in statements) {
            when (statement) {
                is Statement.Label -> {
                    if (statement.name in labels) {
                        throw AssemblyException(
                            "Line ${statement.lineNumber}: duplicate label '${statement.name}'"
                        )
                    }

                    labels[statement.name] = address
                }

                is Statement.Instruction -> {
                    val opcode = Opcode.fromMnemonic(statement.mnemonic)
                        ?: throw AssemblyException(
                            "Line ${statement.lineNumber}: unknown instruction '${statement.mnemonic}'"
                        )

                    ensureArgumentCount(statement, opcode)

                    address += 1 + opcode.operandCount
                }
            }
        }

        return labels
    }

    private fun ensureArgumentCount(
        statement: Statement.Instruction,
        opcode: Opcode
    ) {
        if (statement.arguments.size != opcode.operandCount) {
            throw AssemblyException(
                "Line ${statement.lineNumber}: ${opcode.mnemonic} expects " +
                        "${opcode.operandCount} argument(s), got ${statement.arguments.size}"
            )
        }
    }

    private fun encode(
        statements: List<Statement>,
        labels: Map<String, Int>
    ): List<Int> {
        val bytes = mutableListOf<Int>()

        for (statement in statements) {
            if (statement !is Statement.Instruction) {
                continue
            }

            val opcode = Opcode.fromMnemonic(statement.mnemonic)
                ?: throw AssemblyException(
                    "Line ${statement.lineNumber}: unknown instruction '${statement.mnemonic}'"
                )

            ensureArgumentCount(statement, opcode)

            bytes += opcode.code

            when (opcode) {
                Opcode.MOV -> {
                    bytes += parseRegister(statement.arguments[0], statement.lineNumber)
                    bytes += parseValueOrLabel(statement.arguments[1], labels, statement.lineNumber)
                }

                Opcode.ADD,
                Opcode.SUB -> {
                    bytes += parseRegister(statement.arguments[0], statement.lineNumber)
                    bytes += parseRegister(statement.arguments[1], statement.lineNumber)
                }

                Opcode.JMP -> {
                    bytes += parseValueOrLabel(statement.arguments[0], labels, statement.lineNumber)
                }

                Opcode.JZ -> {
                    bytes += parseRegister(statement.arguments[0], statement.lineNumber)
                    bytes += parseValueOrLabel(statement.arguments[1], labels, statement.lineNumber)
                }

                Opcode.PRINT -> {
                    bytes += parseRegister(statement.arguments[0], statement.lineNumber)
                }

                Opcode.HALT -> {
                    // no operands
                }
            }
        }

        return bytes
    }

    private fun parseRegister(value: String, lineNumber: Int): Int {
        val match = REGISTER_REGEX.matchEntire(value.uppercase())
            ?: throw AssemblyException(
                "Line $lineNumber: invalid register '$value'"
            )

        val register = match.groupValues[1].toInt()

        if (register !in 0 until REGISTER_COUNT) {
            throw AssemblyException(
                "Line $lineNumber: register '$value' is out of range"
            )
        }

        return register
    }

    private fun parseValueOrLabel(
        value: String,
        labels: Map<String, Int>,
        lineNumber: Int
    ): Int {
        val number = parseNumber(value)

        val resolved = number
            ?: labels[value]
            ?: throw AssemblyException(
                "Line $lineNumber: unknown value or label '$value'"
            )

        if (resolved !in 0..255) {
            throw AssemblyException(
                "Line $lineNumber: value '$value' resolves to $resolved, but only 0..255 is allowed"
            )
        }

        return resolved
    }

    private fun parseNumber(value: String): Int? {
        val trimmed = value.trim()

        return when {
            trimmed.startsWith("0x", ignoreCase = true) ->
                trimmed.drop(2).toIntOrNull(16)

            trimmed.startsWith("0b", ignoreCase = true) ->
                trimmed.drop(2).toIntOrNull(2)

            else ->
                trimmed.toIntOrNull()
        }
    }

    private sealed interface Statement {
        val lineNumber: Int

        data class Label(
            val name: String,
            override val lineNumber: Int
        ) : Statement

        data class Instruction(
            val mnemonic: String,
            val arguments: List<String>,
            override val lineNumber: Int,
            val original: String
        ) : Statement
    }

    companion object {
        private val LABEL_REGEX =
            Regex("""^([A-Za-z_][A-Za-z0-9_]*):\s*(.*)$""")

        private val REGISTER_REGEX =
            Regex("""^R([0-9]+)$""")

        private const val REGISTER_COUNT = 4
    }
}

class AssemblyException(message: String) : RuntimeException(message)