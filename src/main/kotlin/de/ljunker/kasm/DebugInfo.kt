package de.ljunker.kasm

import java.math.BigInteger
import java.nio.file.Path

data class DebugProgram(
    val program: Program,
    val sourceMap: SourceMap,
    val symbols: DebugSymbols = DebugSymbols()
)

data class DebugSymbols(
    val constants: List<DebugConstant> = emptyList(),
    val variables: List<DebugVariable> = emptyList()
) {
    fun snapshot(memory: List<Int>): DebugSymbolSnapshot =
        DebugSymbolSnapshot(
            constants = constants,
            variables = variables.map { variable ->
                val bytes = (0 until variable.size).map { offset ->
                    memory.getOrElse(variable.address + offset) { 0 }
                }

                DebugVariableValue(
                    variable = variable,
                    bytes = bytes,
                    numericValue = numericValue(variable.kind, bytes)
                )
            }
        )

    private fun numericValue(kind: DebugVariableKind, bytes: List<Int>): BigInteger? =
        when (kind) {
            DebugVariableKind.BYTE ->
                if (bytes.size == 1) BigInteger.valueOf(bytes.single().toLong()) else null

            DebugVariableKind.NUM64 ->
                bytes.foldIndexed(BigInteger.ZERO) { index, value, byte ->
                    value.add(
                        BigInteger.valueOf((byte and Architecture.WORD_MASK).toLong())
                            .shiftLeft(index * Architecture.WORD_BITS)
                    )
                }

            DebugVariableKind.ASCII,
            DebugVariableKind.STRING,
            DebugVariableKind.INCBIN ->
                null
        }
}

data class DebugConstant(
    val name: String,
    val value: BigInteger,
    val lineNumber: Int,
    val sourcePath: Path? = null
)

data class DebugVariable(
    val name: String,
    val address: Int,
    val size: Int,
    val kind: DebugVariableKind,
    val lineNumber: Int,
    val sourcePath: Path? = null
)

enum class DebugVariableKind {
    BYTE,
    NUM64,
    ASCII,
    STRING,
    INCBIN
}

data class DebugSymbolSnapshot(
    val constants: List<DebugConstant> = emptyList(),
    val variables: List<DebugVariableValue> = emptyList()
) {
    fun formatLines(): List<String> =
        DebugSymbolFormatter.formatLines(this)

    fun formatVariableLines(): List<String> =
        DebugSymbolFormatter.formatVariableLines(this)
}

data class DebugVariableValue(
    val variable: DebugVariable,
    val bytes: List<Int>,
    val numericValue: BigInteger? = null
)

object DebugSymbolFormatter {
    fun formatLines(snapshot: DebugSymbolSnapshot): List<String> =
        listOf(formatConstants(snapshot)) + formatVariableLines(snapshot)

    fun formatConstants(snapshot: DebugSymbolSnapshot): String {
        if (snapshot.constants.isEmpty()) {
            return "Constants: none"
        }

        return snapshot.constants
            .joinToString(prefix = "Constants: ", separator = " ") { constant ->
                "${constant.name}=${constant.value}"
            }
    }

    fun formatVariableLines(snapshot: DebugSymbolSnapshot): List<String> {
        if (snapshot.variables.isEmpty()) {
            return listOf("Variables: none")
        }

        return listOf("Variables:") + snapshot.variables.map { value ->
            "  ${formatVariableValue(value)}"
        }
    }

    private fun formatVariableValue(value: DebugVariableValue): String {
        val variable = value.variable
        val location = "${variable.name}@${formatAddressValue(variable.address)}"

        return when (variable.kind) {
            DebugVariableKind.BYTE ->
                "$location .byte=${formatByteValue(value)}"

            DebugVariableKind.NUM64 ->
                "$location .num64=${value.numericValue}"

            DebugVariableKind.ASCII ->
                "$location .ascii=${formatAscii(value.bytes)}"

            DebugVariableKind.STRING ->
                "$location .string=${formatAscii(value.bytes)}"

            DebugVariableKind.INCBIN ->
                "$location .incbin=${formatByteList(value.bytes)}"
        }
    }

    private fun formatByteValue(value: DebugVariableValue): String =
        value.numericValue?.toString() ?: formatByteList(value.bytes)

    private fun formatByteList(bytes: List<Int>): String =
        bytes.joinToString(prefix = "[", separator = ",", postfix = "]")

    private fun formatAscii(bytes: List<Int>): String =
        bytes.joinToString(prefix = "\"", separator = "", postfix = "\"") { byte ->
            when (byte) {
                0 -> "\\0"
                '\n'.code -> "\\n"
                '\r'.code -> "\\r"
                '\t'.code -> "\\t"
                '"'.code -> "\\\""
                '\\'.code -> "\\\\"
                in 32..126 -> byte.toChar().toString()
                else -> "\\x%02X".format(byte)
            }
        }

    private fun formatAddressValue(address: Int): String =
        "0x%04X".format(address)
}

data class SourceLocation(
    val lineNumber: Int,
    val source: String,
    val sourcePath: Path? = null
)

class SourceMap(
    locationsByAddress: Map<Int, SourceLocation>,
    val primarySourcePath: Path? = null
) {
    private val locationsByAddress = locationsByAddress.toMap()
    private val normalizedPrimarySourcePath = primarySourcePath?.normalizeForLookup()
    private val addressesByLine = this.locationsByAddress.entries
        .filter { (_, location) -> location.normalizedSourcePath() == normalizedPrimarySourcePath }
        .associate { (address, location) -> location.lineNumber to address }
    private val addressesByLocation = this.locationsByAddress.entries
        .mapNotNull { (address, location) ->
            location.sourcePath?.let { sourcePath ->
                SourcePosition(
                    sourcePath = sourcePath.normalizeForLookup(),
                    lineNumber = location.lineNumber
                ) to address
            }
        }
        .associate { it }

    fun locationForAddress(address: Int): SourceLocation? =
        locationsByAddress[address]

    fun addressForLine(lineNumber: Int): Int? =
        addressesByLine[lineNumber]

    fun addressForLocation(sourcePath: Path, lineNumber: Int): Int? =
        addressesByLocation[
            SourcePosition(
                sourcePath = sourcePath.normalizeForLookup(),
                lineNumber = lineNumber
            )
        ]

    fun executableLines(): Set<Int> =
        addressesByLine.keys

    private fun SourceLocation.normalizedSourcePath(): Path? =
        sourcePath?.normalizeForLookup()

    private fun Path.normalizeForLookup(): Path =
        toAbsolutePath().normalize()

    private data class SourcePosition(
        val sourcePath: Path,
        val lineNumber: Int
    )
}
