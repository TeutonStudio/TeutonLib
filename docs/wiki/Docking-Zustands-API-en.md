# Docking state API

[Deutsch](Docking-Zustands-API) · **English** · [Overview](English)

`api.simulated.SimulatedDockingConnector` reads native connector state without placing Simulated types in public addon signatures.

| Method | Result |
| --- | --- |
| `available()` | Mod and connector class available |
| `isConnector(be)` | Supported connector type |
| `isLocked(be)` | Locked |
| `isExtended(be)` / `isRetracted(be)` | Extension state |
| `hasOtherConnector(be)` | Has peer |
| `otherConnector(be)` | Current peer as nullable BlockEntity |

Always check **available and connector type first**. State methods require the proper type and can throw for another block entity. Availability is not an exact-version check. Use already loaded server blocks; do not retain peer references across unload/world changes.

```kotlin
if (SimulatedDockingConnector.available() &&
    SimulatedDockingConnector.isConnector(blockEntity) &&
    SimulatedDockingConnector.isLocked(blockEntity)) {
    val peer = SimulatedDockingConnector.otherConnector(blockEntity)
}
```

This is read-only. Native Simulated owns extension, locking and transfer. Verified base: Create 6.0.10, Sable 2.0.6, Simulated 1.3.2. [Analog docking](Analoge-Dockingports-en).

## Illustration


Actual native locked connection and docking-state readback in an additional Aeroworks consumer.

![Docking state API: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/dock-locked.png)

![Docking state API: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/addon-wiki-dock-telemetry.png)


## Sources

- [SimulatedDockingConnector.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/simulated/SimulatedDockingConnector.kt)
- [SimulatedDockingAccess.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/cso/bridge/SimulatedDockingAccess.kt)

[Media provenance and verification scope](Bildnachweise-en)
