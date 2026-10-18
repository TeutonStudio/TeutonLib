# TeutonLib 1.2.0

Release für Minecraft **1.21.1**, NeoForge **21.1.234 oder neuer innerhalb der 21.1-Linie**, Java **21** und KotlinForForge **5.11.0 bis kleiner 6**. TeutonLib wird auf Server und Clients installiert.

## Änderungen gegenüber 1.1.0

- Gemeinsame aufklappbare Quellen- und Kanalansichten für native CC-Computer und Addon-Workspaces mit API-Fenster und Rückkehr zur bisherigen Liste.
- Quellen-API mit Lua-Methoden, Signaturen und Dokumentation; Provider können Dokumentation und bereits vorhandene Addon-Peripherie ergänzen, ohne Geräte erneut anzuhängen.
- Separate Anzeige von Kabel-, Funk- und Endermodems mit Adresse, Anschluss, Dimension und verfügbaren Reichweiteninformationen. Die Anzeige führt keine Gerätemethoden aus und sendet keine Erkennungspakete.
- Kategorie **Redstone · intern** mit den sechs normalen CC-Redstone-Eingängen und ihren Analogwerten sowie vorhandenen benutzerdefinierten Ausgängen, auch in Addon-Workspaces.
- Gemeinsamer Aktionsbereich für Kanäle und aktualisierte Kotlin-Integrationsvorlage sowie deutsche und englische Dokumentation.
- Spectators dürfen Workspace-Snapshots lesen; Kanaländerungen bleiben gesperrt.

Die bisherigen öffentlichen Schnittstellen bleiben erhalten. Zusätzliche API-Schnittstellen werden mit dieser Minor-Version eingeführt. Die gemeinsame Quellenanzeige benötigt eine passende Workspace-Integration des jeweiligen Addons. Pocket Computer und Turtles erhalten keine zusätzlichen UI-Reiter.

## Download und Installation

- `teutonlib-1.2.0.jar`: Mod für den `mods`-Ordner auf Client und Server.
- `teutonlib-1.2.0-sources.jar`: Kotlin-Quellen für Entwickler.
- `teutonlib-1.2.0-computer-ui-template.zip`: Mod, Quellen, Dokumentation, Lizenzen und kopierbare Kotlin-Integrationsbeispiele.
- `SHA256SUMS`: SHA-256-Prüfsummen dieser drei Dateien.

Downloads: [GitHub Releases](https://github.com/TeutonStudio/TeutonLib/releases/tag/v1.2.0).

Die separate Create Sable Optimization-Mod mit Mod-ID `cso` muss entfernt werden. Fremdmods werden nicht eingebettet. Optionale Integrationen und Voraussetzungen stehen in der [README](../README.md).

Die lokale Maven-Koordinate lautet `de.TeutonStudio.TeutonLib:teutonlib:1.2.0`. Ein öffentliches Maven-Repository wird nicht bereitgestellt. Siehe [Integrationsvorlage](../examples/computer-ui/README.md) und [Quellen und Kanäle](computer-channels.md).

## Prüfung vom 18. Oktober 2026

- Sauberer Release-Build einschließlich Kotlin-Integrationsvorlage und Kompilierung beider Entwicklungstestmods: bestanden.
- 40 JUnit-Tests ohne Fehler oder übersprungene Tests, einschließlich Quellen-API, Modemdetails und Redstone-Quellen: bestanden.
- Dedicated-Server-Prüfung mit CC:Tweaked und Create für normale und erweiterte Computer: bestanden. Enthält Modemarten, Inventar-API, Quellenansicht, Kanalaktionen, Dimensionsgrenzen, Verbindungswechsel, Chunk-Entladung und Weltneustart.
- Release-Archive: Metadaten, Lizenzen, Kotlin-Quellen, Ausschluss fremder JARs und Testmods sowie Übereinstimmung mit den kompilierten Klassen geprüft; SHA-256-Prüfsummen erzeugt.
- Eine erneute vollständige grafische Clientprüfung und die vollständige optionale Modmatrix wurden für dieses Release nicht ausgeführt.
