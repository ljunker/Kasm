package de.ljunker.kasm

import java.nio.file.Path
import kotlin.io.path.readText

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: kasm <file.kasm>")
        return
    }

    val sourcePath = Path.of(args[0])
    val source = sourcePath.readText()

    val program = Assembler().assemble(source)

    println("Bytecode:")
    println(program.hexDump())
    println()

    println("Output:")
    VirtualMachine().run(program)
}