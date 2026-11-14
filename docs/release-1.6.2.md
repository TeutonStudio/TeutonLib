# TeutonLib 1.6.2

Für Minecraft **1.21.1**, NeoForge **21.1.234+**, Java **21** und KotlinForForge **5.11.0–<6**. Gebaut mit NeoForge **21.1.251**. TeutonLib gehört auf Server und alle Clients.

## Änderungen seit 1.6.0

- **Peripheral-Link-Antennen und Ortung:** Enthält die bisher nicht als GitHub-Release veröffentlichte Erweiterung aus 1.6.1: anklickbare Computerantennen, gespeicherte Antennenseiten, bekannte Links einschließlich entladener Geräte und private Zielmarkierungen für 15 Sekunden.
- **Physische Schläuche:** Serverseitig simulierter Verlauf mit Durchhang, Hindernisumlenkung, Zugkräften und maximal 256 Blöcken Länge. Clients interpolieren den gemeinsamen Verlauf; Texturen bleiben am Kopf verankert. Schraubenschlüssel-/Hotbar-Wechsel und Item-Drain-Kupplung sind korrigiert.
- **Sable-Tanks:** Creates Fenstertanks erhalten bei Neigung eine weltwaagerechte, gegen den Tankinnenraum geschnittene Flüssigkeitsoberfläche mit erhaltenem Füllanteil und wiederverwendeter Geometrie. Keine Schwappsimulation.
- **Bahnhofsbau:** Korrekturen an nativen Vorschaupositionen, Speicherung leerer Bauplots und Wiederherstellung laufender Lager beim Abbrechen.
- **Flux/New Age:** Rückseitige Flux-Point-Anschlüsse an Motorerweiterungen erhalten passende Modelle und Auswahlformen.
- **Dokumentation:** Kompakte englische README, illustrierte deutsch-/englischsprachige Wiki-Quellen und dokumentierte Prüfgrenzen.

## Migration der Android-/Zalith-Anpassungen

**TeutonLib liefert die bisherigen mobilen Adapter ab 1.6.2 nicht mehr aus.** Android-Erkennung, Framebuffer-/Stencil-Fallbacks sowie PowerGrid-, Essential-, MIDI- und Backup-Adapter liegen jetzt in TerrArchitectures Modul `mobilecompat`. Für diese Funktionen ist ein passender TerrArchitecture-Build mit diesem Modul erforderlich; beide Projekte gemeinsam aus dem passenden Quellstand bauen und im Pack ersetzen. Dieses Release veröffentlicht kein TerrArchitecture-Update.

Die neuen JVM-Optionen verwenden `terrarchitecture.mobile.*`; bisherige `teutonlib.mobile.*`-Namen bleiben dort Aliase. Die unabhängigen Ressourcenreparaturen bleiben in TeutonLib. Historische mobile Desktop-Prüfnachweise sind keine Android-Gerätetests.

## Prüfung und Grenzen

`releaseBundle` mit Java 21 bestanden: **190 JUnit-Tests, 0 Fehler, 0 übersprungen**. Wiki-Prüfung: **52 Sprachpaare**, Navigation, Medien und Quellverweise geprüft. Release-Archive: Metadaten, Lizenzen, Klassenabgleich, externe Testfixtures und eingebettete Abhängigkeiten geprüft; SHA-256-Prüfsummen erzeugt. Datierte Spiel- und Grafikprüfungen sind in den jeweiligen Funktionsdokumentationen beschrieben; sie sind keine vollständige erneute Abnahme aller optionalen Modkombinationen. Die bekannte Aether-Positionsabweichung stehender Mitfahrer und mögliches Durchhängen angedockter Lasten bleiben dokumentiert.

Downloads: Mod-JAR, Quellen-JAR, Computer-UI-Vorlagenpaket und `SHA256SUMS`. Die separaten Mods SableSchlauch und CreateSableOptimization sind integriert und müssen entfernt werden.

[Änderungen in 1.6.1](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/release-1.6.1.md) · [Wiki Deutsch](https://github.com/TeutonStudio/TeutonLib/wiki) · [Wiki English](https://github.com/TeutonStudio/TeutonLib/wiki/English)
