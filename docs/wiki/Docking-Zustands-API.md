# Docking-Zustands-API

Addon-Code kann mit `de.TeutonStudio.TeutonLib.api.simulated.SimulatedDockingConnector` Simulated-Dockingzustände lesen, ohne Simulated-Typen in seinen öffentlichen Signaturen zu verwenden.

| Methode | Ergebnis |
| --- | --- |
| `available()` | Simulated-Mod und benötigte Connector-Klasse verfügbar |
| `isConnector(blockEntity)` | BlockEntity ist ein unterstützter Connector |
| `isLocked(blockEntity)` | Verriegelt |
| `isExtended(blockEntity)` | Ausgefahren |
| `isRetracted(blockEntity)` | Eingefahren |
| `hasOtherConnector(blockEntity)` | Gegenstelle vorhanden |
| `otherConnector(blockEntity)` | Gegenstelle als `BlockEntity?` |

Vor jeder Zustandsabfrage zuerst **`available()` und `isConnector(blockEntity)`** prüfen. Die Zustandsmethoden setzen den richtigen Connector-Typ voraus; ein Aufruf mit einer fremden BlockEntity kann eine Ausnahme auslösen. `available()` prüft die Mod-/Klassenverfügbarkeit und ersetzt keine Versionsprüfung. Auf dem Server einen bereits geladenen Block verwenden; eine Gegenstelle nicht dauerhaft über Chunk- oder Weltentladung hinweg zwischenspeichern.

```kotlin
if (SimulatedDockingConnector.available() &&
    SimulatedDockingConnector.isConnector(blockEntity) &&
    SimulatedDockingConnector.isLocked(blockEntity)) {
    val peer = SimulatedDockingConnector.otherConnector(blockEntity)
    // Den aktuellen Zustand der geladenen Gegenstelle auswerten.
}
```

Die API ist lesend. Ausfahren, Verriegelung und Ressourcentransfer bleiben bei Simulated. Geprüfte Basis: Create 6.0.10, Sable 2.0.6 und Simulated 1.3.2.

[Analoge Dockingports](Analoge-Dockingports) · [Zug-Docking](Zug-Docking) · [Alle Funktionen](Home)

Quellen: [Öffentliche Fassade](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/simulated/SimulatedDockingConnector.kt), [Adapter](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/cso/bridge/SimulatedDockingAccess.kt).
