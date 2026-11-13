# Create Big Cannons auf Hybridzügen

**Deutsch** · [English](Create-Big-Cannons-auf-Hybridzuegen-en) · [Übersicht](Home)

TeutonLib 1.6.0 unterstützt normale und feste Create-Big-Cannons-Kanonenlager auf Hybridzügen. CBC behält seine native Zusammensetzung, Munition und Schussberechnung. TeutonLib ergänzt die Zusammenarbeit mit der Struktursperre und der Übergabe des laufenden Wagenaufbaus.

## Voraussetzungen

Geprüft sind **Create Big Cannons 5.11.7** und **Ritchie's Projectile Lib 2.1.2** zusammen mit **Create 6.0.10**, **Sable 2.0.6** und **Create: Train Functionality 0.1.47** auf Minecraft **1.21.1**. Die CBC-Anbindung wird nur bei verfügbarer Hybridintegration und passender CBC-Version samt Klassen-Prüfsummen aktiviert. Für Portalreisen kommt **Immersive Portals: Aeronautics 2.6.4** mit **Flywheel 1.0.6** hinzu.

CBC und seine eigenen Abhängigkeiten müssen separat installiert sein. TeutonLib liefert keine fremden Mod-JARs mit.

## Kanonen verwenden

Das normale **Cannon Mount** und das **Fixed Cannon Mount** werden über die üblichen CBC-Funktionen gebaut, zusammengesetzt, geladen und ausgelöst. Ein zusätzlicher TeutonLib-Kanonenbefehl oder Configschlüssel ist nicht erforderlich.

Geschosse und Treibladungen sowie die Munitionspuffer von Autokanonen bleiben im nativen CBC-Aufbau. Schüsse und die Umrechnung von Projektilkoordinaten führt CBC aus. Die Hybrid-Struktursperre lässt die konkrete Erfassung und Rückgabe einer unterstützten Untercontraption zu; zum gewöhnlichen Umbau des Wagens ist weiterhin der [Hybrid-Umbaumodus](Hybridzuege) vorgesehen.

## Portalübertragung

Vor der Wagenübergabe werden aktive Kanonenlager kontrolliert abgesetzt. Die vorhandenen Blockdaten nehmen die Munition mit; zusätzlich speichert der Controller seine Wiederanlaufabsicht und Kanonenausrichtung. Nach der Zielinitialisierung wird das Lager wieder zusammengesetzt und die gespeicherte Gier-/Nickausrichtung übernommen. Dieselbe Behandlung gilt nach einer Rückabwicklung.

Die Entity-UUID einer neu zusammengesetzten Kanone kann wechseln. Die Zusage betrifft den erhaltenen Maschinen- und Munitionsbestand, keine unveränderliche Contraption-Entity-Identität.

## Prüfung

```sh
python3 tools/verify_hybrid_trains.py --packaged
```

Die separate Testwelt prüft beide Lagertypen, erhaltene CBC- und Autokanonenmunition sowie tatsächlich abgegebene Schüsse. Die Maschinen werden außerdem durch Portal-Hin-/Rückfahrt, Rückabwicklung und vollständigen Serverneustart geführt. Die Testmods gehören nicht zur veröffentlichten JAR.

[Hybridzüge](Hybridzuege) · [Lager und Untercontraptions](Lager-und-Untercontraptions) · [Zug-Portaltransfer](Zug-Portaltransfer)

Quellen für 1.6.0: [CBC-Adapter](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/train/CannonPortalAdapter.kt), [Maschinenübergabe](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/train/TrainPortalMachines.kt), [Kompatibilitätsprüfung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/HybridTrainCompatibility.kt).

## Abbildung


Tatsächlich ausgeführte Desktop-Prüfung der versionsgebundenen Hybridintegration einschließlich Kanonen-Zielklassen. Keine Aufnahme eines Schusses.

![Create Big Cannons auf Hybridzügen: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-HybridTrainCompatibilityTest.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
