# TeutonLib 1.2.2

Für Minecraft **1.21.1**, NeoForge **21.1.234** und Java **21**.
**Kotlin for Forge 5.11.0 bis kleiner 6 ist auf Client und Server erforderlich.**

## Änderungen gegenüber 1.2.1

- Config im Spiel bearbeiten: **Mods → TeutonLib → Config** öffnet den eingebauten NeoForge-Editor. Configured ist nicht erforderlich.
- Deutsche und englische Beschriftungen und Hilfetexte für alle Config-Einstellungen.
- Client-Einstellungen sind lokal bearbeitbar. Welt-Einstellungen sind in einer geladenen Einzelspielerwelt bearbeitbar; auf entfernten Servern und bei für LAN geöffneten Welten sperrt NeoForge diese Einstellungen. Dedicated-Server-Einstellungen weiterhin auf dem Server bearbeiten.
- Analoge Simulated-Docking-Connector: Redstone 0–5 fährt ein, 6–10 fordert einen halben Block und 11–15 einen ganzen Block an. Beide Gegenstellen verwenden die kürzere angeforderte Länge. Unter `compat.simulated.enableAnalogDocking` abschaltbar; benötigt Simulated 1.3.2 und die unterstützten Create-/Sable-Versionen. Details: [Analoge Docking Connector](analog-docking.md).
- Mod-Code, Mixins, Tests und Beispiele sind vollständig Kotlin; jetzt verwenden auch die Gradle-Builddateien Kotlin DSL. CC-Laufzeitprogramme bleiben Lua, externe Entwicklungs-Prüfskripte Python.
- Die Releaseprüfung sichert Kotlin for Forge als Pflichtabhängigkeit auf beiden Seiten und die enthaltene Config-Editor-Registrierung ab.

## Downloads

- `teutonlib-1.2.2.jar`: Mod für den `mods`-Ordner auf Client und Server.
- `teutonlib-1.2.2-sources.jar`: Kotlin-Quellen für Entwickler.
- `teutonlib-1.2.2-computer-ui-template.zip`: Mod, Quellen, Dokumentation, Lizenzen und Integrationsbeispiele.
- `SHA256SUMS`: Prüfsummen der drei Archive.

Fremdmods werden nicht eingebettet. Create, Sable, Simulated und die übrigen Integrationen bleiben optional; die separate Create Sable Optimization-Mod (`cso`) muss entfernt werden.

## Prüfung

- `./gradlew build releaseBundle compilePeripheralTestKotlin compileDockingTestKotlin compileEnergyTestKotlin --offline`
- 51 JUnit-Tests erfolgreich.
- Config-Editor im echten Client geprüft: Registrierung, Navigation, Umschalten der UI-Einstellung und Sperre ungeladener Welt-Einstellungen; außerdem native und Vorlagen-Computer mit normaler und erweiterter Ausführung.
- Dedicated-Server-Smoke-Test ohne optionale Mods: Start, Status, Speichern, Umschalten und Neuladen der Config erfolgreich.
- Releasearchive auf Metadaten, Pflichtabhängigkeit, Lizenzen, Kotlin-Quellen und Übereinstimmung mit den kompilierten Klassen geprüft.
