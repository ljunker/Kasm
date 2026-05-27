# KASM Ideas

## Aktueller Stand

KASM ist ein kleiner Assembler mit direkter Bytecode-VM. Der Umfang ist noch
ueberschaubar genug, dass neue Sprachfeatures direkt in Assembler, Opcode-Tabelle,
VM, Tests und Editor-Highlighting gemeinsam weiterentwickelt werden koennen.

### Sprache

Die Sprache kennt aktuell:

- Labels und `;`-Kommentare.
- Dezimale, hexadezimale und binaere Zahlenliterale.
- Konstanten, Datenlayout und kleine Ausdruecke:
  - `.equ`, `.org`, `.byte`, `.num64`, `.ascii`, `.string`, `.incbin`,
    `.include`
  - `end - start`
  - `[buffer + 1]`
- Vier allgemeine Register: `R0` bis `R3`.
- Register- und Immediate-Moves:
  - `MOV R0, 10`
  - `MOV R0, R1`
- 16-bit-Adressregister und Pointer:
  - `MOVA A0, buffer`
  - `MOVA A1, A0`
  - `INCA A0`
  - `DECA A0`
- Arithmetik:
  - `ADD`
  - `ADC`
  - `ADDI`
  - `SUB`
  - `SBC`
  - `SUBI`
  - `INC`
  - `DEC`
  - `MUL`
  - `DIV`
  - `MOD`
  - `NEG`
- Bit-Operationen:
  - `AND`
  - `OR`
  - `XOR`
  - `NOT`
- Kontrollfluss ueber Register:
  - `JMP`
  - `JZ`
  - `JNZ`
- Vergleiche und Flags:
  - `CMP R0, R1`
  - `JE`
  - `JNE`
  - `JG`
  - `JGE`
  - `JL`
  - `JLE`
- Direkten Daten-Memory-Zugriff:
  - `LOAD R0, [40]`
  - `STORE [40], R0`
  - `LOAD R0, [buffer]`
  - `LOAD R0, [A0]`
- Stack und Funktionsaufrufe:
  - `PUSH`
  - `PUSHI`
  - `PUSHA`
  - `POP`
  - `DROP`
  - `PEEK`
  - `PEEKA`
  - `PUSHF`
  - `POPF`
  - `CALL`
  - `RET`
- Ausgabe und Programmende:
  - `CLR`
  - `NOP`
  - `PRINT`
  - `PRINTC`
  - `HALT`

### VM-Modell

Der aktuelle VM-Stand trifft bereits ein paar Architekturentscheidungen:

- Bytecode-Operanden fuer Adressen und Sprungziele werden als 16-bit-Werte
  kodiert.
- Programmbilder passen in den 65536-Byte-Adressraum.
- Byte-Register und Daten-Memory-Zellen speichern 8-bit-Werte; Arithmetik
  wrappt modulo 256.
- `A0` und `A1` speichern 16-bit-Adressen fuer Pointer-Zugriffe.
- Daten-Memory hat 65536 8-bit-Zellen.
- Programm-Bytecode und Daten-Memory sind getrennt.
- Der Stack liegt im Daten-Memory und waechst von oben nach unten.
- `SP` bleibt interner VM-Zustand und wird im Debugger gezeigt, nicht als
  KASM-Register exponiert.
- `CALL` legt die Ruecksprungadresse auf denselben Stack wie `PUSH`.
- Funktionsparameter werden explizit vor `CALL` auf den Stack gelegt; der
  Callee liest sie mit `PEEK`/`PEEKA`, der Caller raeumt sie mit `DROP` auf.
- `PUSHA` legt 16-bit-Adressparameter als zwei Bytes auf den Stack und erlaubt
  variable viele Pointer-Parameter pro Funktion.
- `PUSHF` und `POPF` speichern Zero, Sign, Carry und Overflow als ein Byte auf
  dem Stack und machen Carry-Ketten in Schleifen nutzbar.
