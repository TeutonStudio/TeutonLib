# Analog docking connectors

[Deutsch](Analoge-Dockingports) · **English** · [Overview](English)

With exactly **Create 6.0.10, Sable 2.0.6 and Simulated 1.3.2**, the native Docking Connector follows actual redstone strength. No new connector block or transfer buffer is added.

| Signal | Request | Extension | Base-center spacing in one block grid |
| --- | --- | --- | --- |
| 0–5 | OFF | 0 | no coupling |
| 6–10 | HALF | 0.5 blocks | 2 blocks for HALF/HALF or HALF/FULL |
| 11–15 | FULL | 1 block | 3 blocks for FULL/FULL |

Face the connectors toward each other and signal both sides. Paired connectors negotiate the shorter requested length. Unpaired connectors extend to their own request where space permits. HALF does not create an extra paired block. Across Sable levels, alignment is compared in world coordinates; native magnets and tolerances align the builds, without teleporting them.

`[compat.simulated] enableAnalogDocking = true` defaults on and reloads during play. Turning it off unlocks on the next connector tick and restores native control. Length changes first disconnect physics and fluid, FE, CC and cogwheel bindings; inventory buffers remain. Geometry must pass again before locking. Saved lengths and pairing are revalidated server-side on load.

## Illustration


Actual native dock and executed OFF/HALF/FULL length tests. The gameplay image alone does not establish a particular redstone strength.

![Analog docking connectors: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/dock-locked.png)

![Analog docking connectors: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-DockingLengthTest.png)


## Sources

- [CsoCompatibility.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/CsoCompatibility.kt)
- [AnalogDocking.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/cso/docking/AnalogDocking.kt)
- [analog-docking.md](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/analog-docking.md)

[Media provenance and verification scope](Bildnachweise-en)
