package de.ljunker.kasm

import java.io.IOException
import java.math.BigInteger
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.readText

class Assembler(
    private val baseDirectory: Path = Path.of(".")
) {

    fun assemble(source: String): Program =
        assembleWithDebugInfo(source).program

    fun assemble(source: String, sourcePath: Path?): Program =
        assembleWithDebugInfo(source, sourcePath).program

    fun assembleFile(sourcePath: Path): Program =
        assembleFileWithDebugInfo(sourcePath).program

    fun assembleWithDebugInfo(source: String): DebugProgram =
        assembleWithDebugInfo(source, sourcePath = null)

    fun assembleWithDebugInfo(source: String, sourcePath: Path?): DebugProgram {
        val context = sourceContextFor(sourcePath)
        val statements = parseStatements(
            source = source,
            context = context,
            includeStack = context.sourcePath?.let(::setOf).orEmpty()
        )
        val collectedSymbols = collectSymbols(statements)
        val encoding = encode(statements, collectedSymbols.symbols)

        return DebugProgram(
            program = Program(
                bytes = encoding.bytes,
                initialMemory = encoding.initialMemory,
                fileResources = collectedSymbols.fileResources
            ),
            sourceMap = SourceMap(
                locationsByAddress = encoding.sourceLocations,
                primarySourcePath = context.sourcePath
            ),
            symbols = collectedSymbols.debugSymbols
        )
    }

    fun assembleFileWithDebugInfo(sourcePath: Path): DebugProgram {
        val normalizedSourcePath = normalizeSourcePath(sourcePath)

        return assembleWithDebugInfo(
            source = normalizedSourcePath.readText(),
            sourcePath = normalizedSourcePath
        )
    }

    private fun parseStatements(
        source: String,
        context: SourceContext,
        includeStack: Set<Path>
    ): List<Statement> {
        val statements = mutableListOf<Statement>()

        source.lines().forEachIndexed { index, rawLine ->
            val lineNumber = index + 1

            var line = stripComment(rawLine)
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
                        lineNumber = lineNumber,
                        sourceDirectory = context.directory,
                        sourcePath = context.sourcePath
                    )

                    line = rest
                    continue
                }

                val parts = line.split(Regex("\\s+"), limit = 2)
                val name = parts[0].uppercase()
                val arguments = splitArguments(
                    value = parts.getOrNull(1).orEmpty(),
                    lineNumber = lineNumber
                )

                if (name == ".INCLUDE") {
                    statements += readIncludedStatements(
                        arguments = arguments,
                        lineNumber = lineNumber,
                        context = context,
                        includeStack = includeStack
                    )
                    line = ""
                    continue
                }

                statements += if (name.startsWith(".")) {
                    Statement.Directive(
                        name = name,
                        arguments = arguments,
                        lineNumber = lineNumber,
                        sourceDirectory = context.directory,
                        sourcePath = context.sourcePath
                    )
                } else {
                    Statement.Instruction(
                        mnemonic = name,
                        arguments = arguments,
                        lineNumber = lineNumber,
                        original = rawLine,
                        sourceDirectory = context.directory,
                        sourcePath = context.sourcePath
                    )
                }

                line = ""
            }
        }

        return statements
    }

    private fun readIncludedStatements(
        arguments: List<String>,
        lineNumber: Int,
        context: SourceContext,
        includeStack: Set<Path>
    ): List<Statement> {
        if (arguments.size != 1) {
            throw AssemblyException(
                "Line $lineNumber: .include expects 1 argument(s), got ${arguments.size}"
            )
        }

        val includeSourcePath = parseStringLiteral(arguments[0], lineNumber)
        val path = resolvePath(includeSourcePath, context.directory)
        val stackPath = path.toAbsolutePath().normalize()

        if (stackPath in includeStack) {
            throw AssemblyException("Line $lineNumber: recursive include '$includeSourcePath'")
        }

        val includedSource = try {
            path.readText()
        } catch (error: IOException) {
            throw AssemblyException(
                "Line $lineNumber: could not read include file '$includeSourcePath': ${error.message}"
            )
        }

        return parseStatements(
            source = includedSource,
            context = SourceContext(
                directory = path.parent ?: context.directory,
                sourcePath = path
            ),
            includeStack = includeStack + stackPath
        )
    }

    private fun stripComment(line: String): String {
        var inString = false
        var escaped = false

        line.forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                inString && char == '\\' -> escaped = true
                char == '"' -> inString = !inString
                char == ';' && !inString -> return line.substring(0, index)
            }
        }

        return line
    }

    private fun splitArguments(value: String, lineNumber: Int): List<String> {
        if (value.isBlank()) {
            return emptyList()
        }

        val arguments = mutableListOf<String>()
        val current = StringBuilder()
        var inString = false
        var escaped = false

        value.forEach { char ->
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }

                inString && char == '\\' -> {
                    current.append(char)
                    escaped = true
                }

                char == '"' -> {
                    current.append(char)
                    inString = !inString
                }

                char == ',' && !inString -> {
                    current.toString().trim()
                        .takeIf(String::isNotBlank)
                        ?.let(arguments::add)
                    current.clear()
                }

                else -> current.append(char)
            }
        }

        if (inString) {
            throw AssemblyException("Line $lineNumber: unterminated string literal")
        }

        current.toString().trim()
            .takeIf(String::isNotBlank)
            ?.let(arguments::add)

        return arguments
    }

    private fun collectSymbols(statements: List<Statement>): CollectedSymbols {
        val constants = collectConstantDefinitions(statements)
        val files = collectFileDefinitions(statements, constants)
        val symbols = Symbols(constants, files)
        val pendingLabels = mutableListOf<Statement.Label>()
        val variables = mutableListOf<DebugVariable>()
        var codeAddress = 0
        var dataAddress = 0

        for (statement in statements) {
            when (statement) {
                is Statement.Label ->
                    pendingLabels += statement

                is Statement.Instruction -> {
                    defineLabels(pendingLabels, codeAddress, symbols)

                    val opcode = resolveOpcode(statement)

                    ensureArgumentCount(statement, opcode)

                    codeAddress += instructionSize(opcode)
                }

                is Statement.Directive -> {
                    val kind = directiveKind(statement)

                    ensureDirectiveArgumentCount(statement, kind)

                    when (kind) {
                        DirectiveKind.EQU,
                        DirectiveKind.FILE -> Unit

                        DirectiveKind.ORG ->
                            dataAddress = parseAddressExpression(
                                value = statement.arguments[0],
                                symbols = symbols,
                                lineNumber = statement.lineNumber
                            )

                        DirectiveKind.BYTE,
                        DirectiveKind.NUM64,
                        DirectiveKind.ASCII,
                        DirectiveKind.STRING,
                        DirectiveKind.INCBIN -> {
                            val dataLabels = pendingLabels.toList()
                            defineLabels(pendingLabels, dataAddress, symbols)

                            val size = dataDirectiveSize(statement, kind)

                            ensureDataRange(statement, dataAddress, size)
                            variables += dataLabels.map { label ->
                                DebugVariable(
                                    name = label.name,
                                    address = dataAddress,
                                    size = size,
                                    kind = kind.toDebugVariableKind(),
                                    lineNumber = label.lineNumber,
                                    sourcePath = label.sourcePath
                                )
                            }
                            dataAddress += size
                        }
                    }
                }
            }
        }

        defineLabels(pendingLabels, codeAddress, symbols)

        return CollectedSymbols(
            symbols = symbols,
            fileResources = files.values.map { definition ->
                ProgramFile(
                    id = definition.id,
                    name = definition.name,
                    path = definition.path
                )
            },
            debugSymbols = DebugSymbols(
                constants = symbols.debugConstants(),
                variables = variables
            )
        )
    }

    private fun collectConstantDefinitions(
        statements: List<Statement>
    ): Map<String, ConstantDefinition> {
        val definitions = mutableMapOf<String, ConstantDefinition>()

        statements
            .filterIsInstance<Statement.Directive>()
            .filter { statement -> statement.name == ".EQU" }
            .forEach { statement ->
                ensureDirectiveArgumentCount(statement, DirectiveKind.EQU)

                val name = statement.arguments[0]

                if (!SYMBOL_REGEX.matches(name)) {
                    throw AssemblyException(
                        "Line ${statement.lineNumber}: invalid symbol name '$name'"
                    )
                }

                if (name in definitions) {
                    throw AssemblyException(
                        "Line ${statement.lineNumber}: duplicate symbol '$name'"
                    )
                }

                definitions[name] = ConstantDefinition(
                    expression = parseExpression(statement.arguments[1], statement.lineNumber),
                    lineNumber = statement.lineNumber,
                    sourcePath = statement.sourcePath
                )
            }

        return definitions
    }

    private fun collectFileDefinitions(
        statements: List<Statement>,
        constants: Map<String, ConstantDefinition>
    ): Map<String, FileDefinition> {
        val definitions = linkedMapOf<String, FileDefinition>()

        statements
            .filterIsInstance<Statement.Directive>()
            .filter { statement -> statement.name == ".FILE" }
            .forEach { statement ->
                ensureDirectiveArgumentCount(statement, DirectiveKind.FILE)

                val name = statement.arguments[0]

                if (!SYMBOL_REGEX.matches(name)) {
                    throw AssemblyException(
                        "Line ${statement.lineNumber}: invalid symbol name '$name'"
                    )
                }

                if (name in constants || name in definitions) {
                    throw AssemblyException(
                        "Line ${statement.lineNumber}: duplicate symbol '$name'"
                    )
                }

                if (definitions.size == Architecture.WORD_VALUE_COUNT) {
                    throw AssemblyException(
                        "Line ${statement.lineNumber}: too many file sources; max " +
                                "${Architecture.WORD_VALUE_COUNT}"
                    )
                }

                val sourcePath = parseStringLiteral(statement.arguments[1], statement.lineNumber)

                definitions[name] = FileDefinition(
                    id = definitions.size,
                    name = name,
                    path = resolvePath(sourcePath, statement.sourceDirectory),
                    lineNumber = statement.lineNumber,
                    sourcePath = statement.sourcePath
                )
            }

        return definitions
    }

    private fun DirectiveKind.toDebugVariableKind(): DebugVariableKind =
        when (this) {
            DirectiveKind.BYTE -> DebugVariableKind.BYTE
            DirectiveKind.NUM64 -> DebugVariableKind.NUM64
            DirectiveKind.ASCII -> DebugVariableKind.ASCII
            DirectiveKind.STRING -> DebugVariableKind.STRING
            DirectiveKind.INCBIN -> DebugVariableKind.INCBIN
            DirectiveKind.EQU,
            DirectiveKind.ORG,
            DirectiveKind.FILE ->
                error("$this does not initialize data")
        }

    private fun defineLabels(
        pendingLabels: MutableList<Statement.Label>,
        address: Int,
        symbols: Symbols
    ) {
        pendingLabels.forEach { label ->
            symbols.defineLabel(
                name = label.name,
                address = address,
                lineNumber = label.lineNumber
            )
        }
        pendingLabels.clear()
    }

    private fun dataDirectiveSize(statement: Statement.Directive, kind: DirectiveKind): Int =
        when (kind) {
            DirectiveKind.BYTE ->
                statement.arguments.size

            DirectiveKind.NUM64 ->
                NUM64_BYTE_COUNT

            DirectiveKind.ASCII ->
                parseAsciiBytes(statement.arguments[0], statement.lineNumber).size

            DirectiveKind.STRING ->
                parseAsciiBytes(statement.arguments[0], statement.lineNumber).size + 1

            DirectiveKind.INCBIN ->
                readIncbinBytes(statement).size

            DirectiveKind.EQU,
            DirectiveKind.ORG,
            DirectiveKind.FILE ->
                0
        }

    private fun ensureDataRange(
        statement: Statement.Directive,
        startAddress: Int,
        size: Int
    ) {
        if (size == 0) {
            return
        }

        val endAddress = startAddress + size - 1

        if (startAddress !in Architecture.addressRange || endAddress !in Architecture.addressRange) {
            throw AssemblyException(
                "Line ${statement.lineNumber}: ${statement.name.lowercase()} data range " +
                        "$startAddress..$endAddress is outside ${Architecture.addressRange}"
            )
        }
    }

    private fun encode(
        statements: List<Statement>,
        symbols: Symbols
    ): Encoding {
        val bytes = mutableListOf<Int>()
        val sourceLocations = mutableMapOf<Int, SourceLocation>()
        val initialMemory = mutableMapOf<Int, Int>()
        var dataAddress = 0

        for (statement in statements) {
            when (statement) {
                is Statement.Label -> Unit

                is Statement.Instruction -> {
                    val opcode = resolveOpcode(statement)

                    ensureArgumentCount(statement, opcode)

                    sourceLocations[bytes.size] = SourceLocation(
                        lineNumber = statement.lineNumber,
                        source = statement.original,
                        sourcePath = statement.sourcePath
                    )
                    bytes += opcode.code

                    encodeInstruction(statement, opcode, symbols, bytes)
                }

                is Statement.Directive -> {
                    val kind = directiveKind(statement)

                    ensureDirectiveArgumentCount(statement, kind)

                    when (kind) {
                        DirectiveKind.EQU,
                        DirectiveKind.FILE -> Unit

                        DirectiveKind.ORG ->
                            dataAddress = parseAddressExpression(
                                value = statement.arguments[0],
                                symbols = symbols,
                                lineNumber = statement.lineNumber
                            )

                        DirectiveKind.BYTE,
                        DirectiveKind.NUM64,
                        DirectiveKind.ASCII,
                        DirectiveKind.STRING,
                        DirectiveKind.INCBIN -> {
                            val dataBytes = encodeDataDirective(statement, kind, symbols)

                            ensureDataRange(statement, dataAddress, dataBytes.size)
                            writeInitialMemory(
                                statement = statement,
                                initialMemory = initialMemory,
                                startAddress = dataAddress,
                                dataBytes = dataBytes
                            )
                            dataAddress += dataBytes.size
                        }
                    }
                }
            }
        }

        return Encoding(
            bytes = bytes,
            sourceLocations = sourceLocations,
            initialMemory = initialMemory
        )
    }

    private fun encodeInstruction(
        statement: Statement.Instruction,
        opcode: Opcode,
        symbols: Symbols,
        bytes: MutableList<Int>
    ) {
        when (opcode) {
            Opcode.MOV,
            Opcode.ADDI,
            Opcode.SUBI -> {
                bytes += parseRegister(statement.arguments[0], statement.lineNumber)
                bytes += parseByteExpression(statement.arguments[1], symbols, statement.lineNumber)
            }

            Opcode.PUSHI,
            Opcode.DROP -> {
                bytes += parseByteExpression(statement.arguments[0], symbols, statement.lineNumber)
            }

            Opcode.MOV_REGISTER -> {
                bytes += parseRegister(statement.arguments[0], statement.lineNumber)
                bytes += parseRegister(statement.arguments[1], statement.lineNumber)
            }

            Opcode.MOVA -> {
                bytes += parseAddressRegister(statement.arguments[0], statement.lineNumber)
                encodeAddress(
                    address = parseAddressExpression(statement.arguments[1], symbols, statement.lineNumber),
                    bytes = bytes
                )
            }

            Opcode.MOVA_REGISTER -> {
                bytes += parseAddressRegister(statement.arguments[0], statement.lineNumber)
                bytes += parseAddressRegister(statement.arguments[1], statement.lineNumber)
            }

            Opcode.PUSHA -> {
                encodeAddress(
                    address = parseAddressExpression(statement.arguments[0], symbols, statement.lineNumber),
                    bytes = bytes
                )
            }

            Opcode.PUSHA_REGISTER -> {
                bytes += parseAddressRegister(statement.arguments[0], statement.lineNumber)
            }

            Opcode.ADD,
            Opcode.ADC,
            Opcode.SUB,
            Opcode.SBC,
            Opcode.CMP,
            Opcode.MUL,
            Opcode.DIV,
            Opcode.MOD,
            Opcode.AND,
            Opcode.OR,
            Opcode.XOR,
            Opcode.NEG,
            Opcode.NOT,
            Opcode.CLR -> {
                bytes += parseRegister(statement.arguments[0], statement.lineNumber)
                if (statement.arguments.size > 1) {
                    bytes += parseRegister(statement.arguments[1], statement.lineNumber)
                }
            }

            Opcode.JZ,
            Opcode.JNZ -> {
                bytes += parseRegister(statement.arguments[0], statement.lineNumber)
                encodeAddress(
                    address = parseAddressExpression(statement.arguments[1], symbols, statement.lineNumber),
                    bytes = bytes
                )
            }
            Opcode.JE,
            Opcode.JNE,
            Opcode.JG,
            Opcode.JL,
            Opcode.JGE,
            Opcode.JLE,
            Opcode.JC,
            Opcode.JNC,
            Opcode.JMP,
            Opcode.CALL -> {
                encodeAddress(
                    address = parseAddressExpression(statement.arguments[0], symbols, statement.lineNumber),
                    bytes = bytes
                )
            }

            Opcode.LOAD -> {
                bytes += parseRegister(statement.arguments[0], statement.lineNumber)
                encodeAddress(
                    address = parseDirectMemoryAddress(statement.arguments[1], symbols, statement.lineNumber),
                    bytes = bytes
                )
            }

            Opcode.STORE -> {
                encodeAddress(
                    address = parseDirectMemoryAddress(statement.arguments[0], symbols, statement.lineNumber),
                    bytes = bytes
                )
                bytes += parseRegister(statement.arguments[1], statement.lineNumber)
            }

            Opcode.LOAD_INDEXED -> {
                bytes += parseRegister(statement.arguments[0], statement.lineNumber)
                encodeIndexedMemoryAddress(
                    value = statement.arguments[1],
                    symbols = symbols,
                    lineNumber = statement.lineNumber,
                    bytes = bytes
                )
            }

            Opcode.STORE_INDEXED -> {
                encodeIndexedMemoryAddress(
                    value = statement.arguments[0],
                    symbols = symbols,
                    lineNumber = statement.lineNumber,
                    bytes = bytes
                )
                bytes += parseRegister(statement.arguments[1], statement.lineNumber)
            }

            Opcode.LOAD_ADDRESS_REGISTER -> {
                bytes += parseRegister(statement.arguments[0], statement.lineNumber)
                bytes += parseAddressRegisterMemoryAddress(statement.arguments[1], statement.lineNumber)
            }

            Opcode.STORE_ADDRESS_REGISTER -> {
                bytes += parseAddressRegisterMemoryAddress(statement.arguments[0], statement.lineNumber)
                bytes += parseRegister(statement.arguments[1], statement.lineNumber)
            }

            Opcode.PEEK -> {
                bytes += parseRegister(statement.arguments[0], statement.lineNumber)
                bytes += parseByteExpression(statement.arguments[1], symbols, statement.lineNumber)
            }

            Opcode.PEEK_REGISTER_OFFSET -> {
                bytes += parseRegister(statement.arguments[0], statement.lineNumber)
                bytes += parseRegister(statement.arguments[1], statement.lineNumber)
            }

            Opcode.PEEKA -> {
                bytes += parseAddressRegister(statement.arguments[0], statement.lineNumber)
                bytes += parseByteExpression(statement.arguments[1], symbols, statement.lineNumber)
            }

            Opcode.PEEKA_REGISTER_OFFSET -> {
                bytes += parseAddressRegister(statement.arguments[0], statement.lineNumber)
                bytes += parseRegister(statement.arguments[1], statement.lineNumber)
            }

            Opcode.PUSH,
            Opcode.POP,
            Opcode.DROP_REGISTER,
            Opcode.INC,
            Opcode.DEC -> {
                bytes += parseRegister(statement.arguments[0], statement.lineNumber)
            }

            Opcode.INCA,
            Opcode.DECA -> {
                bytes += parseAddressRegister(statement.arguments[0], statement.lineNumber)
            }

            Opcode.PRINT,
            Opcode.PRINTC -> {
                bytes += parseRegister(statement.arguments[0], statement.lineNumber)
            }

            Opcode.FREAD -> {
                bytes += parseRegister(statement.arguments[0], statement.lineNumber)
                bytes += symbols.resolveFileSource(statement.arguments[1], statement.lineNumber)
            }

            Opcode.FREWIND -> {
                bytes += symbols.resolveFileSource(statement.arguments[0], statement.lineNumber)
            }

            Opcode.RET,
            Opcode.HALT,
            Opcode.PUSHF,
            Opcode.POPF,
            Opcode.NOP -> {
                // no operands
            }
        }
    }

    private fun encodeDataDirective(
        statement: Statement.Directive,
        kind: DirectiveKind,
        symbols: Symbols
    ): List<Int> =
        when (kind) {
            DirectiveKind.BYTE ->
                statement.arguments.map { argument ->
                    parseByteExpression(argument, symbols, statement.lineNumber)
                }

            DirectiveKind.NUM64 ->
                encodeNum64Directive(statement, symbols)

            DirectiveKind.ASCII ->
                parseAsciiBytes(statement.arguments[0], statement.lineNumber)

            DirectiveKind.STRING ->
                parseAsciiBytes(statement.arguments[0], statement.lineNumber) + 0

            DirectiveKind.INCBIN ->
                readIncbinBytes(statement)

            DirectiveKind.EQU,
            DirectiveKind.ORG,
            DirectiveKind.FILE ->
                emptyList()
        }

    private fun writeInitialMemory(
        statement: Statement.Directive,
        initialMemory: MutableMap<Int, Int>,
        startAddress: Int,
        dataBytes: List<Int>
    ) {
        dataBytes.forEachIndexed { offset, value ->
            val address = startAddress + offset

            if (initialMemory.put(address, value) != null) {
                throw AssemblyException(
                    "Line ${statement.lineNumber}: data memory address $address is " +
                            "initialized more than once"
                )
            }
        }
    }

    private fun encodeAddress(address: Int, bytes: MutableList<Int>) {
        bytes += address and Architecture.WORD_MASK
        bytes += (address ushr Architecture.WORD_BITS) and Architecture.WORD_MASK
    }

    private fun instructionSize(opcode: Opcode): Int =
        1 + opcode.operandByteCount

    private fun directiveKind(statement: Statement.Directive): DirectiveKind =
        when (statement.name) {
            ".EQU" -> DirectiveKind.EQU
            ".ORG" -> DirectiveKind.ORG
            ".BYTE" -> DirectiveKind.BYTE
            ".NUM64" -> DirectiveKind.NUM64
            ".ASCII" -> DirectiveKind.ASCII
            ".STRING" -> DirectiveKind.STRING
            ".INCBIN" -> DirectiveKind.INCBIN
            ".FILE" -> DirectiveKind.FILE
            else -> throw AssemblyException(
                "Line ${statement.lineNumber}: unknown directive '${statement.name}'"
            )
        }

    private fun ensureDirectiveArgumentCount(
        statement: Statement.Directive,
        kind: DirectiveKind
    ) {
        val count = statement.arguments.size

        when (kind) {
            DirectiveKind.EQU -> {
                if (count != 2) {
                    throw AssemblyException(
                        "Line ${statement.lineNumber}: .equ expects 2 argument(s), got $count"
                    )
                }
            }

            DirectiveKind.ORG -> {
                if (count != 1) {
                    throw AssemblyException(
                        "Line ${statement.lineNumber}: .org expects 1 argument(s), got $count"
                    )
                }
            }

            DirectiveKind.BYTE -> {
                if (count == 0) {
                    throw AssemblyException(
                        "Line ${statement.lineNumber}: .byte expects at least 1 argument"
                    )
                }
            }

            DirectiveKind.NUM64,
            DirectiveKind.ASCII,
            DirectiveKind.STRING,
            DirectiveKind.INCBIN -> {
                if (count != 1) {
                    throw AssemblyException(
                        "Line ${statement.lineNumber}: ${statement.name.lowercase()} expects " +
                                "1 argument(s), got $count"
                    )
                }
            }

            DirectiveKind.FILE -> {
                if (count != 2) {
                    throw AssemblyException(
                        "Line ${statement.lineNumber}: .file expects 2 argument(s), got $count"
                    )
                }
            }
        }
    }

    private fun resolveOpcode(statement: Statement.Instruction): Opcode {
        val opcode = Opcode.fromMnemonic(statement.mnemonic)
            ?: throw AssemblyException(
                "Line ${statement.lineNumber}: unknown instruction '${statement.mnemonic}'"
            )

        if (opcode == Opcode.MOV && statement.arguments.getOrNull(1)?.let(::isRegister) == true) {
            return Opcode.MOV_REGISTER
        }

        if (
            opcode == Opcode.MOVA &&
            statement.arguments.getOrNull(1)?.let(::isAddressRegister) == true
        ) {
            return Opcode.MOVA_REGISTER
        }

        if (
            opcode == Opcode.PUSHA &&
            statement.arguments.getOrNull(0)?.let(::isAddressRegister) == true
        ) {
            return Opcode.PUSHA_REGISTER
        }

        if (
            opcode == Opcode.DROP &&
            statement.arguments.getOrNull(0)?.let(::isRegister) == true
        ) {
            return Opcode.DROP_REGISTER
        }

        if (
            opcode == Opcode.PEEK &&
            statement.arguments.getOrNull(1)?.let(::isRegister) == true
        ) {
            return Opcode.PEEK_REGISTER_OFFSET
        }

        if (
            opcode == Opcode.PEEKA &&
            statement.arguments.getOrNull(1)?.let(::isRegister) == true
        ) {
            return Opcode.PEEKA_REGISTER_OFFSET
        }

        if (
            opcode == Opcode.LOAD &&
            statement.arguments.getOrNull(1)
                ?.let { isAddressRegisterMemoryAddress(it, statement.lineNumber) } == true
        ) {
            return Opcode.LOAD_ADDRESS_REGISTER
        }

        if (
            opcode == Opcode.STORE &&
            statement.arguments.getOrNull(0)
                ?.let { isAddressRegisterMemoryAddress(it, statement.lineNumber) } == true
        ) {
            return Opcode.STORE_ADDRESS_REGISTER
        }

        if (
            opcode == Opcode.LOAD &&
            statement.arguments.getOrNull(1)
                ?.let { isIndexedMemoryAddress(it, statement.lineNumber) } == true
        ) {
            return Opcode.LOAD_INDEXED
        }

        if (
            opcode == Opcode.STORE &&
            statement.arguments.getOrNull(0)
                ?.let { isIndexedMemoryAddress(it, statement.lineNumber) } == true
        ) {
            return Opcode.STORE_INDEXED
        }

        return opcode
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

    private fun isRegister(value: String): Boolean =
        REGISTER_REGEX.matchEntire(value.uppercase()) != null

    private fun isAddressRegister(value: String): Boolean =
        ADDRESS_REGISTER_REGEX.matchEntire(value.uppercase()) != null

    private fun isAddressRegisterMemoryAddress(value: String, lineNumber: Int): Boolean {
        val expression = parseMemoryExpression(value, lineNumber)
            ?: return false

        return expression is Expression.Symbol && isAddressRegister(expression.name)
    }

    private fun isIndexedMemoryAddress(value: String, lineNumber: Int): Boolean {
        val expression = parseMemoryExpression(value, lineNumber)
            ?: return false

        return splitIndexedMemoryExpression(expression, value, lineNumber) != null
    }

    private fun parseDirectMemoryAddress(
        value: String,
        symbols: Symbols,
        lineNumber: Int
    ): Int {
        val expressionValue = memoryExpressionValue(value)
            ?: throwMemoryAddressSyntax(value, lineNumber)
        val expression = parseExpression(expressionValue, lineNumber)

        if (splitIndexedMemoryExpression(expression, value, lineNumber) != null) {
            throw AssemblyException("Line $lineNumber: indexed memory address '$value' is expected")
        }

        return parseAddressExpression(expressionValue, expression, symbols, lineNumber)
    }

    private fun encodeIndexedMemoryAddress(
        value: String,
        symbols: Symbols,
        lineNumber: Int,
        bytes: MutableList<Int>
    ) {
        val expression = parseMemoryExpression(value, lineNumber)
            ?: throwMemoryAddressSyntax(value, lineNumber)
        val indexed = splitIndexedMemoryExpression(expression, value, lineNumber)
            ?: throw AssemblyException("Line $lineNumber: indexed memory address '$value' is expected")

        encodeAddress(
            address = parseAddressExpression(value, indexed.base, symbols, lineNumber),
            bytes = bytes
        )
        bytes += indexed.indexRegister
    }

    private fun parseAddressRegisterMemoryAddress(value: String, lineNumber: Int): Int {
        val expression = parseMemoryExpression(value, lineNumber)
            ?: throwMemoryAddressSyntax(value, lineNumber)

        if (expression !is Expression.Symbol || !isAddressRegister(expression.name)) {
            throw AssemblyException("Line $lineNumber: address-register memory address '$value' is expected")
        }

        return parseAddressRegister(expression.name, lineNumber)
    }

    private fun parseMemoryExpression(value: String, lineNumber: Int): Expression? {
        val expressionValue = memoryExpressionValue(value)
            ?: return null

        return parseExpression(expressionValue, lineNumber)
    }

    private fun memoryExpressionValue(value: String): String? =
        MEMORY_ADDRESS_REGEX
            .matchEntire(value.trim())
            ?.groupValues
            ?.get(1)
            ?.trim()

    private fun throwMemoryAddressSyntax(value: String, lineNumber: Int): Nothing =
        throw AssemblyException(
            "Line $lineNumber: memory address '$value' must use [value]"
        )

    private fun splitIndexedMemoryExpression(
        expression: Expression,
        value: String,
        lineNumber: Int
    ): IndexedMemoryExpression? =
        when (expression) {
            is Expression.Symbol -> {
                if (isRegister(expression.name)) {
                    IndexedMemoryExpression(
                        base = Expression.Number(BIG_ZERO),
                        indexRegister = parseRegister(expression.name, lineNumber)
                    )
                } else {
                    null
                }
            }

            is Expression.Add -> combineIndexedTerms(
                left = expression.left,
                right = expression.right,
                combineBase = Expression::Add,
                allowRightIndex = true,
                value = value,
                lineNumber = lineNumber
            )

            is Expression.Subtract -> combineIndexedTerms(
                left = expression.left,
                right = expression.right,
                combineBase = Expression::Subtract,
                allowRightIndex = false,
                value = value,
                lineNumber = lineNumber
            )

            is Expression.Number -> null

            is Expression.Negate -> {
                if (containsRegister(expression)) {
                    invalidIndexedMemoryAddress(value, lineNumber)
                }

                null
            }
        }

    private fun combineIndexedTerms(
        left: Expression,
        right: Expression,
        combineBase: (Expression, Expression) -> Expression,
        allowRightIndex: Boolean,
        value: String,
        lineNumber: Int
    ): IndexedMemoryExpression? {
        val leftIndexed = splitIndexedMemoryExpression(left, value, lineNumber)
        val rightIndexed = splitIndexedMemoryExpression(right, value, lineNumber)

        return when {
            leftIndexed != null && rightIndexed == null && !containsRegister(right) ->
                leftIndexed.copy(base = combineBase(leftIndexed.base, right))

            allowRightIndex &&
                    rightIndexed != null &&
                    leftIndexed == null &&
                    !containsRegister(left) ->
                rightIndexed.copy(base = combineBase(left, rightIndexed.base))

            leftIndexed == null && rightIndexed == null ->
                if (containsRegister(left) || containsRegister(right)) {
                    invalidIndexedMemoryAddress(value, lineNumber)
                } else {
                    null
                }

            else ->
                invalidIndexedMemoryAddress(value, lineNumber)
        }
    }

    private fun containsRegister(expression: Expression): Boolean =
        when (expression) {
            is Expression.Number -> false
            is Expression.Symbol -> isRegister(expression.name)
            is Expression.Add -> containsRegister(expression.left) || containsRegister(expression.right)
            is Expression.Subtract ->
                containsRegister(expression.left) || containsRegister(expression.right)

            is Expression.Negate -> containsRegister(expression.expression)
        }

    private fun invalidIndexedMemoryAddress(value: String, lineNumber: Int): Nothing =
        throw AssemblyException(
            "Line $lineNumber: indexed memory address '$value' must use one register " +
                    "as [register] or [base + register]"
        )

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

    private fun parseAddressRegister(value: String, lineNumber: Int): Int {
        val match = ADDRESS_REGISTER_REGEX.matchEntire(value.uppercase())
            ?: throw AssemblyException(
                "Line $lineNumber: invalid address register '$value'"
            )

        val register = match.groupValues[1].toInt()

        if (register !in 0 until ADDRESS_REGISTER_COUNT) {
            throw AssemblyException(
                "Line $lineNumber: address register '$value' is out of range"
            )
        }

        return register
    }

    private fun parseByteExpression(
        value: String,
        symbols: Symbols,
        lineNumber: Int
    ): Int {
        val expression = parseExpression(value, lineNumber)

        return parseByteExpression(value, expression, symbols, lineNumber)
    }

    private fun parseByteExpression(
        value: String,
        expression: Expression,
        symbols: Symbols,
        lineNumber: Int
    ): Int {
        val resolved = evaluateExpression(expression, symbols, lineNumber)

        if (resolved < BIG_ZERO || resolved > BIG_WORD_MASK) {
            throw AssemblyException(
                "Line $lineNumber: value '$value' resolves to $resolved, but only " +
                        "${Architecture.wordRange} is allowed"
            )
        }

        return resolved.toInt()
    }

    private fun parseAddressExpression(
        value: String,
        symbols: Symbols,
        lineNumber: Int
    ): Int {
        val expression = parseExpression(value, lineNumber)

        return parseAddressExpression(value, expression, symbols, lineNumber)
    }

    private fun parseAddressExpression(
        value: String,
        expression: Expression,
        symbols: Symbols,
        lineNumber: Int
    ): Int {
        val resolved = evaluateExpression(expression, symbols, lineNumber)

        if (resolved < BIG_ZERO || resolved > BIG_ADDRESS_MASK) {
            throw AssemblyException(
                "Line $lineNumber: address '$value' resolves to $resolved, but only " +
                        "${Architecture.addressRange} is allowed"
            )
        }

        return resolved.toInt()
    }

    private fun parseExpression(value: String, lineNumber: Int): Expression =
        ExpressionParser(value.trim(), lineNumber).parse()

    private fun evaluateExpression(
        expression: Expression,
        symbols: Symbols,
        lineNumber: Int,
        resolvingConstants: MutableSet<String> = mutableSetOf()
    ): BigInteger =
        when (expression) {
            is Expression.Number -> expression.value
            is Expression.Symbol -> symbols.resolve(
                name = expression.name,
                lineNumber = lineNumber,
                resolvingConstants = resolvingConstants
            )

            is Expression.Add ->
                evaluateExpression(expression.left, symbols, lineNumber, resolvingConstants)
                    .add(evaluateExpression(expression.right, symbols, lineNumber, resolvingConstants))

            is Expression.Subtract ->
                evaluateExpression(expression.left, symbols, lineNumber, resolvingConstants)
                    .subtract(evaluateExpression(expression.right, symbols, lineNumber, resolvingConstants))

            is Expression.Negate ->
                evaluateExpression(expression.expression, symbols, lineNumber, resolvingConstants).negate()
        }

    private fun encodeNum64Directive(
        statement: Statement.Directive,
        symbols: Symbols
    ): List<Int> {
        val value = parseNum64Value(statement.arguments[0], symbols, statement.lineNumber)

        return (0 until NUM64_BYTE_COUNT).map { index ->
            value
                .shiftRight(index * Architecture.WORD_BITS)
                .and(BIG_WORD_MASK)
                .toInt()
        }
    }

    private fun parseNum64Value(
        value: String,
        symbols: Symbols,
        lineNumber: Int
    ): BigInteger {
        if (value.trim().startsWith('"')) {
            throw AssemblyException("Line $lineNumber: .num64 expects a numeric expression, not a file path")
        }
        val resolved = evaluateExpression(parseExpression(value, lineNumber), symbols, lineNumber)

        if (resolved < BIG_ZERO || resolved > UNSIGNED_64_MAX) {
            throw AssemblyException(
                "Line $lineNumber: .num64 value '$value' resolves to $resolved, but only " +
                        "$BIG_ZERO..$UNSIGNED_64_MAX is allowed"
            )
        }

        return resolved
    }

    private fun parseAsciiBytes(value: String, lineNumber: Int): List<Int> {
        val decoded = parseStringLiteral(value, lineNumber)

        return decoded.map { char ->
            if (char.code !in 0..ASCII_MAX) {
                throw AssemblyException(
                    "Line $lineNumber: string literal contains non-ASCII character '$char'"
                )
            }

            char.code
        }
    }

    private fun parseStringLiteral(value: String, lineNumber: Int): String {
        if (!value.startsWith('"')) {
            throw AssemblyException("Line $lineNumber: expected string literal, got '$value'")
        }

        val decoded = StringBuilder()
        var index = 1

        while (index < value.length) {
            val char = value[index++]

            if (char == '"') {
                if (index != value.length) {
                    throw AssemblyException("Line $lineNumber: invalid string literal '$value'")
                }

                return decoded.toString()
            }

            val decodedChar = if (char == '\\') {
                if (index == value.length) {
                    throw AssemblyException("Line $lineNumber: unterminated string escape")
                }

                when (val escaped = value[index++]) {
                    '0' -> '\u0000'
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    '"' -> '"'
                    '\\' -> '\\'
                    else -> throw AssemblyException(
                        "Line $lineNumber: unsupported string escape '\\$escaped'"
                    )
                }
            } else {
                char
            }

            decoded.append(decodedChar)
        }

        throw AssemblyException("Line $lineNumber: unterminated string literal")
    }

    private fun readIncbinBytes(statement: Statement.Directive): List<Int> {
        val sourcePath = parseStringLiteral(statement.arguments[0], statement.lineNumber)
        val path = resolvePath(sourcePath, statement.sourceDirectory)

        return try {
            path.readBytes().map { byte -> byte.toInt() and Architecture.WORD_MASK }
        } catch (error: IOException) {
            throw AssemblyException(
                "Line ${statement.lineNumber}: could not read incbin file '$sourcePath': ${error.message}"
            )
        }
    }

    private fun resolvePath(sourcePath: String, sourceDirectory: Path): Path {
        val path = Path.of(sourcePath)

        return if (path.isAbsolute) {
            path.normalize()
        } else {
            sourceDirectory.resolve(path).toAbsolutePath().normalize()
        }
    }

    private fun sourceContextFor(sourcePath: Path?): SourceContext {
        val normalizedSourcePath = sourcePath?.let(::normalizeSourcePath)

        return SourceContext(
            directory = normalizedSourcePath?.parent ?: baseDirectory.toAbsolutePath().normalize(),
            sourcePath = normalizedSourcePath
        )
    }

    private fun normalizeSourcePath(sourcePath: Path): Path {
        val path = if (sourcePath.isAbsolute) {
            sourcePath
        } else {
            baseDirectory.resolve(sourcePath)
        }

        return path.toAbsolutePath().normalize()
    }

    private inner class Symbols(
        private val constants: Map<String, ConstantDefinition>,
        private val files: Map<String, FileDefinition>
    ) {
        private val labels = mutableMapOf<String, Int>()

        fun defineLabel(name: String, address: Int, lineNumber: Int) {
            if (name in labels || name in constants || name in files) {
                throw AssemblyException("Line $lineNumber: duplicate symbol '$name'")
            }

            labels[name] = address
        }

        fun resolve(
            name: String,
            lineNumber: Int,
            resolvingConstants: MutableSet<String>
        ): BigInteger {
            labels[name]?.let { address ->
                return BigInteger.valueOf(address.toLong())
            }

            if (name in files) {
                throw AssemblyException("Line $lineNumber: file source '$name' cannot be used as a numeric symbol")
            }

            val constant = constants[name]
                ?: throw AssemblyException("Line $lineNumber: unknown symbol '$name'")

            if (!resolvingConstants.add(name)) {
                throw AssemblyException("Line $lineNumber: cyclic constant '$name'")
            }

            val result = evaluateExpression(
                expression = constant.expression,
                symbols = this,
                lineNumber = constant.lineNumber,
                resolvingConstants = resolvingConstants
            )

            resolvingConstants.remove(name)

            return result
        }

        fun resolveFileSource(name: String, lineNumber: Int): Int {
            val trimmedName = name.trim()

            files[trimmedName]?.let { file ->
                return file.id
            }

            if (trimmedName in labels || trimmedName in constants) {
                throw AssemblyException("Line $lineNumber: symbol '$trimmedName' is not a file source")
            }

            throw AssemblyException("Line $lineNumber: unknown file source '$trimmedName'")
        }

        fun debugConstants(): List<DebugConstant> =
            constants.map { (name, constant) ->
                DebugConstant(
                    name = name,
                    value = resolve(
                        name = name,
                        lineNumber = constant.lineNumber,
                        resolvingConstants = mutableSetOf()
                    ),
                    lineNumber = constant.lineNumber,
                    sourcePath = constant.sourcePath
                )
            }
    }

    private inner class ExpressionParser(
        private val value: String,
        private val lineNumber: Int
    ) {
        private var index = 0

        fun parse(): Expression {
            val expression = parseSum()

            skipWhitespace()

            if (index != value.length) {
                invalid()
            }

            return expression
        }

        private fun parseSum(): Expression {
            var expression = parsePrimary()

            while (true) {
                expression = when {
                    consume('+') -> Expression.Add(expression, parsePrimary())
                    consume('-') -> Expression.Subtract(expression, parsePrimary())
                    else -> return expression
                }
            }
        }

        private fun parsePrimary(): Expression {
            skipWhitespace()

            if (consume('-')) {
                return Expression.Negate(parsePrimary())
            }

            if (consume('(')) {
                val expression = parseSum()

                if (!consume(')')) {
                    invalid()
                }

                return expression
            }

            if (index == value.length) {
                invalid()
            }

            val char = value[index]

            return when {
                char.isDigit() -> parseNumber()
                char == '_' || char.isLetter() -> parseSymbol()
                else -> invalid()
            }
        }

        private fun parseNumber(): Expression {
            val start = index

            while (index < value.length && value[index].isLetterOrDigit()) {
                index++
            }

            val token = value.substring(start, index)
            val number = parseNumberLiteral(token)
                ?: invalid()

            return Expression.Number(number)
        }

        private fun parseSymbol(): Expression {
            val start = index

            while (
                index < value.length &&
                (value[index] == '_' || value[index].isLetterOrDigit())
            ) {
                index++
            }

            return Expression.Symbol(value.substring(start, index))
        }

        private fun consume(char: Char): Boolean {
            skipWhitespace()

            if (index < value.length && value[index] == char) {
                index++
                return true
            }

            return false
        }

        private fun skipWhitespace() {
            while (index < value.length && value[index].isWhitespace()) {
                index++
            }
        }

        private fun invalid(): Nothing =
            throw AssemblyException("Line $lineNumber: invalid expression '$value'")
    }

    private sealed interface Expression {
        data class Number(val value: BigInteger) : Expression
        data class Symbol(val name: String) : Expression
        data class Add(val left: Expression, val right: Expression) : Expression
        data class Subtract(val left: Expression, val right: Expression) : Expression
        data class Negate(val expression: Expression) : Expression
    }

    private sealed interface Statement {
        val lineNumber: Int
        val sourceDirectory: Path
        val sourcePath: Path?

        data class Label(
            val name: String,
            override val lineNumber: Int,
            override val sourceDirectory: Path,
            override val sourcePath: Path?
        ) : Statement

        data class Instruction(
            val mnemonic: String,
            val arguments: List<String>,
            override val lineNumber: Int,
            val original: String,
            override val sourceDirectory: Path,
            override val sourcePath: Path?
        ) : Statement

        data class Directive(
            val name: String,
            val arguments: List<String>,
            override val lineNumber: Int,
            override val sourceDirectory: Path,
            override val sourcePath: Path?
        ) : Statement
    }

    private data class SourceContext(
        val directory: Path,
        val sourcePath: Path?
    )

    private enum class DirectiveKind {
        EQU,
        ORG,
        BYTE,
        NUM64,
        ASCII,
        STRING,
        INCBIN,
        FILE
    }

    private data class ConstantDefinition(
        val expression: Expression,
        val lineNumber: Int,
        val sourcePath: Path?
    )

    private data class FileDefinition(
        val id: Int,
        val name: String,
        val path: Path,
        val lineNumber: Int,
        val sourcePath: Path?
    )

    private data class IndexedMemoryExpression(
        val base: Expression,
        val indexRegister: Int
    )

    private data class Encoding(
        val bytes: List<Int>,
        val sourceLocations: Map<Int, SourceLocation>,
        val initialMemory: Map<Int, Int>
    )

    private data class CollectedSymbols(
        val symbols: Symbols,
        val fileResources: List<ProgramFile>,
        val debugSymbols: DebugSymbols
    )

    companion object {
        private val LABEL_REGEX =
            Regex("""^([A-Za-z_][A-Za-z0-9_]*):\s*(.*)$""")

        private val SYMBOL_REGEX =
            Regex("""^[A-Za-z_][A-Za-z0-9_]*$""")

        private val REGISTER_REGEX =
            Regex("""^R([0-9]+)$""")

        private val ADDRESS_REGISTER_REGEX =
            Regex("""^A([0-9]+)$""")

        private val MEMORY_ADDRESS_REGEX =
            Regex("""^\[(.+)]$""")

        private const val ASCII_MAX = 0x7F
        private const val NUM64_BYTE_COUNT = 8
        private const val REGISTER_COUNT = Architecture.REGISTER_COUNT
        private const val ADDRESS_REGISTER_COUNT = Architecture.ADDRESS_REGISTER_COUNT
        private val BIG_ZERO = BigInteger.ZERO
        private val BIG_WORD_MASK = BigInteger.valueOf(Architecture.WORD_MASK.toLong())
        private val BIG_ADDRESS_MASK = BigInteger.valueOf(Architecture.ADDRESS_MASK.toLong())
        private val UNSIGNED_64_MAX = BigInteger.ONE
            .shiftLeft(64)
            .subtract(BigInteger.ONE)

        private fun parseNumberLiteral(value: String): BigInteger? =
            try {
                when {
                    value.startsWith("0x", ignoreCase = true) ->
                        parseBigInteger(value.drop(2), 16)

                    value.startsWith("0b", ignoreCase = true) ->
                        parseBigInteger(value.drop(2), 2)

                    else ->
                        parseBigInteger(value, 10)
                }
            } catch (_: NumberFormatException) {
                null
            }

        private fun parseBigInteger(value: String, radix: Int): BigInteger? {
            if (value.isBlank()) {
                return null
            }

            return BigInteger(value, radix)
        }
    }
}

class AssemblyException(message: String) : RuntimeException(message)