- `R0` ist in der dokumentierten Call-Konvention primaerer Eingabe- und
  Rueckgabewert; Callees sichern modifizierte `R1` bis `R3`.
- `CMP` veraendert keine Register, sondern setzt Zero-, Sign-, Carry- und
  Overflow-Flag.
- `ADD`, `ADC`, `SUB`, `SBC`, `INC` und `DEC` aktualisieren dieselben
  Ergebnis-Flags; `ADC` nutzt Carry als Carry-in, `SBC` als Borrow-in.
- `ADDI`, `SUBI`, `MUL`, `DIV`, `MOD`, `NEG` sowie die Bit-Operationen sind
  Teil des aktuellen Instruktionssatzes.
- `JE` und `JNE` lesen das Zero-Flag; `JG`, `JGE`, `JL` und `JLE` sind signed
  Spruenge mit Sign- und Overflow-Semantik.

### Beispiele

Die Beispiele im Repo zeigen jetzt verschiedene Teile der Sprache:

- `examples/countdown.kasm` zeigt `DEC` und `JNZ`.
- `examples/compare-max.kasm` zeigt `CMP`, Flags und `MOV` zwischen Registern.
- `examples/memory-swap.kasm` zeigt benannte initialisierte Memory-Zellen.
- `examples/memory-layout.kasm` zeigt `.equ`, `.org`, `.byte` und
  Adressausdruecke.
- `examples/memory-strings.kasm` zeigt `.string` und indizierte String-Iteration.
- `examples/ascii-print.kasm` zeigt `A0`, high memory und `PRINTC`.
- `examples/incbin-print.kasm` zeigt `.incbin` mit dateibasierten Rohbytes.
- `examples/wide-add64.kasm`, `examples/wide-sub64.kasm`,
  `examples/wide-incdec64.kasm` und `examples/wide-mul8x64.kasm` zeigen
  64-bit-Arithmetik mit 8-bit-Registern, `ADC` und `SBC`.
- `examples/num64-arithmetic.kasm` zeigt `.num64`, explizite Stack-Parameter,
  `.include` und die U64-Add/Sub/Mul/Div/Mod-Library.
- `examples/num64-parse-decimal-file.kasm` liest ASCII-Ziffern mit `.incbin`
  und der U64-Decimal-Library in einen `.num64`-Speicherbereich.
- `examples/num64-print-decimal.kasm` konvertiert eine binaere `.num64`
  per Library-Routine in Dezimal-ASCII und gibt sie mit `PRINTC` aus.
- `examples/num64-parse-print-decimal.kasm` zeigt den Roundtrip von
  `.incbin`-Text zu `.num64` und zurueck zu Dezimal-ASCII.
- `examples/num64-varargs.kasm` zeigt variable viele Stack-Parameter mit
  `PUSHI`, `PUSHA`, `PEEK` und `PEEKA`.
- `examples/aoc-2025-day1-sample.kasm` zeigt einen kleinen ASCII-Parser mit
  `CALL`/`RET` fuer Advent of Code 2025 Day 1.
- `examples/aoc-2025-day1-part2.kasm` loest Advent of Code 2025 Day 1 Part 2
  mit `.include`, `.num64`, Division durch 100 und Dezimal-Ausgabe.
- `examples/stack-calls.kasm` zeigt verschachtelte `CALL`/`RET` und gesicherte Register.

### CLI und Debugger

Die CLI kann Quellprogramme direkt ausfuehren und interaktiv debuggen:

- `kasm run program.kasm` assembliert und startet ein Programm.
- `kasm program.kasm` assembliert und startet ein Programm.
- `kasm debug program.kasm` startet den Debugger auf der Quelldatei.
- `br 5` setzt im Debugger einen Breakpoint auf eine ausfuehrbare Quellzeile.
- `run` startet oder setzt die Ausfuehrung bis zum naechsten Breakpoint fort.
- `step` fuehrt die naechste Quellinstruktion aus.
- `state` zeigt IP, Flags, Register, Stack und nichtleere Memory-Zellen.

