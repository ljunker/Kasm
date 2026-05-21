package de.ljunker.kasm

import java.nio.file.Path
import kotlin.io.path.readText

fun main(args: Array<String>) {
    when {
        args.size == 1 -> runFile(Path.of(args[0]))
        args.size == 2 && args[0].equals("debug", ignoreCase = true) ->
            debugFile(Path.of(args[1]))

        else -> printUsage()
    }
}

private fun runFile(sourcePath: Path) {
    val source = sourcePath.readText()

    val program = Assembler().assemble(source)

    println("Bytecode:")
    println(program.hexDump())
    println()

    println("Output:")
    VirtualMachine().run(program)
}

private fun debugFile(sourcePath: Path) {
    val source = sourcePath.readText()
    val debugProgram = Assembler().assembleWithDebugInfo(source)

    Debugger(
        debugProgram = debugProgram,
        sourceName = sourcePath.toString(),
        readCommand = {
            print("debug> ")
            readlnOrNull()
        }
    ).run()
}

private fun printUsage() {
    println("Usage:")
    println("  kasm <file.kasm>")
    println("  kasm debug <file.kasm>")
}
