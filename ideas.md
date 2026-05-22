# KASM Ideas

## Aktueller Stand

KASM ist ein kleiner Assembler mit direkter Bytecode-VM. Der Umfang ist noch
ueberschaubar genug, dass neue Sprachfeatures direkt in Assembler, Opcode-Tabelle,
VM, Tests und Editor-Highlighting gemeinsam weiterentwickelt werden koennen.

### Sprache

Die Sprache kennt aktuell:

- Labels und `;`-Kommentare.
- Dezimale, hexadezimale und binaere Zahlenliterale.
- Vier allgemeine Register: `R0` bis `R3`.
- Register- und Immediate-Moves:
  - `MOV R0, 10`
  - `MOV R0, R1`
- Arithmetik:
  - `ADD`
  - `SUB`
  - `INC`
  - `DEC`
- Kontrollfluss ueber Register:
  - `JMP`
  - `JZ`
  - `JNZ`
- Vergleiche und Flags:
  - `CMP R0, R1`
  - `JE`
  - `JNE`
  - `JG`
  - `JL`
- Direkten Daten-Memory-Zugriff:
  - `LOAD R0, [40]`
  - `STORE [40], R0`
- Stack und Funktionsaufrufe:
  - `PUSH`
  - `POP`
  - `CALL`
  - `RET`
- Ausgabe und Programmende:
  - `PRINT`
  - `HALT`

### VM-Modell

Der aktuelle VM-Stand trifft bereits ein paar Architekturentscheidungen:

- Bytecode-Operanden und Sprungziele werden als Werte von `0..255` kodiert.
- Programmbilder passen in den 256-Byte-Adressraum.
- Register und Daten-Memory-Zellen speichern 8-bit-Werte; Arithmetik wrappt
  modulo 256.
- Daten-Memory hat 256 8-bit-Zellen.
- Programm-Bytecode und Daten-Memory sind getrennt.
- Der Stack liegt im Daten-Memory und waechst von oben nach unten.
- `SP` bleibt interner VM-Zustand und wird im Debugger gezeigt, nicht als
  KASM-Register exponiert.
- `CALL` legt die Ruecksprungadresse auf denselben Stack wie `PUSH`.
- `R0` ist in der dokumentierten Call-Konvention primaerer Eingabe- und
  Rueckgabewert; Callees sichern modifizierte `R1` bis `R3`.
- `CMP` veraendert keine Register, sondern setzt Zero-, Sign-, Carry- und
  Overflow-Flag.
- `ADD`, `SUB`, `INC` und `DEC` aktualisieren dieselben Ergebnis-Flags.
- `JE` und `JNE` lesen das Zero-Flag; `JG` und `JL` sind signed Spruenge mit
  Sign- und Overflow-Semantik.

### Beispiele

Die Beispiele im Repo zeigen jetzt verschiedene Teile der Sprache:

- `examples/countdown.kasm` zeigt `DEC` und `JNZ`.
- `examples/compare-max.kasm` zeigt `CMP`, Flags und `MOV` zwischen Registern.
- `examples/memory-swap.kasm` zeigt direkten Memory-Zugriff.
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

Der Assembler erzeugt dafuer bereits eine Source-Map zwischen Quellzeilen und
Bytecode-Adressen. Breakpoints auf reinen Label-, Leer- oder Kommentarzeilen
werden aktuell abgelehnt. `DebugSession` kapselt Run/Step, Source-Breakpoints,
typed Stop-Gruende und Snapshots headless; die CLI-REPL ist nur ein Adapter
darueber. Damit kann ein IntelliJ-Plugin die Debugger-Steuerung benutzen, ohne
Terminalausgabe parsen zu muessen.

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

- Register, Daten-Memory und Programmadressen sind auf 8-bit-Werte
  beziehungsweise 256 Adressen festgelegt.
- Arithmetik wrappt; Zero, Sign, Carry und Overflow sind getestet und
  dokumentiert.