Der Assembler erzeugt dafuer eine Source-Map zwischen Bytecode-Adressen und
Quellpositionen. `SourceLocation` enthaelt Zeile, Quelltext und optional den
konkreten Quelldateipfad; Datei-Assembly und Includes setzen diesen Pfad,
String-Assembly ohne echte Datei bleibt pfadlos. `SourceMap` kann Adressen
ueber Datei+Zeile aufloesen, waehrend die alten line-basierten APIs auf die
Hauptdatei beziehungsweise den Top-Level-String zeigen. Breakpoints auf reinen
Label-, Leer- oder Kommentarzeilen werden aktuell abgelehnt. `DebugSession`
kapselt Run/Step, line- und dateibewusste Source-Breakpoints, typed
Stop-Gruende und Snapshots headless; die CLI-REPL ist nur ein Adapter darueber.
Damit kann ein IntelliJ-Plugin die Debugger-Steuerung benutzen, ohne
Terminalausgabe oder Include-Pfade rekonstruieren zu muessen.

Der Entwicklungsstart ueber `./gradlew run` ist fuer Tests und Repo-Arbeit
praktisch, soll aber nicht der normale Nutzerweg bleiben. `installDist` erzeugt
eine lokale `kasm`-Distribution mit Startskript und Runtime-Libs. Fuer einen
Aufruf von ueberall soll die ganze Distribution an einem stabilen Ort liegen
und nur `bin/kasm` ueber den `PATH` sichtbar gemacht werden:

- `kasm run examples/countdown.kasm`
- `kasm debug examples/stack-calls.kasm`

### Absicherung und Referenz

Die aktuelle Basis ist durch normale Unit Tests und Golden Tests abgesichert:

- Assembler-Fehler fuer ungueltige Memory-Operands sowie fehlende und zu viele
  Operanden sind abgedeckt.
- VM-Fehler fuer Stack Underflow, Stack Overflow und ungueltige Jump- oder
  Call-Ziele sind abgedeckt.
- Die Beispielprogramme haben stabile Hexdump- und Ausgabe-Erwartungen.
- `docs/language-reference.md` beschreibt Instruktionen, Operandentypen, Flags,
  Memory und Stack.

Die Opcode-Tabelle kennt Operandentypen statt nur Argumentzahlen. Das ist der
erste Schritt, damit Assembler, Diagnostik und spaeter Editor-Tooling auf
gemeinsame Instruktionsmetadaten aufbauen koennen.

## Empfohlene Umsetzungsreihenfolge

### Abgeschlossen: Bestehende Basis absichern

Der erste Absicherungsblock ist umgesetzt:

- Fehlerfalltests decken den aktuellen Assembler- und VM-Rand besser ab.
- Golden Tests bewachen Bytecode und Ausgabe der Beispiele.
- Die Sprachreferenz haelt den aktuellen Maschinenstand fest.
- Operandentypen liegen in der Opcode-Metadatenstruktur.

Neue Features sollten diese Tests und die Referenz jeweils erweitern, statt die
Absicherung spaeter nachzuholen.

### Abgeschlossen: Architektursemantik festziehen

Memory, Stack und Flags machen KASM jetzt zu einer kleinen Maschine und nicht
mehr nur zu einem Assembler-Spielzeug. Der Maschinenstand ist jetzt explizit:

- Byte-Register und Daten-Memory-Zellen sind auf 8-bit-Werte festgelegt.
- Programmadressen, Datenadressen und Address-Register sind auf 16-bit-Werte
  beziehungsweise 65536 Adressen festgelegt.
- Arithmetik wrappt; Zero, Sign, Carry und Overflow sind getestet und
  dokumentiert.
