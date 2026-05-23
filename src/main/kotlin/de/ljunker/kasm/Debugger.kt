package de.ljunker.kasm

class Debugger(
    private val debugProgram: DebugProgram,
    private val sourceName: String,
    private val readCommand: () -> String?,
    private val output: (String) -> Unit = ::println
) {
    private val session = DebugSession(
        debugProgram = debugProgram,
        output = output
    )

    fun run() {
        output("Debugging $sourceName")
        output("Commands: br <line>, run, step, state, breakpoints, help, quit")
        printState("Ready.")

        while (true) {
            val command = readCommand() ?: return

            if (!handleCommand(command.trim())) {
                return
            }
        }
    }

    private fun handleCommand(command: String): Boolean {
        if (command.isBlank()) {
            return true
        }

        val parts = command.split(Regex("\\s+"), limit = 2)

        return when (parts[0].lowercase()) {
            "br" -> {
                setBreakpoint(parts.getOrNull(1))
                true
            }

            "run" -> {
                continueUntilBreakpoint()
                true
            }

            "step" -> {
                stepOnce()
                true
            }

            "state" -> {
                printState("State.")
                true
            }

            "breakpoints" -> {
                printBreakpoints()
                true
            }

            "help" -> {
                printHelp()
                true
            }

            "quit",
            "exit" -> false

            else -> {
                output("Unknown command '$command'. Use help for commands.")
                true
            }
        }
    }

    private fun setBreakpoint(lineArgument: String?) {
        val lineNumber = lineArgument?.trim()?.toIntOrNull()

        if (lineNumber == null || lineNumber <= 0) {
            output("Usage: br <line>")
            return
        }

        val breakpoint = session.setBreakpoint(lineNumber)

        if (breakpoint == null) {
            output("Line $lineNumber has no executable instruction.")
            return
        }

        output("Breakpoint set at line ${breakpoint.lineNumber} (address ${breakpoint.address}).")
    }

    private fun continueUntilBreakpoint() {
        when (val stop = session.run()) {
            is DebugStop.BreakpointHit ->
                printState("Breakpoint hit at line ${stop.breakpoint.lineNumber}.")

            is DebugStop.Halted ->
                printState("Program halted.")

            is DebugStop.VmError ->
                printState("VM error: ${stop.error.message}")

            is DebugStop.Stepped ->
                error("Run cannot stop after a single step without a reason")
        }
    }

    private fun stepOnce() {
        when (val stop = session.step()) {
            is DebugStop.Stepped ->
                printState("Stepped.")

            is DebugStop.Halted ->
                printState("Program halted.")

            is DebugStop.VmError ->
                printState("VM error: ${stop.error.message}")

            is DebugStop.BreakpointHit ->
                error("Step cannot stop before executing a breakpoint")
        }
    }

    private fun printState(message: String) {
        val debugSnapshot = session.snapshot()
        val snapshot = debugSnapshot.vm

        output(message)
        output(
            "IP=${snapshot.instructionPointer} SP=${snapshot.stackPointer} " +
                    "Status=${if (snapshot.running) "RUNNING" else "HALTED"} " +
                    "Flags: Z=${bit(snapshot.zeroFlag)} S=${bit(snapshot.signFlag)} " +
                    "C=${bit(snapshot.carryFlag)} O=${bit(snapshot.overflowFlag)}"
        )

        val location = debugSnapshot.nextLocation

        when {
            !snapshot.running ->
                output("Next: <halted>")

            location != null ->
                output("Next: line ${location.lineNumber}: ${location.source.trim()}")

            else ->
                output("Next: no source location for address ${snapshot.instructionPointer}")
        }

        output(
            snapshot.registers
                .mapIndexed { register, value -> "R$register=$value" }
                .joinToString(prefix = "Registers: ", separator = " ")
        )
        output(
            snapshot.addressRegisters
                .mapIndexed { register, value -> "A$register=${formatAddressValue(value)}" }
                .joinToString(prefix = "Address registers: ", separator = " ")
        )
        output(formatStack(snapshot))
        output(formatMemory(snapshot))
    }

    private fun formatStack(snapshot: VmSnapshot): String {
        if (snapshot.stackPointer == snapshot.memory.size) {
            return "Stack: empty"
        }

        val cells = (snapshot.stackPointer until snapshot.memory.size)
            .joinToString(separator = " ") { address ->
                "${formatAddress(address)}=${snapshot.memory[address]}"
            }

        return "Stack (top first): $cells"
    }

    private fun formatMemory(snapshot: VmSnapshot): String {
        val dataCells = snapshot.memory.indices
            .filter { address -> address < snapshot.stackPointer && snapshot.memory[address] != 0 }

        if (dataCells.isEmpty()) {
            return "Memory (non-zero outside stack): empty"
        }

        val cells = dataCells.joinToString(separator = " ") { address ->
            "${formatAddress(address)}=${snapshot.memory[address]}"
        }

        return "Memory (non-zero outside stack): $cells"
    }

    private fun printBreakpoints() {
        val lineBreakpoints = session.breakpoints()

        if (lineBreakpoints.isEmpty()) {
            output("Breakpoints: none")
            return
        }

        val breakpoints = lineBreakpoints
            .joinToString(separator = " ") { breakpoint ->
                "line ${breakpoint.lineNumber} @ ${breakpoint.address}"
            }

        output("Breakpoints: $breakpoints")
    }

    private fun printHelp() {
        output("br <line>     Set a breakpoint on an executable source line.")
        output("run           Start or continue until a breakpoint or HALT.")
        output("step          Execute one source instruction.")
        output("state         Print registers, stack and non-zero memory cells.")
        output("breakpoints   List current breakpoints.")
        output("quit          Leave the debugger.")
    }

    private fun bit(value: Boolean): Int =
        if (value) 1 else 0

    private fun formatAddress(address: Int): String =
        "[${formatAddressValue(address)}]"

    private fun formatAddressValue(address: Int): String =
        "0x%04X".format(address)
}
