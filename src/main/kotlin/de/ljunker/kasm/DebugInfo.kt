package de.ljunker.kasm

import java.nio.file.Path

data class DebugProgram(
    val program: Program,
    val sourceMap: SourceMap
)

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