- `JG` und `JL` folgen signed Flag-Semantik an Byte-Grenzen.
- `SP` bleibt intern und debugger-sichtbar.
- Die dokumentierte Call-Konvention legt `R0` als primaeren Rueckgabewert und
  `R1` bis `R3` als callee-preserved Register fest.

Diese Regeln sind Grundlage fuer `MUL`, `DIV`, weitere Spruenge,
Assembler-Direktiven, Debugger und Disassembler.

### Abgeschlossen: 16-bit-Adressraum und ASCII-Ausgabe

Der Schritt weg von der reinen 255er-Denke ist umgesetzt, ohne die 8-bit-Daten
aufzugeben:

- Byte-Register `R0` bis `R3` bleiben 8-bit.
- Daten-Memory und Programmadressen haben jetzt 16-bit-Adressen.
- Sprungziele, `CALL`, direkte Memory-Adressen, indizierte Memory-Basen und
  `.org` werden als 16-bit-Adressen verarbeitet.
- `A0` und `A1` sind 16-bit-Adressregister fuer Pointer-Zugriffe.
- `LOAD R0, [A0]` und `STORE [A0], R0` greifen ueber Address-Register zu.
- `MOVA`, `INCA` und `DECA` bewegen und veraendern Address-Register.
- `CALL` legt 16-bit-Ruecksprungadressen als zwei Bytes auf den Stack.
- `PRINTC` gibt einen Byte-Wert als ASCII-Zeichen aus.
- `examples/ascii-print.kasm` und `examples/aoc-2025-day1-sample.kasm`
  demonstrieren High-Memory-Strings und Pointer-Iteration.

Programme koennen jetzt grosse Speicherbereiche adressieren; `.incbin` macht
externe Rohdaten als initialisiertes Daten-Memory nutzbar.

### Abgeschlossen: Memory im Assembler wirklich nutzbar machen

Direkte Adressen wie `[40]` reichen fuer erste Beispiele. Fuer laengere Programme
hat der Assembler jetzt Namen, Datenbereiche und kleine Ausdruecke:

- `.equ` definiert Konstanten.
- `.org` bewegt den Cursor fuer das initiale Daten-Memory-Bild.
- `.byte`, `.num64`, `.ascii` und `.string` initialisieren benannte Datenzellen.
- `.num64` schreibt unsigned 64-bit-Zahlen als acht little-endian Bytes.
- `.incbin` bettet externe Rohbytes relativ zur Quelldatei ein.
- Datenlabels werden als direkte Memory-Adressen benutzt, Code-Labels bleiben
  Bytecode-Adressen.
- Operanden und Datenwerte verstehen kleine `+`/`-`-Ausdruecke wie
  `end - start` und `[buffer + 1]`.
- `LOAD` und `STORE` koennen mit genau einem Laufzeit-Indexregister auf
  `[R1]`, `[buffer + R1]` und `[R1 + 4]` zugreifen.
- Ueberlappende Dateninitialisierer werden als Assembler-Fehler abgelehnt.

`.word` bleibt bewusst offen: In der aktuellen 8-bit-Architektur ist ein
gespeichertes Word genau eine Zelle, also deckt `.byte` den Bedarf sauberer ab.

### Abgeschlossen: Instruktionssatz gezielt erweitern

Neue Opcodes sollten dann zuerst Programme kuerzer oder klarer machen, die mit
dem aktuellen Kern bereits moeglich sind. Dieser Block ist umgesetzt:

- Immediate-Arithmetik: `ADDI`, `SUBI`.
- Carry-Arithmetik fuer Multi-Byte-Zahlen: `ADC`, `SBC`.
- Flag-Stack fuer Carry-Schleifen: `PUSHF`, `POPF`.
- Stack-Parameter und Stack-Inspektion: `PUSHI`, `PUSHA`, `DROP`, `PEEK`,
  `PEEKA`.
