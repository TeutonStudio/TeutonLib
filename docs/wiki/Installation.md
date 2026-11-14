# Installation

**Deutsch** · [English](Installation-en) · [Übersicht](Home)

Diese Wiki beschreibt **TeutonLib 1.6.1** für Minecraft **1.21.1**.

## Grundvoraussetzungen

| Komponente | Voraussetzung |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | mindestens 21.1.234, passend zu Minecraft 1.21.1 |
| Java | 21 |
| KotlinForForge | mindestens 5.11.0 und kleiner als 6 |

1. Lade `teutonlib-1.6.1.jar` aus dem [Release](https://github.com/TeutonStudio/TeutonLib/releases).
2. Installiere die JAR und KotlinForForge auf **Server und allen Clients** im jeweiligen `mods`-Ordner.
3. Ergänze die optionalen Mods für die gewünschten Funktionen. Ihre eigenen Abhängigkeiten müssen ebenfalls installiert sein.
4. Entferne die separaten Mods **Create Sable Optimization** (`cso`) und **Hose Pulley Sable Bridge / SableSchlauch** (`hosepulley_sable_bridge`): TeutonLib enthält ihre Integrationen und erklärt beide Mod-IDs als inkompatibel.

Die `-sources.jar` dient Entwicklern; sie ersetzt die normale Mod-JAR nicht. Das `computer-ui-template.zip` enthält zusätzlich Quellen, Beispiele und Dokumentation. `SHA256SUMS` enthält die Prüfsummen dieser drei Downloads.

## Version und Entwicklungsstand

Die Basis 1.6.1 enthält [Peripheral-Link-Antennen und Ortung](Peripheral-Link#antenne-und-ortung). Release 1.6.2 ergänzt Schlauchführung, Bahnhofsbau-Korrekturen, Flux-Anschlussdarstellung und Tankoberflächen. Mobile Adapter sind nach TerrArchitecture ausgelagert; siehe [Migration](Android-und-Zalith-Kompatibilitaet).

Im lokalen Stand vom 14.11.2026 wurden die Android-/Zalith-Adapter nach TerrArchitecture ausgelagert. Für diesen Stand TeutonLib und TerrArchitecture gemeinsam aktualisieren; neue JVM-Optionen und die historischen TeutonLib-Anleitungen stehen unter [Android und Zalith](Android-und-Zalith-Kompatibilitaet). Ressourcenreparaturen bleiben in TeutonLib.

## Optionale Integrationen

| Gewünschte Funktion | Geprüfte Kombination |
| --- | --- |
| [Zahnradbrücken](Zahnradbruecken) und [analoge Dockingports](Analoge-Dockingports) | Create 6.0.10, Sable 2.0.6, Simulated 1.3.2 aus Aeronautics 1.3.2 |
| [Bahnhofsbau](Bahnhofsbau) und [Hybridzüge](Hybridzuege) | Create 6.0.10, Sable 2.0.6, Create: Train Functionality 0.1.47 |
| [Zug-Portaltransfer](Zug-Portaltransfer) | obige Zugkombination und Immersive Portals: Aeronautics 2.6.4 |
| Physischer Schlauch und Item-Drain-Kupplung | Create 6.0.10 und Sable 2.0.6 |
| Computerintegration | CC:Tweaked 1.120.2 als Testbasis; deklarierter Bereich ab 1.119.0 bis kleiner 1.121.0 |
| Drive-by-Wire-Kanäle | Drive by Wire 0.3.0; deklarierter Bereich ab 0.3.0 bis kleiner 0.4.0 |
| FE-Eingang an Motorerweiterungen | Create New Age 1.1.7c; Flux Networks 8.0.0 als geprüfte FE-Quelle |

Weitere Versionsgrenzen stehen auf den jeweiligen Funktionsseiten. Ein vorhandenes Sable muss mindestens 2.0.6 sein. Mehrere interne Integrationen verlangen trotzdem **exakt** 2.0.6 und prüfen zusätzlich Klassen-Prüfsummen; eine neuere Version ist nicht automatisch kompatibel.

Ohne passende optionale Mods bleibt die betreffende Integration inaktiv. TeutonLib liefert diese Mods nicht mit; als zusätzliche eingebettete Integrationsbibliothek enthält die JAR MixinSquared 0.3.7.

## Weiter

[Konfiguration und Befehle](Konfiguration-und-Befehle) · [Alle Funktionen](Home) · [Entwicklung und Prüfungen](Entwicklung-und-Pruefungen)

Quellen: [Mod-Metadaten](https://github.com/TeutonStudio/TeutonLib/blob/e6c27c6/src/main/templates/META-INF/neoforge.mods.toml), [Versionen](https://github.com/TeutonStudio/TeutonLib/blob/e6c27c6/gradle.properties).

## Abbildung

Der eingebaute NeoForge-Editor für TeutonLib; eine zusätzliche Config-Mod ist nicht erforderlich.

![Installation: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-config.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
