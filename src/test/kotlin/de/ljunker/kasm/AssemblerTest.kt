package de.ljunker.kasm

import kotlin.io.path.createTempDirectory
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AssemblerTest {

    @Test
    fun assemblesImmediateAndRegisterMoves() {
        val program = Assembler().assemble(
            """
            MOV R0, 10
            MOV R1, R0
            INC R1
            DEC R0
            """.trimIndent()
        )

        assertEquals(
            Program.of(
                0x01, 0, 10,
                0x07, 1, 0,
                0x09, 1,
                0x0A, 0
            ),
            program
        )
    }

    @Test
    fun resolvesNonZeroJumpLabels() {
        val program = Assembler().assemble(
            """
            start:
            JNZ R0, start
            HALT
            """.trimIndent()
        )

        assertEquals(
            Program.of(
                0x08, 0, 0,
                0,
                0xFF
            ),
            program
        )
    }

    @Test
    fun assemblesCompareMemoryAndStackInstructions() {
        val program = Assembler().assemble(
            """
            CMP R0, R1
            JE done
            LOAD R2, [20]
            STORE [21], R2
            PUSH R2
            POP R3
            CALL done
            done:
            RET
            """.trimIndent()
        )

        assertEquals(
            Program.of(
                0x0B, 0, 1,
                0x0C, 21, 0,
                0x10, 2, 20, 0,
                0x11, 21, 0, 2,
                0x12, 2,
                0x13, 3,
                0x14, 21, 0,
                0x15
            ),
            program
        )
    }

    @Test
    fun assemblesIndexedMemoryAddresses() {
        val program = Assembler().assemble(
            """
            MOV R1, 1
            LOAD R0, [40 + R1]
            STORE [R1 + 60], R0
            LOAD R2, [R3]
            HALT
            """.trimIndent()
        )

        assertEquals(
            Program.of(
                Opcode.MOV.code, 1, 1,
                Opcode.LOAD_INDEXED.code, 0, 40, 0, 1,
                Opcode.STORE_INDEXED.code, 60, 0, 1, 0,
                Opcode.LOAD_INDEXED.code, 2, 0, 0, 3,
                Opcode.HALT.code
            ),
            program
        )
    }

    @Test
    fun assemblesWideMemoryAndJumpAddresses() {
        val program = Assembler().assemble(
            """
            .org 0x1234
            item:
              .byte 7

              LOAD R0, [item]
              STORE [item + 1], R0
              JMP done
            done:
              HALT
            """.trimIndent()
        )

        assertEquals(
            Program(
                bytes = listOf(
                    Opcode.LOAD.code, 0, 0x34, 0x12,
                    Opcode.STORE.code, 0x35, 0x12, 0,
                    Opcode.JMP.code, 11, 0,
                    Opcode.HALT.code
                ),
                initialMemory = mapOf(0x1234 to 7)
            ),
            program
        )
    }

    @Test
    fun assemblesAddressRegisterInstructions() {
        val program = Assembler().assemble(
            """
            MOVA A0, 0x1234
            MOVA A1, A0
            LOAD R0, [A1]
            STORE [A0], R0
            INCA A0
            DECA A1
            PRINTC R0
            HALT
            """.trimIndent()
        )

        assertEquals(
            Program.of(
                Opcode.MOVA.code, 0, 0x34, 0x12,
                Opcode.MOVA_REGISTER.code, 1, 0,
                Opcode.LOAD_ADDRESS_REGISTER.code, 0, 1,
                Opcode.STORE_ADDRESS_REGISTER.code, 0, 0,
                Opcode.INCA.code, 0,
                Opcode.DECA.code, 1,
                Opcode.PRINTC.code, 0,
                Opcode.HALT.code
            ),
            program
        )
    }

    @Test
    fun requiresMemoryAddressBrackets() {
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble("LOAD R0, 10")
        }

        assertEquals("Line 1: memory address '10' must use [value]", exception.message)
    }

    @Test
    fun rejectsOutOfRangeMemoryAddresses() {
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble("STORE [70000], R0")
        }

        assertEquals(
            "Line 1: address '70000' resolves to 70000, but only 0..65535 is allowed",
            exception.message
        )
    }

    @Test
    fun rejectsMissingOperands() {
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble("ADD R0")
        }

        assertEquals("Line 1: ADD expects 2 argument(s), got 1", exception.message)
    }

    @Test
    fun rejectsExtraOperands() {
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble("HALT R0")
        }

        assertEquals("Line 1: HALT expects 0 argument(s), got 1", exception.message)
    }

    @Test
    fun mapsExecutableLinesToBytecodeAddresses() {
        val debugProgram = Assembler().assembleWithDebugInfo(
            """
            MOV R0, 2
            loop:
            PRINT R0
            HALT
            """.trimIndent()
        )

        assertEquals(0, debugProgram.sourceMap.addressForLine(1))
        assertEquals(null, debugProgram.sourceMap.addressForLine(2))
        assertEquals(3, debugProgram.sourceMap.addressForLine(3))
        assertEquals("PRINT R0", debugProgram.sourceMap.locationForAddress(3)?.source?.trim())
        assertEquals(null, debugProgram.sourceMap.locationForAddress(3)?.sourcePath)
    }

    @Test
    fun mapsExecutableLocationsToIncludedSourceFiles() {
        val baseDirectory = createTempDirectory("kasm-debug-include")
        baseDirectory.resolve("lib").createDirectories()
        val includePath = baseDirectory.resolve("lib/foo.kasm")
        val mainPath = baseDirectory.resolve("main.kasm")
        includePath.writeText(
            """
            included_add:
              ADD R0, R1
              RET
            """.trimIndent()
        )
        mainPath.writeText(
            """
            MOV R0, 1
            .include "lib/foo.kasm"
            HALT
            """.trimIndent()
        )

        val debugProgram = Assembler().assembleFileWithDebugInfo(mainPath)
        val normalizedMainPath = mainPath.toAbsolutePath().normalize()
        val normalizedIncludePath = includePath.toAbsolutePath().normalize()
        val includeLocation = debugProgram.sourceMap.locationForAddress(3)

        assertEquals(normalizedMainPath, debugProgram.sourceMap.primarySourcePath)
        assertEquals(normalizedIncludePath, includeLocation?.sourcePath)
        assertEquals(2, includeLocation?.lineNumber)
        assertEquals("ADD R0, R1", includeLocation?.source?.trim())
        assertEquals(3, debugProgram.sourceMap.addressForLocation(includePath, 2))
        assertEquals(null, debugProgram.sourceMap.addressForLine(2))
        assertEquals(7, debugProgram.sourceMap.addressForLine(3))
    }

    @Test
    fun rejectsOutOfRangeRegisterMoveSources() {
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble("MOV R0, R4")
        }

        assertEquals("Line 1: register 'R4' is out of range", exception.message)
    }

    @Test
    fun initializesNamedDataWithConstantsAndExpressions() {
        val program = Assembler().assemble(
            """
            .equ BASE, 40

            .org BASE
            first:
              .byte 4, 9
            first_end:
            title:
              .ascii "OK"
            marker:
              .string "!"

              LOAD R0, [first + 1]
              MOV R1, first_end - first
              HALT
            """.trimIndent()
        )

        assertEquals(
            Program.of(
                Opcode.LOAD.code, 0, 41, 0,
                Opcode.MOV.code, 1, 2,
                Opcode.HALT.code
            ).bytes,
            program.bytes
        )
        assertEquals(
            mapOf(
                40 to 4,
                41 to 9,
                42 to 'O'.code,
                43 to 'K'.code,
                44 to '!'.code,
                45 to 0
            ),
            program.initialMemory
        )
    }

    @Test
    fun initializesNum64DataAsLittleEndianBytes() {
        val program = Assembler().assemble(
            """
            .org 0x2000
            literal:
              .num64 0x0102030405060708
            literal_end:
            decimal:
              .num64 655361234

              MOV R0, literal_end - literal
              HALT
            """.trimIndent()
        )

        assertEquals(
            Program.of(
                Opcode.MOV.code, 0, 8,
                Opcode.HALT.code
            ).bytes,
            program.bytes
        )
        assertEquals(
            mapOf(
                0x2000 to 0x08,
                0x2001 to 0x07,
                0x2002 to 0x06,
                0x2003 to 0x05,
                0x2004 to 0x04,
                0x2005 to 0x03,
                0x2006 to 0x02,
                0x2007 to 0x01,
                0x2008 to 0xD2,
                0x2009 to 0x04,
                0x200A to 0x10,
                0x200B to 0x27,
                0x200C to 0,
                0x200D to 0,
                0x200E to 0,
                0x200F to 0
            ),
            program.initialMemory
        )
    }

    @Test
    fun rejectsNum64FilePathArguments() {
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble(".num64 \"data/value.txt\"")
        }

        assertEquals(
            "Line 1: .num64 expects a numeric expression, not a file path",
            exception.message
        )
    }

    @Test
    fun assemblesExplicitStackParameterInstructions() {
        val program = Assembler().assemble(
            """
            MOVA A0, buffer
            PUSHA A0
            PUSHA buffer
            PUSHI 3
            CALL target
            DROP 5
            target:
              PEEKA A1, 2
              PEEK R0, R1
              DROP R0
              HALT

            .org 0x1200
            buffer:
              .byte 0
            """.trimIndent()
        )

        assertEquals(
            Program.of(
                Opcode.MOVA.code, 0, 0x00, 0x12,
                Opcode.PUSHA_REGISTER.code, 0,
                Opcode.PUSHA.code, 0x00, 0x12,
                Opcode.PUSHI.code, 3,
                Opcode.CALL.code, 16, 0,
                Opcode.DROP.code, 5,
                Opcode.PEEKA.code, 1, 2,
                Opcode.PEEK_REGISTER_OFFSET.code, 0, 1,
                Opcode.DROP_REGISTER.code, 0,
                Opcode.HALT.code
            ).bytes,
            program.bytes
        )
    }

    @Test
    fun embedsBinaryFilesRelativeToTheAssemblerBaseDirectory() {
        val baseDirectory = createTempDirectory("kasm-incbin")
        baseDirectory.resolve("blob.bin").writeBytes(byteArrayOf(0, 65, -1))
        val program = Assembler(baseDirectory = baseDirectory).assemble(
            """
            .org 0x1234
            blob:
              .incbin "blob.bin"
            blob_end:
              .byte 0

              LOAD R0, [blob + 2]
              MOV R1, blob_end - blob
              HALT
            """.trimIndent()
        )

        assertEquals(
            Program.of(
                Opcode.LOAD.code, 0, 0x36, 0x12,
                Opcode.MOV.code, 1, 3,
                Opcode.HALT.code
            ).bytes,
            program.bytes
        )
        assertEquals(
            mapOf(
                0x1234 to 0,
                0x1235 to 65,
                0x1236 to 255,
                0x1237 to 0
            ),
            program.initialMemory
        )
    }

    @Test
    fun assemblesFileSourcesAndFileInstructions() {
        val baseDirectory = createTempDirectory("kasm-file-source")
        val program = Assembler(baseDirectory = baseDirectory).assemble(
            """
            .file input, "input.bin"

              FREAD R0, input
              JNC after_rewind
              FREWIND input
            after_rewind:
              HALT
            """.trimIndent()
        )

        assertEquals(
            Program(
                bytes = listOf(
                    Opcode.FREAD.code, 0, 0,
                    Opcode.JNC.code, 8, 0,
                    Opcode.FREWIND.code, 0,
                    Opcode.HALT.code
                ),
                fileResources = listOf(
                    ProgramFile(
                        id = 0,
                        name = "input",
                        path = baseDirectory.resolve("input.bin").toAbsolutePath().normalize()
                    )
                )
            ),
            program
        )
    }

    @Test
    fun resolvesFileSourcesRelativeToIncludedFiles() {
        val baseDirectory = createTempDirectory("kasm-include-file-source")
        baseDirectory.resolve("lib").createDirectories()
        baseDirectory.resolve("lib/input.kasm").writeText(
            """
            .file included_input, "blob.bin"
            """.trimIndent()
        )
        val program = Assembler(baseDirectory = baseDirectory).assemble(
            """
            .include "lib/input.kasm"
            FREAD R0, included_input
            HALT
            """.trimIndent()
        )

        assertEquals(
            listOf(
                ProgramFile(
                    id = 0,
                    name = "included_input",
                    path = baseDirectory.resolve("lib/blob.bin").toAbsolutePath().normalize()
                )
            ),
            program.fileResources
        )
        assertEquals(
            Program.of(
                Opcode.FREAD.code, 0, 0,
                Opcode.HALT.code
            ).bytes,
            program.bytes
        )
    }

    @Test
    fun rejectsUnknownFileSources() {
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble("FREAD R0, input")
        }

        assertEquals("Line 1: unknown file source 'input'", exception.message)
    }

    @Test
    fun rejectsNonFileSymbolsAsFileSources() {
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble(
                """
                input:
                  .byte 0
                  FREAD R0, input
                """.trimIndent()
            )
        }

        assertEquals("Line 3: symbol 'input' is not a file source", exception.message)
    }

    @Test
    fun rejectsFileSourcesThatDuplicateOtherSymbols() {
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble(
                """
                .equ input, 1
                .file input, "input.bin"
                """.trimIndent()
            )
        }

        assertEquals("Line 2: duplicate symbol 'input'", exception.message)
    }

    @Test
    fun rejectsMoreThanTwoHundredFiftySixFileSources() {
        val source = (0..256).joinToString(separator = "\n") { index ->
            ".file input$index, \"input$index.bin\""
        }
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble(source)
        }

        assertEquals("Line 257: too many file sources; max 256", exception.message)
    }

    @Test
    fun includesSourcesRelativeToTheCurrentSourceDirectory() {
        val baseDirectory = createTempDirectory("kasm-include")
        baseDirectory.resolve("lib").createDirectories()
        baseDirectory.resolve("lib/u64.kasm").writeText(
            """
            .equ INCLUDED_VALUE, 7
            included_add:
              ADD R0, R1
              RET
            """.trimIndent()
        )

        val program = Assembler(baseDirectory = baseDirectory).assemble(
            """
            MOV R0, INCLUDED_VALUE
            MOV R1, 5
            CALL included_add
            HALT

            .include "lib/u64.kasm"
            """.trimIndent()
        )

        assertEquals(
            Program.of(
                Opcode.MOV.code, 0, 7,
                Opcode.MOV.code, 1, 5,
                Opcode.CALL.code, 10, 0,
                Opcode.HALT.code,
                Opcode.ADD.code, 0, 1,
                Opcode.RET.code
            ).bytes,
            program.bytes
        )
    }

    @Test
    fun mapsNestedIncludesRelativeToEachIncludedFile() {
        val baseDirectory = createTempDirectory("kasm-nested-debug-include")
        baseDirectory.resolve("lib/math/nested").createDirectories()
        val mainPath = baseDirectory.resolve("main.kasm")
        val includePath = baseDirectory.resolve("lib/math/foo.kasm")
        val nestedPath = baseDirectory.resolve("lib/math/nested/bar.kasm")
        includePath.writeText(
            """
            .include "nested/bar.kasm"
            RET
            """.trimIndent()
        )
        nestedPath.writeText(
            """
            nested_add:
              ADD R0, R1
            """.trimIndent()
        )
        mainPath.writeText(
            """
            MOV R0, 1
            .include "lib/math/foo.kasm"
            HALT
            """.trimIndent()
        )

        val sourceMap = Assembler().assembleFileWithDebugInfo(mainPath).sourceMap

        assertEquals(3, sourceMap.addressForLocation(nestedPath, 2))
        assertEquals(6, sourceMap.addressForLocation(includePath, 2))
        assertEquals(nestedPath.toAbsolutePath().normalize(), sourceMap.locationForAddress(3)?.sourcePath)
        assertEquals(includePath.toAbsolutePath().normalize(), sourceMap.locationForAddress(6)?.sourcePath)
    }

    @Test
    fun rejectsRecursiveIncludesWhenAssemblingAFile() {
        val baseDirectory = createTempDirectory("kasm-recursive-include")
        val firstPath = baseDirectory.resolve("a.kasm")
        val secondPath = baseDirectory.resolve("b.kasm")
        firstPath.writeText(".include \"b.kasm\"")
        secondPath.writeText(".include \"a.kasm\"")

        val exception = assertFailsWith<AssemblyException> {
            Assembler().assembleFileWithDebugInfo(firstPath)
        }

        assertEquals("Line 1: recursive include 'a.kasm'", exception.message)
    }

    @Test
    fun resolvesIncbinRelativeToIncludedFiles() {
        val baseDirectory = createTempDirectory("kasm-include-incbin")
        baseDirectory.resolve("lib").createDirectories()
        baseDirectory.resolve("lib/blob.bin").writeBytes(byteArrayOf(1, 2, 3))
        baseDirectory.resolve("lib/data.kasm").writeText(
            """
            .org 0x2200
            blob:
              .incbin "blob.bin"
            """.trimIndent()
        )

        val program = Assembler(baseDirectory = baseDirectory).assemble(
            """
            .include "lib/data.kasm"
            LOAD R0, [blob + 1]
            HALT
            """.trimIndent()
        )

        assertEquals(
            Program.of(
                Opcode.LOAD.code, 0, 0x01, 0x22,
                Opcode.HALT.code
            ).bytes,
            program.bytes
        )
        assertEquals(
            mapOf(
                0x2200 to 1,
                0x2201 to 2,
                0x2202 to 3
            ),
            program.initialMemory
        )
    }

    @Test
    fun rejectsOverlappingDataInitializers() {
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble(
                """
                .org 12
                  .byte 1, 2
                .org 13
                  .byte 3
                HALT
                """.trimIndent()
            )
        }

        assertEquals(
            "Line 4: data memory address 13 is initialized more than once",
            exception.message
        )
    }

    @Test
    fun rejectsMemoryAddressesWithTwoIndexRegisters() {
        val exception = assertFailsWith<AssemblyException> {
            Assembler().assemble("LOAD R0, [R1 + R2]")
        }

        assertEquals(
            "Line 1: indexed memory address '[R1 + R2]' must use one register " +
                    "as [register] or [base + register]",
            exception.message
        )
    }
}