- Weitere Arithmetik: `MUL`, `DIV`, `MOD`, `NEG`.
- Bit-Operationen: `AND`, `OR`, `XOR`, `NOT`.
- Weitere signed Flag-Spruenge: `JGE`, `JLE`.
- Kleine Einfachheitsinstruktionen: `CLR`, `NOP`.
- `DIV` und `MOD` werfen bei Divisor `0` einen VM-Fehler.
- `MUL`, `NEG`, `DIV`, `MOD`, Bit-Operationen und `CLR` haben dokumentierte
  Flag-Semantik.

Unsigned Vergleichsspruenge auf Basis des Carry-Flags bleiben eine moegliche
spaetere Ergaenzung, wenn Programme sie wirklich brauchen.

### Abgeschlossen: Datei-I/O per `.incbin` nutzbar machen

Der 16-bit-Adressraum macht groessere Eingaben sinnvoll, aber noch nicht
bequem. Die reproduzierbare Assembler-Variante ist umgesetzt:

- `.incbin "path"` liest Rohbytes beim Assemblieren ein.
- Relative Pfade werden im CLI relativ zur Quelldatei aufgeloest.
- Eingebettete Bytes landen im initialen Daten-Memory ab dem aktuellen
  `.org`-Cursor.
- `.incbin` haengt bewusst keinen Nullterminator an; Programme koennen danach
  explizit `.byte 0` setzen.
- Bereichsfehler werden ueber die bestehende Datenrange-Pruefung abgefangen.
- `examples/incbin-print.kasm` zeigt Datei-Daten plus Pointer-Iteration mit
  `A0` und `PRINTC`.

### Abgeschlossen: `.include` und U64-Libraries

Die langen `.num64`-Beispiele sind jetzt auf wiederverwendbare KASM-Libraries
aufgeteilt:

- `.include "path"` expandiert eine andere KASM-Datei an Ort und Stelle.
- Relative Include-Pfade werden relativ zu der Datei aufgeloest, die die
  Direktive enthaelt; verschachtelte Includes funktionieren entsprechend.
- `.incbin` in inkludierten Dateien wird ebenfalls relativ zur inkludierten
  Datei aufgeloest.
- Rekursive Include-Zyklen werden als Assembler-Fehler abgelehnt.
- Debug-Informationen behalten fuer Instruktionen aus Includes den konkreten
  Quelldateipfad und die Zeile der Include-Datei.
- `lib/u64-core.kasm` enthaelt Copy/Clear/Zero/Add/Sub/Byte-Ausgabe.
- `lib/u64-arithmetic.kasm` enthaelt einfache 64-bit-Mul/Div/Mod-Helfer und
  erwartet `lib/u64-core.kasm`.
- `lib/u64-decimal.kasm` enthaelt Dezimal-Parsing und Dezimal-Ausgabe fuer
  `.num64`-Werte und erwartet `lib/u64-core.kasm`.
- Library-Includes stehen in den Beispielen hinter `HALT`, weil inkludierte
  Instruktionen normaler Bytecode sind und sonst mit ausgefuehrt wuerden.

### 1. Direkten CLI-Workflow ausbauen

Der interaktive Source-Debugger ist bereits vorhanden. Sobald Programme groesser
werden, sollte zuerst der normale Aufruf ohne Gradle sauber werden und danach
das Bytecode- und Debugger-Tooling folgen.

- Gradle-Distribution und explizite CLI-Aufrufe sind vorbereitet:
  - `applicationName` ist `kasm`
  - `installDist` erzeugt Startskript und Runtime-Libs
  - `README.md` dokumentiert Distribution-Install und `PATH`
  - `kasm run file.kasm` und `kasm debug file.kasm` sind die expliziten Aufrufe
  - `kasm file.kasm` bleibt als Kurzform erhalten
- Usage- und Fehlerausgaben fuer Subcommands schaerfen:
  - fehlende Datei
  - unbekanntes Subcommand
  - Assembler- und Laufzeitfehler ohne Gradle-Rauschen

