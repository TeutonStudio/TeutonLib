# TeutonLib Wiki — English

[Deutsch — Hauptsprache](Home) · **English**

TeutonStudio’s shared Minecraft library combines computer UI/APIs with optional Create/Sable, train, energy, fluid and compatibility integrations. **SableSchlauch and CreateSableOptimization are integrated**; remove their separate JARs.

Minecraft **1.21.1**, NeoForge **21.1.234+**, Java **21**, KotlinForForge **5.11.0–<6**. Install on server and every client. Optional integrations have exact version gates.

[Downloads](https://github.com/TeutonStudio/TeutonLib/releases) · [README](https://github.com/TeutonStudio/TeutonLib#readme) · [Issues](https://github.com/TeutonStudio/TeutonLib/issues)

**Updated November 14, 2026.** The baseline is source version **1.6.1**. Compared with 1.6.0, it adds [Peripheral Link antennas and endpoint locating](Peripheral-Link-en#antenna-and-locating-endpoints): saved antenna faces, known links and private target markers lasting 15 seconds. Unreleased local changes are listed below and labelled on their guides.

Each guide has its own illustration and reciprocal language link. Gameplay captures, addon consumers and nonvisual desktop test/code captures are explicitly distinguished.

## Getting started

- [Installation](Installation-en)
- [Configuration and commands](Konfiguration-und-Befehle-en)
- [Development and verification](Entwicklung-und-Pruefungen-en)

## Trains and portals

- [Station construction](Bahnhofsbau-en)
- [Hybrid trains](Hybridzuege-en)
- [Bearings and child contraptions](Lager-und-Untercontraptions-en)
- [Radar type and persistence](Radar-Typ-und-Speicherung-en)
- [Create Big Cannons on hybrid trains](Create-Big-Cannons-auf-Hybridzuegen-en)
- [Train docking](Zug-Docking-en)
- [Train portal transfer](Zug-Portaltransfer-en)
- [Portal rendering and lighting](Portal-Darstellung-und-Beleuchtung-en)

## Create, energy and fluids

- [Cogwheel bridges](Zahnradbruecken-en)
- [Analog docking connectors](Analoge-Dockingports-en)
- [FE input through New Age motor extensions](New-Age-FE-Eingang-en)
- [New Age motor speed on Sable](New-Age-Motordrehzahl-auf-Sable-en)
- [Physical hose pulley](Physische-Schlauchrolle-en)
- [Hose coupling on the Item Drain](Schlauchkupplung-am-Item-Drain-en)
- [Sable tank fluid surfaces — development](Sable-Tankoberflaeche-en)
- [FluidLogistics and Factory Logistics](FluidLogistics-und-Factory-Logistics-en)
- [FluidLogistics and TFMG](FluidLogistics-und-TFMG-en)
- [Mekanism connection models on Sable](Mekanism-Anschlussmodelle-en)

## Computers and control

- [Computer UI: sources and Lua API](Computer-UI-en)
- [Computer channels](Computer-Kanaele-en)
- [Peripheral Link: antennas and locating endpoints](Peripheral-Link-en)
- [Directional Gearshift](Directional-Gearshift-en)
- [Helicopter peripherals: gimbal sensor](Helikopter-Peripherie-en)
- [Gyroscopic bearing control](Gyroskop-Steuerung-en)
- [Optical sensor: local laser direction](Optischer-Sensor-en)
- [Radio and GPS on Sable](Funk-und-GPS-en)
- [Graphics API: portable SVG output](Grafik-API-en)
- [Bitmap font](Bitmap-Schrift-en)

## Developer APIs

- [Peripheral topology and attachments](Peripheral-Topologie-und-Attachments-en)
- [Computer workspaces and hosted runtime](Computer-Workspace-und-Kernel-en)
- [Embedded computer kernel](Embedded-Computer-Kernel-en)
- [Persistent groups and signals](Persistente-Kanalgruppen-und-Signale-en)
- [Persistent channel groups](Persistente-Kanalgruppen-en)
- [Signal bank](Signalbank-en)
- [Sable geometry API](Sable-Geometrie-API-en)
- [Docking state API](Docking-Zustands-API-en)
- [Network sessions and budgets](Netzwerk-Sitzungen-und-Budgets-en)

## Android and Zalith — earlier TeutonLib versions

Unreleased: These adapters have moved to TerrArchitecture’s [mobilecompat module](https://github.com/TeutonStudio/TerrArchitecture/blob/master/src/main/kotlin/de/TeutonStudio/mobilecompat/SPECS.md); the following pages document earlier TeutonLib releases.

- [Android and Zalith compatibility](Android-und-Zalith-Kompatibilitaet-en)
- [Ponder and Catnip on Android](Ponder-und-Catnip-auf-Android-en)
- [PowerGrid on Android](PowerGrid-auf-Android-en)
- [Essential on Android](Essential-auf-Android-en)
- [Immersive Melodies on Android](Immersive-Melodies-auf-Android-en)
- [Visual Recipe Editor on Android](Visual-Recipe-Editor-auf-Android-en)

## Resources and diagnostics

- [Resource repairs](Ressourcenreparaturen-en)
- [Create Radar resource repair](Create-Radar-Ressourcenreparatur-en)
- [EnderIO resources without Athena](EnderIO-ohne-Athena-en)
- [NTGL animation repairs](NTGL-Animationsreparaturen-en)
- [Design 'n' Decor sound resource](Design-n-Decor-Soundreparatur-en)
- [Package metadata diagnostics](Paketmetadaten-Diagnose-en)

## Development and documentation scope

Guides now cover **release 1.6.2**. Historical images and reports retain their original version: the 160 tests from November 13 cover 1.6.0, not today's checkout. These changes are included in **1.6.2** in addition to the 1.6.1 baseline:

- [Sable tank fluid surfaces](Sable-Tankoberflaeche-en).
- [Station construction](Bahnhofsbau-en): native preview positions, empty-plot persistence and bearing restart after cancellation.
- [Physical hoses](Physische-Schlauchrolle-en) and [Item Drain coupling](Schlauchkupplung-am-Item-Drain-en): wrench switching and cable sag, obstacle routing and tension.
- [New Age FE input](New-Age-FE-Eingang-en): recessed rear Flux Point models and matching selection shapes.
- [Android adapters](Android-und-Zalith-Kompatibilitaet-en): migration to TerrArchitecture; the TeutonLib guides remain historical references.

The [gyroscopic bearing guide](Gyroskop-Steuerung-en) records the missing direct Lua methods found during capture. The known standing-rider Aether position error and possible docked-load sag remain limitations. Desktop mobile-adapter tests are not Android-device recordings.

[Media provenance and verification scope](Bildnachweise-en) · [Wiki sources](https://github.com/TeutonStudio/TeutonLib/tree/master/docs/wiki)
