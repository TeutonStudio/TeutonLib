# Media provenance and verification scope

[Deutsch](Bildnachweise) · **English**

All illustrations are actual gameplay, test-report or source-code screenshots and existing gameplay GIFs. No game images were generated or reconstructed as functional evidence. Historical captures are labelled. Test-report screenshots show **160 JUnit tests actually executed on November 13, 2026 against release sources 15f1a2a**.

Pure APIs and automatic adapters have no standalone gameplay UI. Their test/code views do not validate the whole game system. **No Android-device recordings are available.** Mobile target-signature checks do not establish Android GPU execution. The public API check establishes signature compatibility, not all runtime failure cases.

Aeroworks GPS/docking/workspace images show an **additional addon consuming TeutonLib**. The historical CSO scene comes from the now-integrated predecessor. The Peripheral Link UI capture explicitly uses verification data. Tank images show **unreleased local development**. Direct gyro Lua methods were absent in the check; the guide documents the link path.

Documentation was updated to the 1.6.1 baseline on November 14. The pictured 160-test report still belongs to historical version 1.6.0. Three unmodified antenna captures were added from successful CC/Create/Sable run `2372da0bb22a`: face selection, mounting on a rotated Sable computer, and a private target marker through a wall. Native Minecraft tutorial overlays remain visible.

Cable routing and the recessed rear Flux flange are local development. Their older images do not establish these changes; pending graphical checks are named on the feature pages. Android images remain historical TeutonLib evidence after local migration to TerrArchitecture.

Two additional unmodified cable images come from local client run `20261114-203614-8e820559`. Both observers passed sag/Flux/long-cable captures with matching compared cable points. The overall run subsequently failed an untracked-chunk pause timeout. Images document those partial cases, not a passing full test.

