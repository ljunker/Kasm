package de.ljunker.kasm

class Debugger(
    private val debugProgram: DebugProgram,
    private val sourceName: String,
    private val readCommand: () -> String?,
    private val output: (String) -> Unit = ::println
) {
    private val vm = VirtualMachine(output)
    private val breakpointsByAddress = mutableMapOf<Int, Int>()

    private var stoppedAtBreakpoint = false

    fun run() {
        vm.load(debugProgram.program)

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

        val address = debugProgram.sourceMap.addressForLine(lineNumber)

        if (address == null) {
            output("Line $lineNumber has no executable instruction.")
            return
        }

        breakpointsByAddress[address] = lineNumber
        output("Breakpoint set at line $lineNumber (address $address).")
    }

    private fun continueUntilBreakpoint() {
        if (!vm.isRunning) {
            printState("Program is halted.")
            return
        }

        if (stoppedAtBreakpoint) {
            stoppedAtBreakpoint = false

            if (!stepVm()) {
                return
            }
        }

        while (vm.isRunning) {
            val lineNumber = breakpointsByAddress[vm.instructionPointer]

            if (lineNumber != null) {
                stoppedAtBreakpoint = true
                printState("Breakpoint hit at line $lineNumber.")
                return
            }

            if (!stepVm()) {
                return
            }
        }

        printState("Program halted.")
    }

    private fun stepOnce() {
        if (!vm.isRunning) {
            printState("Program is halted.")
            return
        }

        stoppedAtBreakpoint = false

        if (!stepVm()) {
            return
        }

        if (vm.isRunning) {
            printState("Stepped.")
        } else {
            printState("Program halted.")
        }
    }

    private fun stepVm(): Boolean =
        try {
            vm.step()
            true
        } catch (error: VmException) {
            printState("VM error: ${error.message}")
            false
        }

    private fun printState(message: String) {
        val snapshot = vm.snapshot()

        output(message)
        output(
            "IP=${snapshot.instructionPointer} SP=${snapshot.stackPointer} " +
                    "Status=${if (snapshot.running) "RUNNING" else "HALTED"} " +
                    "Flags: Z=${bit(snapshot.zeroFlag)} S=${bit(snapshot.signFlag)}"
        )

        val location = debugProgram.sourceMap.locationForAddress(snapshot.instructionPointer)

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
        if (breakpointsByAddress.isEmpty()) {
            output("Breakpoints: none")
            return
        }

        val breakpoints = breakpointsByAddress.entries
            .sortedBy { (_, lineNumber) -> lineNumber }
            .joinToString(separator = " ") { (address, lineNumber) ->
                "line $lineNumber @ $address"
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
        "[%03d]".format(address)
}
