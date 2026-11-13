# Radar-Typ und Speicherung

**Deutsch** · [English](Radar-Typ-und-Speicherung-en) · [Übersicht](Home)

TeutonLib 1.6.0 erhält den eigenen Contraption-Typ von Create: Radars beim Speichern und Laden. Die Originalversion meldet ihre Radar-Contraption als gewöhnliches Create-Lager; TeutonLib registriert dafür `teutonlib:radar`, damit der geladene Aufbau wieder eine echte Radar-Contraption wird.

## Voraussetzungen

Geprüft sind **Create: Radars 0.4.9.2-1.21.1**, **Create 6.0.10**, **Sable 2.0.6** und **Create: Train Functionality 0.1.47** auf Minecraft **1.21.1**. Die Radar-Erweiterung setzt die verfügbare Hybridintegration voraus und prüft zusätzlich die Originalklasse anhand ihrer SHA-256-Summe. Immersive Portals ist nur für Portalreisen erforderlich.

Die Funktion verwendet die vorhandenen Radarblöcke und benötigt keinen zusätzlichen Spielerbefehl. Sie ergänzt den gespeicherten Contraption-Typ und diese Radarmerkmale:

| Gespeichertes Merkmal | Bedeutung |
| --- | --- |
| Schüsselzahl | Zahl der erfassten Radarschüsseln |
| Empfänger vorhanden | Empfängerausstattung des Radars |
| Empfängerausrichtung | Gespeicherte Richtung des Empfängers |
| Creative-Status | Creative-Eigenschaft der Radar-Contraption |

Diese Daten stehen im zusätzlichen NBT-Tag `teutonlib:radar`. Die Registrierung und Speicherung sind bei verfügbarer Integration aktiv; sie haben keinen eigenen Configschlüssel. `[trains] hybridByDefault` steuert den Hybridbetrieb, während die Typregistrierung lesbar bleibt.

## Auf Hybridzügen

Das Radar arbeitet auf dem tatsächlichen Sable-Aufbau weiter. Bei einer [Portalübertragung](Zug-Portaltransfer) wird das laufende Lager vorbereitet und anschließend wieder zusammengesetzt. Der wieder geladene Rotor muss seinen Radartyp und seine Controllerzuordnung behalten. Die Integration ersetzt weder Radars Zielerfassung noch dessen Berechnung von Weltkoordinaten.

Die lokale Hybridprüfung erfasst ein tatsächlich betriebenes Radar, Rotation und Nutzlast, Portal-Hin-/Rückfahrt, Rückabwicklung und Weltneustart:

```sh
python3 tools/verify_hybrid_trains.py --packaged
```

Die separate [Radar-Ressourcenreparatur](Create-Radar-Ressourcenreparatur) betrifft Ressourcen der Mod und ist eine andere Funktion.

[Hybridzüge](Hybridzuege) · [Lager und Untercontraptions](Lager-und-Untercontraptions) · [Alle Funktionen](Home)

Quellen für 1.6.0: [Typregistrierung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hybrid/HybridRadarTypes.kt), [Radar-Persistenz](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/hybrid/HybridRadarPersistenceMixin.kt), [Versions- und Klassenprüfung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/HybridTrainCompatibility.kt).

## Abbildung


Echtes Radar auf dem Hybridzug. Das Bild zeigt den Aufbau; den gespeicherten Typ prüft der separat dokumentierte Spieltest.

![Radar-Typ und Speicherung: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/train-radar.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
