# KASM Ideas

## Aktueller Stand

KASM ist aktuell ein kleiner Two-Pass-Assembler plus direkte VM.

- Der Assembler parst Labels, Mnemonics und Argumente.
- Die VM interpretiert Bytecode direkt.
- Opcodes sind fest als Enum definiert.
- Es gibt aktuell `MOV`, `ADD`, `SUB`, `JMP`, `JZ`, `PRINT` und `HALT`.
- Die Architektur nutzt 4 Register und 8-bit Operanden beziehungsweise Adressen.
- Es gibt ein Beispielprogramm unter `examples/countdown.kasm`.
- Die TextMate-Grammatik fuer `.kasm` existiert bereits, muss bei neuen Opcodes aber manuell gepflegt werden.

## Schnelle Fixes

- `./gradlew test` schlaegt aktuell fehl, weil Testquellen existieren, aber keine Tests entdeckt werden.
- `./gradlew run --args=examples/countdown.kasm` schlaegt fehl, weil `application.mainClass` auf `de.ljunker.kasm.Main`
  zeigt. Die top-level `main`-Funktion wird in Kotlin typischerweise als `de.ljunker.kasm.MainKt` kompiliert.
- Erste echte Tests sollten Assembler, VM-Ausgabe, Labels, Fehlerfaelle und Spruenge abdecken.

## Sprachfeatures

- Basis-Instruktionen:
    - `NOP`
    - `INC`
    - `DEC`
    - `MUL`
    - `DIV`
    - `MOD`
    - `NEG`

- Register- und Immediate-Varianten:
    - `MOV R0, R1`
    - `ADDI R0, 5`
    - `SUBI R0, 1`

- Bessere Kontrollfluesse:
    - `JNZ`
    - `CMP`
    - Flags wie `ZF`, `SF`, `CF`
    - Spruenge wie `JE`, `JNE`, `JG`, `JL`

- Speicherzugriff:
    - `LOAD R0, [addr]`
    - `STORE [addr], R0`
    - spaeter auch `[R1]`, `[label]` oder `[R1 + 4]`

- Stack und Funktionen:
    - `PUSH`
    - `POP`
    - `CALL`
    - `RET`
    - dediziertes `SP`-Register

- Datenbereiche und Direktiven:
    - `.byte`
    - `.word`
    - `.string`
    - `.ascii`
    - `.org`
    - `.equ`
    - `.include`

- Konstanten und Ausdruecke:
    - `COUNT = 10`
    - `JMP start + 2`
    - `len = end - start`

- Zeichen und Strings:
    - `PRINTC R0`
    - `PRINTS message`

- Makros und Pseudo-Instruktionen:
    - `CLR R0` als `MOV R0, 0`
    - `DEC R0` als eigener Opcode oder als Expansion

## Interpreter- und VM-Features

- Architekturmodell zentralisieren:
    - Registerzahl
    - Word-Groesse
    - Speichergroesse
    - Operandentypen
    - Adressraum

- VM-State explizit modellieren:
    - Instruction Pointer
    - Register
    - Flags
    - Speicher
    - Status wie Running oder Halted

- Step-Modus:
    - `step()`
    - Breakpoints
    - Trace-Ausgabe pro Instruktion

- Endlosschleifen-Schutz:
    - optionales `maxSteps`
    - saubere Fehlermeldung bei Schrittlimit

- Disassembler:
    - Bytecode zurueck in ein KASM-nahes Listing wandeln
    - hilfreich fuer Tests, Debugging und Bytecode-Inspektion

- Binaerformat:
    - `kasm assemble file.kasm -o file.kbc`
    - `kasm run file.kbc`
    - Magic Header und Versionsfeld

- Source Maps:
    - Bytecode-Adresse zu Quellzeile
    - bessere VM-Fehler mit Bezug zur Originaldatei

- Bessere Diagnostik:
    - Zeile und Spalte
    - Originalzeile
    - Caret-Ausgabe
    - erwartete Operandentypen

- CLI-Subcommands:
    - `assemble`
    - `run`
    - `dump`
    - `disasm`
    - `check`
    - `debug`

## Tooling

- VS-Code-Erweiterung ausbauen:
    - Snippets
    - Hover-Dokumentation
    - Completion
    - Diagnostics

- Formatter:
    - Labels linksbuendig
    - Instruktionen eingerueckt
    - Operanden normalisiert

- Golden Tests fuer Beispielprogramme.
- Roundtrip-Tests: Assembler zu Bytecode zu Disassembler zu Bytecode.
- Kleine Standardbibliothek an Beispielen:
    - Countdown
    - Fibonacci
    - Multiplikation
    - Funktionen
    - Speicherzugriff

## Empfohlene Reihenfolge

1. CLI-Fix und echte Tests.
2. Kleine Instruktionen wie `JNZ`, `INC`, `DEC` und `MOV reg, reg`.
3. Flags und `CMP`.
4. Speicherzugriff.
5. Stack und Funktionen.
6. Disassembler, Debugger und Binaerformat.

Ab Speicher und Stack wird KASM deutlich interessanter. Vorher sollte aber die Basis mit Tests und einem
funktionierenden CLI abgesichert werden.