- CLI-Subcommands:
  - `kasm assemble file.kasm -o file.kbc`
  - `kasm check file.kasm`
  - `kasm dump file.kbc`
  - `kasm disasm file.kbc`
- Ein kleines Binaerformat:
  - Magic Header
  - Formatversion
  - Bytecode-Payload
  - spaeter optionale Debug-Informationen
- Disassembler:
  - Opcode und Operanden lesbar ausgeben
  - unbekannte Bytes robust anzeigen
- VM-Debug-Hooks:
  - Trace-Ausgabe
  - Schrittlimit gegen Endlosschleifen
- Debugger-Komfort und Editor-Anbindung:
  - Breakpoints wieder loeschen
  - Source-Listing um die aktuelle Zeile
  - Memory-Fenster statt nur nichtleerer Zellen
  - Reset oder Neustart einer Debug-Session
  - IntelliJ-Adapter fuer `DebugSession` mit Run/Step-Aktionen und Tool Window
- Source Maps weiter nutzen:
  - bessere Laufzeitfehler fuer Spruenge, Stack und Memory
  - spaeter optional im Bytecode-Format persistieren

### 2. Parser und Diagnostik verbessern

Der aktuelle Parser ist fuer die kleine Syntax bewusst direkt. Die neuen
Direktiven, Includes, Ausdruecke und Strings machen eine sauberere
Parser-Grenze jetzt nuetzlicher; Makros wuerden ohne sie schnell unhandlich.

- Lexer/Parser-Grenze sauberer machen.
- Operandentypen als Datenmodell einfuehren statt spaet Strings zu zerlegen.
- Fehlermeldungen verbessern:
  - Zeile und Spalte
  - Originalzeile
  - Caret-Markierung
  - erwarteter Operandentyp
- Mehrere Fehler in einem Assembler-Lauf sammeln, wo das sinnvoll ist.

### 3. Editor- und Sprachtooling nachziehen

Das TextMate-Highlighting ist ein guter Anfang. Sobald Syntax und Semantik
stabiler sind, lohnt sich reichhaltigeres Tooling.

- Highlighting aus Opcode- oder Sprachmetadaten ableiten.
- Snippets fuer Labels, Loops und Funktionen.
- Formatter fuer einheitliche Einrueckung und Operandenschreibweise.
- Completion und Hover-Dokumentation.
- Spaeter Diagnostics oder ein kleiner Language Server.

### 4. Groessere Sprachideen spaeter

Diese Ideen sind interessant, sollten aber nach den Grundlagen kommen:

- Makros.
- Optionaler Laufzeit-Dateiloader:
  - `kasm run program.kasm --load input.txt:0x2000`
  - dieselben Loader-Optionen fuer `kasm debug`
  - optional Nullterminator anhaengen fuer textbasierte Parser
- Pseudo-Instruktionen mit Expansion im Assembler.
- Weitere Flag-Kontrolle fuer generische Multi-Byte-Routinen, falls die
  Beispiele sie brauchen: `CLC`/`SEC` oder unsigned Vergleichsspruenge.
- Komplexere Adressierung mit mehr als einem Laufzeitregister, falls reale
  Beispiele sie brauchen.
- Bessere I/O:
  - `PRINTS`
  - eventuell einfache Input- oder Syscall-Instruktionen
- Mehr Beispiele oder kleine Standardroutinen:
  - Fibonacci
  - Schleifen ueber Memory
  - Stack-basierte Funktionen
  - einfache String-Ausgabe

## Leitlinie

KASM sollte in kleinen, nachvollziehbaren Maschinen-Schritten wachsen. Features,
die mehr Programme ermoeglichen, sind wichtiger als reine Syntaxabkuerzungen.
Vor allem Memory, Flags, Stack und Bytecode-Format sollten gemeinsam sauber
bleiben, weil sie fast jedes spaetere Tool beeinflussen.
