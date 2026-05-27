package de.ljunker.kasm

import java.nio.file.Path

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

        return setBreakpointAtAddress(address, lineNumber)
    }

    fun setBreakpoint(sourcePath: Path, lineNumber: Int): LineBreakpoint? {
        val address = debugProgram.sourceMap.addressForLocation(sourcePath, lineNumber)
            ?: return null

        return setBreakpointAtAddress(address, lineNumber)
    }

    fun removeBreakpoint(lineNumber: Int): Boolean {
        val address = debugProgram.sourceMap.addressForLine(lineNumber)
            ?: return false

        return breakpointsByAddress.remove(address) != null
    }

    fun removeBreakpoint(sourcePath: Path, lineNumber: Int): Boolean {
        val address = debugProgram.sourceMap.addressForLocation(sourcePath, lineNumber)
            ?: return false

        return breakpointsByAddress.remove(address) != null
    }

    fun breakpoints(): List<LineBreakpoint> =
        breakpointsByAddress.values.sortedWith(
            compareBy<LineBreakpoint> { it.sourcePath?.toString().orEmpty() }
                .thenBy(LineBreakpoint::lineNumber)
        )

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

    private fun setBreakpointAtAddress(address: Int, lineNumber: Int): LineBreakpoint {
        val breakpoint = LineBreakpoint(
            lineNumber = lineNumber,
            address = address,
            sourcePath = debugProgram.sourceMap.locationForAddress(address)?.sourcePath
        )

        breakpointsByAddress[address] = breakpoint

        return breakpoint
    }
}

data class LineBreakpoint(
    val lineNumber: Int,
    val address: Int,
    val sourcePath: Path? = null
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
