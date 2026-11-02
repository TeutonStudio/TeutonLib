# TeutonLib Wiki

Willkommen in der deutschen Dokumentation für **TeutonLib 1.6.0**. Jede Funktion hat eine eigene Seite mit Voraussetzungen, Bedienung, Konfiguration oder API-Vertrag.

TeutonLib erweitert Minecraft **1.21.1** mit NeoForge **ab 21.1.234**, Java **21** und KotlinForForge **ab 5.11.0 bis kleiner 6**. Die Mod gehört auf Server und Clients. Weitere Mods sind je Funktion optional; ihre genauen Versionsgrenzen stehen auf den Einzelseiten.

[Release 1.6.0 und Downloads](https://github.com/TeutonStudio/TeutonLib/releases/tag/v1.6.0) · [Release-Notizen](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/release-1.6.0.md) · [Quellcode](https://github.com/TeutonStudio/TeutonLib/tree/v1.6.0) · [Fehler melden](https://github.com/TeutonStudio/TeutonLib/issues)

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
- [FluidLogistics und Factory Logistics](FluidLogistics-und-Factory-Logistics)
- [FluidLogistics und TFMG](FluidLogistics-und-TFMG)
- [Mekanism-Anschlussmodelle auf Sable](Mekanism-Anschlussmodelle)

## Computer und Steuerung

- [Computer-UI: Quellen und Lua-API](Computer-UI)
- [Computer-Kanäle](Computer-Kanaele)
- [Peripheral Link](Peripheral-Link)
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

## Android und Zalith

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

## Bekannte Grenzen

Bei stehenden Mitfahrern wurde in der Aether-Prüfung eine Positionsabweichung bei der Wagenkörperübergabe festgestellt. Angedockte Lasten können unter Gewicht und Hebelwirkung durchhängen. Einzelheiten und Prüfgrenzen stehen unter [Zug-Portaltransfer](Zug-Portaltransfer) und [Zug-Docking](Zug-Docking). Das ContraptionLevel verwendet weiterhin einen echten Sable-Simulationsplot.

## Dokumentationsstand

Die Wiki gehört zum Release **v1.6.0 vom 02.11.2026**. Ihre Markdown-Quellen sind im Hauptrepository unter [docs/wiki](https://github.com/TeutonStudio/TeutonLib/tree/v1.6.0/docs/wiki) und im Vorlagen-ZIP enthalten. Historische Abnahmen sind als solche gekennzeichnet; Build, Spieltests und bekannte Einschränkungen werden auf den jeweiligen Seiten getrennt beschrieben.
