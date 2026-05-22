package de.ljunker.kasm

class DebugSession(
    private val debugProgram: DebugProgram,
    output: (String) -> Unit = {}
) {
    private val vm = VirtualMachine(output)
    private val breakpointsByAddress = mutableMapOf<Int, LineBreakpoint>()

    private var stoppedAtBreakpoint = false

    init {
        vm.load(debugProgram.program)
    }

    fun setBreakpoint(lineNumber: Int): LineBreakpoint? {
        val address = debugProgram.sourceMap.addressForLine(lineNumber)
            ?: return null
        val breakpoint = LineBreakpoint(
            lineNumber = lineNumber,
            address = address
        )

        breakpointsByAddress[address] = breakpoint

        return breakpoint
    }

    fun removeBreakpoint(lineNumber: Int): Boolean {
        val address = debugProgram.sourceMap.addressForLine(lineNumber)
            ?: return false

        return breakpointsByAddress.remove(address) != null
    }

    fun breakpoints(): List<LineBreakpoint> =
        breakpointsByAddress.values.sortedBy(LineBreakpoint::lineNumber)

    fun snapshot(): DebugSnapshot =
        DebugSnapshot(
            vm = vm.snapshot(),
            nextLocation = debugProgram.sourceMap.locationForAddress(vm.instructionPointer)
        )

    fun run(): DebugStop {
        if (!vm.isRunning) {
            return DebugStop.Halted(snapshot())
        }

        if (stoppedAtBreakpoint) {
            stoppedAtBreakpoint = false

            stepVm()?.let { stop ->
                return stop
            }
        }

        while (vm.isRunning) {
            val breakpoint = breakpointsByAddress[vm.instructionPointer]

            if (breakpoint != null) {
                stoppedAtBreakpoint = true
                return DebugStop.BreakpointHit(
                    breakpoint = breakpoint,
                    snapshot = snapshot()
                )
            }

            stepVm()?.let { stop ->
                return stop
            }
        }

        return DebugStop.Halted(snapshot())
    }

    fun step(): DebugStop {
        if (!vm.isRunning) {
            return DebugStop.Halted(snapshot())
        }

        stoppedAtBreakpoint = false

        stepVm()?.let { stop ->
            return stop
        }

        return if (vm.isRunning) {
            DebugStop.Stepped(snapshot())
        } else {
            DebugStop.Halted(snapshot())
        }
    }

    private fun stepVm(): DebugStop? =
        try {
            vm.step()

            if (vm.isRunning) {
                null
            } else {
                DebugStop.Halted(snapshot())
            }
        } catch (error: VmException) {
            DebugStop.VmError(
                error = error,
                snapshot = snapshot()
            )
        }
}

data class LineBreakpoint(
    val lineNumber: Int,
    val address: Int
)

data class DebugSnapshot(
    val vm: VmSnapshot,
    val nextLocation: SourceLocation?
)

sealed interface DebugStop {
    val snapshot: DebugSnapshot

    data class BreakpointHit(
        val breakpoint: LineBreakpoint,
        override val snapshot: DebugSnapshot
    ) : DebugStop

    data class Stepped(
        override val snapshot: DebugSnapshot
    ) : DebugStop

    data class Halted(
        override val snapshot: DebugSnapshot
    ) : DebugStop

    data class VmError(
        val error: VmException,
        override val snapshot: DebugSnapshot
    ) : DebugStop
}
