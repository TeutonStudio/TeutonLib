# Zahnradbrücken über Sable-Dockingverbindungen

**Deutsch** · [English](Zahnradbruecken-en) · [Übersicht](Home)

Sichtbar ineinandergreifende Create-Zahnräder übertragen Rotation zwischen zwei verschiedenen, starr gekoppelten Sable-/Simulated-Sublevels oder zwischen Welt und Sublevel. TeutonLib enthält dafür die Zahnrad-Brückenlogik von Create Sable Optimization.

## Voraussetzungen

Erforderlich sind exakt **Create 6.0.10, Sable 2.0.6 und Simulated 1.3.2**. Ohne vollständige unterstützte Kombination bleibt die Integration inaktiv. Eine installierte Sable-Version unter 2.0.6 wird bereits durch die Modabhängigkeit abgelehnt. Die separate Mod mit der ID `cso` muss entfernt werden.

## Aufbau

1. Baue einen Create-Antrieb auf der einen und einen Verbraucher auf der anderen Seite auf.
2. Richte passende Zahnräder so aus, dass sie nach dem Andocken sichtbar ineinandergreifen.
3. Verriegele beide Docking Connector.

Kleine Zahnräder greifen mit parallelen Achsen ineinander. Kleine und große Zahnräder übernehmen die üblichen Übersetzungen: groß zu klein mit Faktor −2, klein zu groß mit −0,5. Für zwei große Zahnräder wird die rechtwinklige Create-Geometrie unterstützt. Die Integration berechnet aus den in die Welt projizierten Zahnradpositionen die virtuelle Verbindung.

Create bleibt für Drehzahl, Drehrichtung, Stress, Kreise und Überlastung zuständig. Die Clientintegration korrigiert den Zahnphasenversatz. Wellen, Riemen, Pumpen und andere kinetische Blöcke bilden keine zusätzlichen Brücken. Eine Verbindung setzt zwei unterschiedliche Sublevels beziehungsweise Welt und Sublevel voraus.

## Ein- und Ausschalten

```toml
[modules.createSableOptimization]
enabled = true
```

Der Server synchronisiert den wirksamen Zustand mit allen Clients. Es stehen außerdem folgende Befehle bereit:

| Befehl | Wirkung | Berechtigung |
| --- | --- | --- |
| `/teutonlib cso status` | Config, Aktivierung und fehlende Voraussetzungen anzeigen | alle |
| `/teutonlib cso enable` | einschalten und speichern | OP/Stufe 2 |
| `/teutonlib cso disable` | ausschalten und speichern | OP/Stufe 2 |

Abschalten entfernt die virtuellen Verbindungen und lässt Create die betroffenen Netze neu berechnen. Nach dem Einschalten werden geladene Zahnräder ohne Neustart wieder berücksichtigt. Verriegelte Docks werden im regulären Zehn-Tick-Zyklus neu geprüft. Entdocken, Entfernen eines Zahnrads und Weltentladung räumen die zugehörigen Brücken auf.

## Quellen

- [Versionsgrenzen](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/CsoCompatibility.kt)
- [Zahnradgeometrie](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/cso/bridge/GearGeometry.kt)
- [Brückenverwaltung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/cso/bridge/GearBridgeManager.kt)
- [Laufzeit und Synchronisierung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/CsoRuntime.kt)

[Wiki-Startseite](Home) · [Analoge Dockingports](Analoge-Dockingports)

## Abbildung


Historische Spielaufnahme des inzwischen integrierten CSO-Moduls: Antrieb und Zahnräder an einer Dockingverbindung. Kein neuer TeutonLib-Abnahmetest.

![Zahnradbrücken über Sable-Dockingverbindungen: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/cogwheel-bridge-historical.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
