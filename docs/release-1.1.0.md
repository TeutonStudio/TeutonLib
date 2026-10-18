# TeutonLib 1.1.0

Release für Minecraft **1.21.1**, NeoForge **21.1.234 oder neuer innerhalb der 21.1-Linie**, Java **21** und KotlinForForge **5.11.0 bis kleiner 6**. TeutonLib wird auf Server und Clients installiert.

## Änderungen gegenüber 1.0.0

- Gemeinsame Addon-Workspaces für Quellen und Kanäle mit autoritativem Addon-Backend; CC Aeroworks behält seine Kanalgruppen, Bindungen, DbW-Overrides und Speicherung.
- Clientseitige UI-Einstellung unter `[computerUi] enabled` in `config/teutonlib-client.toml`, einschließlich Aktualisierung bereits geöffneter nativer Computer und Beispielbildschirme.
- Vollständige Kotlin-Implementierung einschließlich Mixins, Tests und kopierbarer Integrationsbeispiele.
- Schutz vor Kanaländerungen durch Spectators.
- MIT-Lizenz für die eigene Bibliothek und Beispiele; bestehende NeoForged-Hinweise bleiben enthalten.

Peripheral Links, persistente Kanäle, Lua-APIs, optionale Create-/Sable-Zahnradbrücken und New-Age-FE-Eingänge bleiben enthalten. Es ist keine Migration bestehender Welt- oder Addondaten erforderlich.

## Download und Installation

- `teutonlib-1.1.0.jar`: Mod für den `mods`-Ordner auf Client und Server.
- `teutonlib-1.1.0-sources.jar`: Kotlin-Quellen für Entwickler; nicht im `mods`-Ordner installieren.
- `teutonlib-1.1.0-computer-ui-template.zip`: Mod, Quellen, Dokumentation, Lizenzen und kopierbare Kotlin-Integrationsbeispiele.
- `SHA256SUMS`: SHA-256-Prüfsummen dieser drei Dateien.

Downloads: [GitHub Releases](https://github.com/TeutonStudio/TeutonLib/releases/tag/v1.1.0).

Die separate Create Sable Optimization-Mod mit Mod-ID `cso` muss entfernt werden. Fremdmods werden nicht eingebettet. Die optionalen Integrationen und ihre getesteten Versionen stehen in der [README](../README.md).

## Verwendung durch Addons

Die Koordinate der lokalen Maven-Publikation bleibt `de.TeutonStudio.TeutonLib:teutonlib:1.1.0`. Dieses Release bietet die JAR über GitHub an; ein öffentliches Maven-Repository wird noch nicht bereitgestellt. Die [Integrationsanleitung](../examples/computer-ui/README.md) beschreibt JAR-Einbindung und lokale Maven-Nutzung.

Die öffentlichen Provider-, Kontext- und Workspace-Schnittstellen sowie die dokumentierten Clientkomponenten sind die unterstützten Erweiterungspunkte. Kompatible API-Ergänzungen erfolgen in Minor-Versionen, inkompatible Änderungen an der öffentlichen API erst mit einer neuen Major-Version. Implementierungsdetails in Integrations- und Mixin-Klassen sind keine zugesicherten Erweiterungspunkte.

Pocket Computer und Turtles erhalten keine zusätzlichen UI-Reiter. Lua-Skripte und JSON-/NBT-Ressourcen bleiben Bestandteile der Mod; die JVM-Implementierung und Beispiele bestehen ausschließlich aus Kotlin.

## Prüfnachweis vom 18. Oktober 2026

- Sauberer Kotlin-Build, kompilierte Beispiele und beide Entwicklungstestmods: bestanden.
- 32 JUnit-Tests, darunter unveränderte JVM-Signaturen der veröffentlichten 1.0.0-API und defensive Kopien der Providerdaten: bestanden.
- Dedicated Server ohne optionale Mods, mit Create sowie mit vollständigem Create-/Sable-/New-Age-/Flux-Satz: bestanden. Config-Neuladen, Umschalten, Energieübertragung und Motor-Neustart sind enthalten.
- CC- und Aeroworks-/DbW-Prüfungen: bestanden. Kanäle, Peripheral-Methoden, Inventar-/Flüssigkeitstransfers, Laufwerks-Mounts, Ereignisse, Sable-Bewegung, Menürechte und Spectator-Schutz sind enthalten.
- Gezielter Kanal-Lebenszyklus mit Chunk-Entladung, Wiederladen und Serverneustart: bestanden.
- Release-JAR in einer getrennten Clientinstallation ohne optionale Mods: bestanden; Herkunft der geladenen TeutonLib-Klassen aus der JAR geprüft.
- Acht minimale CC-UI-Szenarien direkt mit der Release-JAR: bestanden.
- Zwölf UI-Szenarien direkt mit der Release-JAR und allen optionalen Integrationen: bestanden. Native CC-Computer, Kotlin-Vorlage und Aeroworks-Workspace jeweils normal/erweitert und GUI-Skalierung 2/3; Fokus, Terminalwechsel, Namenseingabe und Resize-Sitzungen geprüft.
- Vollständige Peripheral-/Workspace-/Sable-Serverprüfung direkt mit der Release-JAR in einer getrennten Testinstallation einschließlich Neustart: bestanden.
- CSO-, New-Age-/Flux-Energie- und Motor-Neustartprüfungen direkt mit der Release-JAR und vollständigem optionalem Modsatz: bestanden.
- UI-Konfiguration direkt mit der Release-JAR: bestanden. Deaktivierter Start, Ein-/Ausschalten im offenen Bildschirm, Resize und Wiederherstellung des Terminals.

Die UI-Prüfungen verwenden echte Widgets mit synthetischen Menüs ohne Welt. Reale Hosts, Datenhaltung, Peripherals und Berechtigungen werden separat in den Server-Testwelten geprüft. Fremde Addon-Hosts benötigen weiterhin eigene Integrationsprüfungen.

`python3 tools/verify_release.py` prüft die Metadaten, beide Lizenzen, ausschließlich Kotlin-Quellen, den Ausschluss fremder JARs und Testmods sowie die Gleichheit der ausgelieferten Klassen mit dem geprüften Compilergebnis. Das Skript vergleicht außerdem die eingebetteten JARs im Vorlagen-ZIP und erzeugt `build/distributions/SHA256SUMS`.

Versionsbasis: CC:Tweaked 1.120.2, CC Aeroworks 1.4.0 mit Workspace-Adapter, Aeroworks 1.5.0, Drive By Wire 0.3.0, Create 6.0.10, Sable 2.0.5, Aeronautics/Simulated 1.3.2, Create New Age 1.1.7c und Flux Networks 8.0.0. Commit-ID und JAR-Prüfsumme werden zusätzlich in der GitHub-Veröffentlichung dokumentiert.