| Guide | Illustration and scope |
| --- | --- |
| [Installation](Installation-en) | Built-in NeoForge configuration editor for TeutonLib; no extra config mod is required. |
| [Configuration and commands](Konfiguration-und-Befehle-en) | TeutonLib in the native configuration editor. Server options require an appropriate loaded world. |
| [Station construction](Bahnhofsbau-en) | Historical first-carriage station scene from the 1.6.0 demo; this is not evidence of the November 12 local preview repair. |
| [Hybrid trains](Hybridzuege-en) | Actual hybrid train with machines and orange ContraptionLevel bounds from the 1.6.0 demo. |
| [Bearings and child contraptions](Lager-und-Untercontraptions-en) | Native running bearing payload on the hybrid test train; persistence/restart have separate historical gameplay checks. |
| [Radar type and persistence](Radar-Typ-und-Speicherung-en) | Actual radar on the hybrid train. The image shows the payload; saved type is checked by the separately documented gameplay test. |
| [Create Big Cannons on hybrid trains](Create-Big-Cannons-auf-Hybridzuegen-en) | Executed desktop check of version-gated hybrid integration including cannon target classes; not a firing recording. |
| [Train docking](Zug-Docking-en) | Native locking, partner after a fitting portal transfer, and platform left behind when the opening is too small. |
| [Train portal transfer](Zug-Portaltransfer-en) | Actual portal crossing with the same machine payload on source/destination sides; the known standing-rider issue remains. |
| [Portal rendering and lighting](Portal-Darstellung-und-Beleuchtung-en) | Portal clipping, destination-world lighting and ContraptionLevel bounds in the actual test scene. |
| [Cogwheel bridges](Zahnradbruecken-en) | Historical gameplay capture of the now-integrated CSO module: drive and cogwheels at a dock; not a fresh TeutonLib acceptance test. |
| [Analog docking connectors](Analoge-Dockingports-en) | Actual native dock and executed OFF/HALF/FULL length tests. The gameplay image alone does not establish a particular redstone strength. |
| [FE input through New Age motor extensions](New-Age-FE-Eingang-en) | Actual FE source → motor extension → motor arrangement and existing Flux demonstration. |
| [New Age motor speed on Sable](New-Age-Motordrehzahl-auf-Sable-en) | Executed desktop checks of supported motor variants and the version-gated rotation integration; not a new movement recording. |
| [Physical hose pulley](Physische-Schlauchrolle-en) | Historical carrying/throwing GIF plus local development captures of sag, obstacle routing and a long cable without a tracked head. The captures passed; the complete client run subsequently failed its chunk-pause check. |
| [Hose coupling on the Item Drain](Schlauchkupplung-am-Item-Drain-en) | Actual entity head seated in the Item Drain in the separate graphical hose test. |
| [Mekanism connection models on Sable](Mekanism-Anschlussmodelle-en) | Actual pipe-model captures after mode change and during Sable movement. |
| [FluidLogistics and Factory Logistics](FluidLogistics-und-Factory-Logistics-en) | Executed desktop version/class contract checks for the shared resource adapter; no new ordering UI is introduced. |
| [FluidLogistics and TFMG](FluidLogistics-und-TFMG-en) | Executed desktop checks of pump/propagation target classes; the broader fluid-flow verification is documented separately. |
| [Computer UI: sources and Lua API](Computer-UI-en) | Actual CC block computer with TeutonLib Sources tab in the disposable capture world. |
| [Computer channels](Computer-Kanaele-en) | Actual CC block computer with two channels created through the native Lua API. |
| [Peripheral Link](Peripheral-Link-en) | Historical Link UI with fixture data plus actual antenna face selection, Sable mounting and target marker from the successful antenna client test. No personal save was used. |
| [Directional Gearshift](Directional-Gearshift-en) | Actual Lua readback after setOutputs(true, false) on the native Directional Gearshift. |
| [Helicopter peripherals: gimbal sensor](Helikopter-Peripherie-en) | Actual pitch/yaw/roll readback and unchanged two-axis legacy format from the native gimbal sensor. |
| [Gyroscopic bearing control](Gyroskop-Steuerung-en) | Actual setTargetDirection and adjustTilt Lua calls accepted by the native bearing through a Peripheral Link; this is not a complete flight controller. |
| [Optical sensor: local laser direction](Optischer-Sensor-en) | Actual local-direction Lua readback through a Peripheral Link and executed checks of all six directions. |
| [Radio and GPS on Sable](Funk-und-GPS-en) | Actual GPS fix from a Sable-mounted Aeroworks computer and four GPS hosts. Aeroworks is an additional consumer of TeutonLib integration. |
| [Graphics API: portable SVG output](Grafik-API-en) | Actual teutonlib.svg and teutonlib.font output on a CC terminal without CCVisual or Aeroworks. |
| [Bitmap font](Bitmap-Schrift-en) | RPM and READY are actual 3×5 bitmap-font output; shapes come from the SVG module. |
| [Computer workspaces and hosted runtime](Computer-Workspace-und-Kernel-en) | Real Aeroworks workspace as an addon consumer plus executed snapshot/command checks. The addon supplies the domain UI. |
| [Embedded computer kernel](Embedded-Computer-Kernel-en) | Actual embedded CC computer in the additional Aeroworks addon consuming HostedComputer. TeutonLib registers no such desk block. |
| [Peripheral topology and attachments](Peripheral-Topologie-und-Attachments-en) | Actual device view and executed public API signature check. The image does not establish every attach/detach failure case. |
| [Persistent groups and signals](Persistente-Kanalgruppen-und-Signale-en) | Actual addon group editor and executed bank tests. Group and signal banks are separate APIs. |
| [Persistent channel groups](Persistente-Kanalgruppen-en) | Aeroworks as an actual group-bank consumer; tests cover retained missing targets and definition persistence. |
| [Signal bank](Signalbank-en) | Executed checks of signal authority, pulse expiry and reset after loading. SignalBank has no standalone GUI. |
| [Sable geometry API](Sable-Geometrie-API-en) | Visible world position read in a real Sable GPS consumer; this does not separately validate every interaction/render helper. |
| [Docking state API](Docking-Zustands-API-en) | Actual native locked connection and docking-state readback in an additional Aeroworks consumer. |
| [Network sessions and budgets](Netzwerk-Sitzungen-und-Budgets-en) | Executed desktop contract tests for session expiry, gesture order and per-player tick budgets; no standalone game UI. |
| [Android and Zalith compatibility](Android-und-Zalith-Kompatibilitaet-en) | Executed desktop Android-detection tests. This is not an Android/Zalith device capture. |
| [Ponder and Catnip on Android](Ponder-und-Catnip-auf-Android-en) | Executed desktop check of the exact supported target methods. Not a functional validation on an Android device. |
| [PowerGrid on Android](PowerGrid-auf-Android-en) | Executed desktop check of the exact supported target methods. Not a functional validation on an Android device. |
| [Immersive Melodies on Android](Immersive-Melodies-auf-Android-en) | Executed desktop check of the exact supported target methods. Not a functional validation on an Android device. |
| [Visual Recipe Editor on Android](Visual-Recipe-Editor-auf-Android-en) | Executed desktop check of the exact supported target methods. Not a functional validation on an Android device. |
| [Essential on Android](Essential-auf-Android-en) | The actual Essential socket test checks connected=false, defined errors and safe close. Desktop capture, not an Android gameplay test. |
| [Resource repairs](Ressourcenreparaturen-en) | Actually executed desktop checks of original file hashes and concrete resource rules. Tests identify the affected resources. |
| [Create Radar resource repair](Create-Radar-Ressourcenreparatur-en) | Actually executed desktop checks of original file hashes and concrete resource rules. Tests identify the affected resources. |
| [EnderIO resources without Athena](EnderIO-ohne-Athena-en) | Actually executed desktop checks of original file hashes and concrete resource rules. Tests identify the affected resources. |
| [NTGL animation repairs](NTGL-Animationsreparaturen-en) | Actually executed desktop checks of original file hashes and concrete resource rules. Tests identify the affected resources. |
| [Design 'n' Decor sound resource](Design-n-Decor-Soundreparatur-en) | Actually executed desktop checks of original file hashes and concrete resource rules. Tests identify the affected resources. |
| [Package metadata diagnostics](Paketmetadaten-Diagnose-en) | Screenshot of the actual diagnostic path: filename, mod version, fingerprint and rethrow of the original error. Code inspection, not a staged player crash. |
| [Development and verification](Entwicklung-und-Pruefungen-en) | Historical November 13, 2026 run: 160 JUnit tests against unchanged 1.6.0 sources, no failures or skipped tests. This is not a test result for 1.6.1 or local changes. |
| [Sable tank fluid surface — development](Sable-Tankoberflaeche-en) | Unreleased development build: actual rendered fluid surfaces in tilted and sideways tanks. Not included in release 1.6.0. |

[Machine-readable media inventory](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/wiki-media-coverage.json) · [Review report](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/wiki-review-2026-11-13.md)
