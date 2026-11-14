# Analoge Dockingports

**Deutsch** · [English](Analoge-Dockingports-en) · [Übersicht](Home)

TeutonLib steuert die vorhandenen Docking Connector von Create Simulated mit der tatsächlichen Redstone-Stärke. Es entstehen keine zusätzlichen Blöcke oder Transferpuffer. Diese Funktion ist von den [Zahnradbrücken](Zahnradbruecken) unabhängig.

## Voraussetzungen

Die Integration wird ausschließlich mit **Create 6.0.10, Sable 2.0.6 und Simulated 1.3.2** aktiviert. Simulated ist im Aeronautics-Bundle 1.3.2 enthalten. Die separate CSO-Mod darf nicht gleichzeitig geladen werden. TeutonLib gehört auf Server und Clients.

## Bedienung

Lege an beide Anschlüsse passende Redstone-Signale an und richte ihre Vorderseiten gegeneinander aus:

| Signalstärke | Anforderung | Auslegerlänge | Abstand der Basismittelpunkte im selben Blockraster |
| --- | --- | --- | --- |
| 0–5 | OFF | 0 Blöcke | keine Kopplung |
| 6–10 | HALF | 0,5 Blöcke | 2 Blöcke bei HALF/HALF oder HALF/FULL |
| 11–15 | FULL | 1 Block | 3 Blöcke bei FULL/FULL |

Beide Seiten verwenden die kürzere ihrer angeforderten Längen. Ein Anschluss ohne passende Gegenstelle fährt bis zu seiner eigenen Anforderung aus, sofern davor Platz ist. HALF erzeugt keinen zusätzlichen Paired-Docking-Block.

Auf verschiedenen Sable-Konstruktionen werden Position und Ausrichtung in Weltkoordinaten verglichen. Die normale Magnetkraft von Simulated richtet die Anschlüsse aus; die vorhandenen Abstands- und Winkeltoleranzen entscheiden über die Verriegelung. TeutonLib teleportiert keine Konstruktionen.

## Konfiguration und Verhalten

In der weltbezogenen `serverconfig/teutonlib-server.toml`:

```toml
[compat.simulated]
enableAnalogDocking = true
```

Standard ist `true`. Die Änderung gilt während des Spiels. `false` entriegelt die analoge Verbindung beim nächsten Connector-Tick und stellt die ursprüngliche Simulated-Steuerung wieder her.

Ein Wechsel der ausgehandelten Länge trennt zuerst beide Seiten: Physikverbindung, Fluid-, FE-, ComputerCraft- und Zahnradverbindung. Die vorhandenen Inventarpuffer bleiben erhalten. Erst nach erneuter Geometrieprüfung wird wieder verriegelt. Gespeicherte Längen und Dockingzustände werden beim Laden serverseitig neu geprüft.

## Quellen und Prüfung

Die getrennte Testmod prüft unter anderem Signalgrenzen, alle Längenkombinationen, sechs Ausrichtungen, Transfers, NBT, Sable-Constraints und Weltneustarts. Bewegte Konstruktionen und Clientdarstellung benötigen zusätzlich eine Spielprüfung.

- [Versionsprüfung](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/CsoCompatibility.kt)
- [Analoge Dockinglogik](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/cso/docking/AnalogDocking.kt)
- [Ausführliche Geometrie- und Testdokumentation](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/analog-docking.md)

[Wiki-Startseite](Home) · [Zahnradbrücken](Zahnradbruecken)

## Abbildung


Reale native Dockingverbindung und ausgeführte Prüfung der OFF/HALF/FULL-Längen. Das Spielbild allein belegt keine bestimmte Redstone-Stärke.

![Analoge Dockingports: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/dock-locked.png)

![Analoge Dockingports: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-DockingLengthTest.png)

[Bildherkunft und Prüfgrenzen](Bildnachweise)
