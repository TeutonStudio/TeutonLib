# TeutonLib Wiki

**Deutsch** · [English](English)

TeutonLib ist die gemeinsame Minecraft-Bibliothek von TeutonStudio. Sie verbindet Computeroberflächen und APIs mit Create-/Sable-, Zug-, Energie-, Flüssigkeits- und Kompatibilitätsfunktionen. **SableSchlauch und CreateSableOptimization sind darin aufgegangen**; die separaten JARs dürfen nicht zusätzlich installiert werden.

Minecraft **1.21.1** · NeoForge **ab 21.1.234** · Java **21** · KotlinForForge **5.11.0 bis kleiner 6**. Auf Server und allen Clients installieren; weitere Mods sind je Funktion optional und versionsgebunden.

[Downloads](https://github.com/TeutonStudio/TeutonLib/releases) · [Kurze englische README](https://github.com/TeutonStudio/TeutonLib#readme) · [Fehler melden](https://github.com/TeutonStudio/TeutonLib/issues)

**Stand: 21.11.2026.** Aktuelles Release ist **1.6.3**. Neu sind [angepasste Flux-Point-Anschlüsse an allen zugänglichen Motorerweiterungsflächen](New-Age-FE-Eingang) und ein [Absturzfix beim Bahnhofszusammenbau](Bahnhofsbau).

Deutsch ist die Hauptsprache. Jede unten verlinkte Anleitung hat eine englische Fassung und eine direkt eingebundene Abbildung. Spielaufnahmen zeigen die Bedienung; reine Bibliotheks-/Kompatibilitätsfunktionen zeigen ihre tatsächliche Prüfung oder den Diagnosepfad. [Bildherkunft und Grenzen](Bildnachweise).

## Einstieg

- [Installation](Installation)
- [Konfiguration und Befehle](Konfiguration-und-Befehle)
- [Entwicklung und Prüfungen](Entwicklung-und-Pruefungen)

## Züge und Portale

- [Bahnhofsbau](Bahnhofsbau)
- [Hybridzüge](Hybridzuege)
- [Lager und Untercontraptions](Lager-und-Untercontraptions)
- [Radar-Typ und Speicherung](Radar-Typ-und-Speicherung)
- [Create Big Cannons auf Hybridzügen](Create-Big-Cannons-auf-Hybridzuegen)
- [Zug-Docking](Zug-Docking)
- [Zug-Portaltransfer](Zug-Portaltransfer)
- [Portal-Darstellung und Beleuchtung](Portal-Darstellung-und-Beleuchtung)

## Create, Energie und Flüssigkeiten

- [Zahnradbrücken über Sable-Dockingverbindungen](Zahnradbruecken)
- [Analoge Dockingports](Analoge-Dockingports)
- [FE-Eingang an New-Age-Motorerweiterungen](New-Age-FE-Eingang)
- [New-Age-Motordrehzahl auf Sable](New-Age-Motordrehzahl-auf-Sable)
- [Physische Schlauchrolle](Physische-Schlauchrolle)
- [Schlauchkupplung am Item Drain](Schlauchkupplung-am-Item-Drain)
- [Sable-Tankoberfläche — Entwicklung](Sable-Tankoberflaeche)
- [FluidLogistics und Factory Logistics](FluidLogistics-und-Factory-Logistics)
- [FluidLogistics und TFMG](FluidLogistics-und-TFMG)
- [Mekanism-Anschlussmodelle auf Sable](Mekanism-Anschlussmodelle)

## Computer und Steuerung

- [Computer-UI: Quellen und Lua-API](Computer-UI)
- [Computer-Kanäle](Computer-Kanaele)
- [Peripheral Link: Antennen und Ortung](Peripheral-Link)
- [Directional Gearshift](Directional-Gearshift)
- [Helikopter-Peripherie: Gimbalsensor](Helikopter-Peripherie)
- [Gyroskop-Steuerung](Gyroskop-Steuerung)
- [Optischer Sensor: lokale Laserrichtung](Optischer-Sensor)
- [Funk und GPS auf Sable](Funk-und-GPS)
- [Grafik-API: portable SVG-Ausgabe](Grafik-API)
- [Bitmap-Schrift](Bitmap-Schrift)

## Entwickler-APIs

- [Peripheral-Topologie und Attachments](Peripheral-Topologie-und-Attachments)
- [Computer-Workspace und eingebetteter Kernel](Computer-Workspace-und-Kernel)
- [Embedded-Computer-Kernel](Embedded-Computer-Kernel)
- [Persistente Kanalgruppen und Signale](Persistente-Kanalgruppen-und-Signale)
- [Persistente Kanalgruppen](Persistente-Kanalgruppen)
- [Signalbank](Signalbank)
- [Sable-Geometrie-API](Sable-Geometrie-API)
- [Docking-Zustands-API](Docking-Zustands-API)
- [Netzwerk-Sitzungen und Budgets](Netzwerk-Sitzungen-und-Budgets)

## Android und Zalith — frühere TeutonLib-Versionen

Unveröffentlichter Quellstand: Die Adapter wurden in TerrArchitectures [Modul mobilecompat](https://github.com/TeutonStudio/TerrArchitecture/blob/master/src/main/kotlin/de/TeutonStudio/mobilecompat/SPECS.md) ausgelagert; die folgenden Seiten dokumentieren frühere TeutonLib-Versionen.

- [Android- und Zalith-Kompatibilität](Android-und-Zalith-Kompatibilitaet)
- [Ponder und Catnip auf Android](Ponder-und-Catnip-auf-Android)
- [PowerGrid auf Android](PowerGrid-auf-Android)
- [Essential auf Android](Essential-auf-Android)
- [Immersive Melodies auf Android](Immersive-Melodies-auf-Android)
- [Visual Recipe Editor auf Android](Visual-Recipe-Editor-auf-Android)

## Ressourcen und Diagnose

- [Ressourcenreparaturen](Ressourcenreparaturen)
- [Create-Radar-Ressourcenreparatur](Create-Radar-Ressourcenreparatur)
- [EnderIO-Ressourcen ohne Athena](EnderIO-ohne-Athena)
- [NTGL-Animationsreparaturen](NTGL-Animationsreparaturen)
- [Design 'n' Decor: Soundressource](Design-n-Decor-Soundreparatur)
- [Paketmetadaten-Diagnose](Paketmetadaten-Diagnose)

## Entwicklung und Dokumentationsstand

Die Anleitungen berücksichtigen **Release 1.6.3**. Gegenüber 1.6.2 kommen angepasste Flux-Point-Modelle samt Auswahl- und Kollisionsformen sowie der Schutz geparkter Sable-Physikkörper beim Bahnhofszusammenbau hinzu. Historische Bilder und Prüfberichte behalten ihren ursprünglichen Versionsstand; die 160 Tests vom 13.11.2026 gehören zu 1.6.0 und sind kein Prüfergebnis des heutigen Quellstands. Folgende Änderungen sind zusätzlich zur Basis 1.6.1 in **1.6.2 enthalten**:

- [Sable-Tankoberfläche](Sable-Tankoberflaeche)
- [Bahnhofsbau](Bahnhofsbau): native Vorschaupositionen, Leerplot-Speicherung und Wiederanlauf von Lagern beim Abbrechen.
- [Physische Schlauchrolle](Physische-Schlauchrolle) und [Item-Drain-Kupplung](Schlauchkupplung-am-Item-Drain): Schraubenschlüsselwechsel und Schlauchführung mit Durchhang, Hindernissen und Zugkräften.
- [New-Age-FE-Eingang](New-Age-FE-Eingang): rückseitiges Flux-Point-Anschlussmodell mit passender Auswahlform.
- [Android-Adapter](Android-und-Zalith-Kompatibilitaet): Auslagerung nach TerrArchitecture; die TeutonLib-Seiten bleiben als historische Referenz erhalten.

Die [Gyroskop-Anleitung](Gyroskop-Steuerung) nennt die beim Prüfen gefundene Einschränkung des direkten Lua-Zugriffs. Die bekannte Aether-Abweichung stehender Mitfahrer und mögliches Durchhängen angedockter Lasten sind weiterhin dokumentiert. Screenshots ändern diese Prüfgrenzen nicht.

[Versionierte Wiki-Quellen](https://github.com/TeutonStudio/TeutonLib/tree/master/docs/wiki) · [Bildnachweise und Prüfgrenzen](Bildnachweise)