- `JG` und `JL` folgen signed Flag-Semantik an Byte-Grenzen.
- `SP` bleibt intern und debugger-sichtbar.
- Die dokumentierte Call-Konvention legt `R0` als primaeren Rueckgabewert und
  `R1` bis `R3` als callee-preserved Register fest.

Diese Regeln sind Grundlage fuer `MUL`, `DIV`, weitere Spruenge,
Assembler-Direktiven, Debugger und Disassembler.

### 1. Memory im Assembler wirklich nutzbar machen

Direkte Adressen wie `[40]` reichen fuer erste Beispiele. Fuer laengere Programme
braucht Memory aber Namen, Datenbereiche und kleine Ausdruecke.

- Konstanten und Symbole:
  - `.equ COUNT, 10`
  - oder eine klare `COUNT = 10`-Syntax
- Daten-Direktiven:
  - `.byte`
  - `.word`, falls die Registerbreite das rechtfertigt
  - `.ascii`
  - `.string`
- Adresssteuerung:
  - `.org`
  - benannte Datenlabels
  - klare Trennung oder klare Verbindung von Code- und Datenadressen
- Ausdrucksauswertung:
  - `end - start`
  - `buffer + 1`
  - Konstanten in Operanden statt nur nackter Literale

Danach werden `LOAD` und `STORE` deutlich lesbarer als Programme mit vielen
hart kodierten Adressen.

### 2. Instruktionssatz gezielt erweitern

Neue Opcodes sollten dann zuerst Programme kuerzer oder klarer machen, die mit
dem aktuellen Kern bereits moeglich sind.

- Immediate-Arithmetik:
  - `ADDI R0, 5`
  - `SUBI R0, 1`
- Weitere Arithmetik:
  - `MUL`
  - `DIV`
  - `MOD`
  - `NEG`
- Bit-Operationen:
  - `AND`
  - `OR`
  - `XOR`
  - `NOT`
  - Shifts
- Weitere Flag-Spruenge:
  - `JGE`
  - `JLE`
  - unsigned Varianten auf Basis der bestehenden Carry-Semantik
- Kleine Pseudo-Instruktionen:
  - `CLR R0`
  - `NOP`

Bei `DIV` muss noch festgelegt werden, wie Division durch null und Quotienten
unter der bestehenden 8-bit-Semantik behandelt werden.

### 3. Direkten CLI-Workflow ausbauen

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

### 4. Parser und Diagnostik verbessern

Der aktuelle Parser ist fuer die kleine Syntax bewusst direkt. Direktiven,
Ausdruecke, Strings und Makros werden mit strukturierterem Parsing deutlich
leichter.

- Lexer/Parser-Grenze sauberer machen.
- Operandentypen als Datenmodell einfuehren statt spaet Strings zu zerlegen.
- Fehlermeldungen verbessern:
  - Zeile und Spalte
  - Originalzeile
  - Caret-Markierung
  - erwarteter Operandentyp
- Mehrere Fehler in einem Assembler-Lauf sammeln, wo das sinnvoll ist.

### 5. Editor- und Sprachtooling nachziehen

Das TextMate-Highlighting ist ein guter Anfang. Sobald Syntax und Semantik
stabiler sind, lohnt sich reichhaltigeres Tooling.

- Highlighting aus Opcode- oder Sprachmetadaten ableiten.
- Snippets fuer Labels, Loops und Funktionen.
- Formatter fuer einheitliche Einrueckung und Operandenschreibweise.
- Completion und Hover-Dokumentation.
- Spaeter Diagnostics oder ein kleiner Language Server.

### 6. Groessere Sprachideen spaeter

Diese Ideen sind interessant, sollten aber nach den Grundlagen kommen:

- Makros und `.include`.
- Pseudo-Instruktionen mit Expansion im Assembler.
- Indirekte Adressierung:
  - `[R1]`
  - `[R1 + 4]`
- Bessere I/O:
  - `PRINTC`
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
