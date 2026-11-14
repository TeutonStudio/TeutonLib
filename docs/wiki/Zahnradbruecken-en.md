# Cogwheel bridges

[Deutsch](Zahnradbruecken) · **English** · [Overview](English)

TeutonLib incorporates **Create Sable Optimization**: visibly meshing Create cogwheels transmit rotation between two distinct rigidly docked Sable sublevels, or between a sublevel and the world.

Requires exactly **Create 6.0.10, Sable 2.0.6 and Simulated 1.3.2**. Remove the separate `cso` JAR.

1. Put a native Create drive on one side and a consumer on the other.
2. Align appropriate cogwheels so their projected world positions visibly mesh.
3. Lock both Docking Connectors.

Small gears mesh on parallel axes; large-to-small uses −2, small-to-large −0.5. Perpendicular large-gear geometry follows Create. Create retains speed, direction, stress, loop and overstress behavior. The client corrects tooth phase. Shafts, belts and pumps do not become additional bridges.

`[modules.createSableOptimization] enabled = true` is the default. `/teutonlib cso status` reports activation; `enable` / `disable` require OP level 2 and save the value. Disabling removes virtual links and recalculates native networks. Re-enabling rechecks loaded gears and locked docks in the normal ten-tick cycle, without restarting the world. Undocking, removal and unload clean up stale bridges. [Analog docks](Analoge-Dockingports-en) are independent.

## Illustration


Historical gameplay capture of the now-integrated CSO module: drive and cogwheels at a dock; not a fresh TeutonLib acceptance test.

![Cogwheel bridges: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/cogwheel-bridge-historical.png)


## Sources

- [CsoCompatibility.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/CsoCompatibility.kt)
- [GearGeometry.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/cso/bridge/GearGeometry.kt)
- [GearBridgeManager.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/cso/bridge/GearBridgeManager.kt)
- [CsoRuntime.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/CsoRuntime.kt)

[Media provenance and verification scope](Bildnachweise-en)
