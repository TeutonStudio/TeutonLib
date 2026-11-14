# Lager und Untercontraptions

**Deutsch** · [English](Lager-und-Untercontraptions-en) · [Übersicht](Home)

Mechanische Create-Lager und unterstützte Unter-Contraptions bleiben auf Hybridzügen betriebsfähig. Ihre erfassten Blöcke dürfen sich zusammensetzen und wieder absetzen, während der übrige Wagen im Fahrmodus gegen strukturelle Änderungen gesperrt bleibt.

## Voraussetzungen

Die Basis ist **Create 6.0.10**, **Sable 2.0.6** und **Create: Train Functionality 0.1.47** auf Minecraft **1.21.1**. Für Swivel Bearings ist zusätzlich **Simulated 1.3.2** erforderlich. Die geprüften Klassen werden anhand ihrer Prüfsummen abgesichert. TeutonLib gehört auf Server und Clients.

## Gewöhnliche Create-Untercontraptions

Lager und andere Create-Untercontraptions verwenden ihre native Erfassung und Platzierung. TeutonLib erlaubt dafür nur die konkreten erfassten Blockpositionen innerhalb desselben Hybridwagenplots. Die Ausnahme gilt während des Methodenaufrufs und wird auch bei Fehlern aufgehoben. Sie erlaubt keine beliebigen neuen Blöcke im übrigen Wagen und keinen Blocktransfer über die Wagengrenze.

Der Spieler bedient die Lager weiterhin über ihre normalen Create-Funktionen. Zum Umbauen des Wagens den angehaltenen Zug mit `/teutonlib train edit` freigeben und anschließend mit `/teutonlib train run` wieder sperren. Beide Befehle erfordern Berechtigungsstufe 2 und gelten für den gesamten Zug.

## Swivel Bearings

Ein Swivel Bearing behält seinen eigenen Sable-Kindplot und seine Nutzlast. TeutonLib speichert dessen Elternbeziehung und leitet daraus die Struktursperre des Hybridwagens ab. Die Prüfung folgt der tatsächlichen Lagerverbindung; nach dem Lösen gehört ein ehemaliger Kindplot nicht dauerhaft zum Zug.

Fehlt nach dem Laden am separaten Swivel-Zahnrad die kinetische Quelle, lässt TeutonLib Create die tatsächliche Kinetik nach der Initialisierung der Nachbarn erneut verbinden. Es wird keine zusätzliche künstliche Drehzahlquelle erzeugt.

## Zusammenbau und Portalreisen

Für den [Bahnhofszusammenbau](Bahnhofsbau) und [Portaltransfer](Zug-Portaltransfer) werden laufende mechanische und Clockwork-Lager sowie unterstützte Swivel-Aufbauten kontrolliert abgesetzt. Swivel-Kindplots werden rekursiv in ihre Eltern zurückgesetzt. Die Wiederanlaufabsicht reist in den persistenten Daten des Controllers mit und wird nach der Zielinitialisierung ausgeführt; nach einer Rückabwicklung gilt derselbe Ablauf.

Die Swivel-Zielausrichtung wird wiederhergestellt. Neu zusammengesetzte Contraption-Entities können neue UUIDs erhalten. Fracht verbleibt im echten Maschinenbestand; es entsteht durch den Moduswechsel kein zweiter tickender Aufbau.

## Prüfung

```sh
python3 tools/verify_hybrid_trains.py --packaged
```

Die separate Testwelt prüft laufende mechanische Lager mit Nutzlast, Swivel-Nutzlast samt Kisteninventar, geerbte Struktursperre, Bahnhofszusammenbau, Portal-Hin-/Rückfahrt, Rückabwicklung und Neustart.

[Hybridzüge](Hybridzuege) · [Radar-Typ und Speicherung](Radar-Typ-und-Speicherung) · [Create Big Cannons auf Hybridzügen](Create-Big-Cannons-auf-Hybridzuegen)

Quellen für 1.6.0: [Begrenzte Blockfreigabe](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hybrid/HybridTrainRuntime.kt), [Swivel-Anbindung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/hybrid/swivel/HybridSwivelMixin.kt), [Maschinenübergabe](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/train/TrainPortalMachines.kt).

## Abbildung


Laufende native Lager-Nutzlast auf dem Hybrid-Testzug. Persistenz und Wiederanlauf sind zusätzlich im historischen Spieltest geprüft.

![Lager und Untercontraptions: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/train-bearings.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
