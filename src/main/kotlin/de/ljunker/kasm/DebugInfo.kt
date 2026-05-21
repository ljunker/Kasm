package de.ljunker.kasm

data class DebugProgram(
    val program: Program,
    val sourceMap: SourceMap
)

data class SourceLocation(
    val lineNumber: Int,
    val source: String
)

class SourceMap(
    locationsByAddress: Map<Int, SourceLocation>
) {
    private val locationsByAddress = locationsByAddress.toMap()
    private val addressesByLine = locationsByAddress.entries
        .associate { (address, location) -> location.lineNumber to address }

    fun locationForAddress(address: Int): SourceLocation? =
        locationsByAddress[address]

    fun addressForLine(lineNumber: Int): Int? =
        addressesByLine[lineNumber]

    fun executableLines(): Set<Int> =
        addressesByLine.keys
}
