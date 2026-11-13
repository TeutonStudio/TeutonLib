# Mekanism connection models on Sable

[Deutsch](Mekanism-Anschlussmodelle) · **English** · [Overview](English)

With **Mekanism 10.7.19, Create Aeronautics: Mekanism Compatibility 0.1.21, Sable 2.0.6 and IP Aeronautics 2.6.4**, TeutonLib fixes the receiver/address checks used for pipe model updates on moving Sable constructions.

Build a normal Mekanism pipe network and change push/pull modes with the Configurator, both before and after assembly and while moving. Updates address the stable plot block position so the actual client pipe receives the mode change rather than an unrelated projected location.

No new pipe or player setting is introduced. The adapter activates only for the verified optional-mod combination. The historical separate-server/Sodium-client check compares server/client modes, model data, compiled geometry and directly addressed packets, including configuration changes during motion. The screenshots show actual mounted/moving test pipes; they do not replace a test of an arbitrary existing world.

## Illustration


Actual pipe-model captures after mode change and during Sable movement.

![Mekanism connection models on Sable: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/mekanism-modes.png)

![Mekanism connection models on Sable: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/mekanism-moving.png)


## Sources

- [MekanismModelCompatibility.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/MekanismModelCompatibility.kt)
- [MekanismMountedUpdateMixin.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/mekanism/MekanismMountedUpdateMixin.kt)
- [MekanismPacketTrackingMixin.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/mekanism/MekanismPacketTrackingMixin.kt)

[Media provenance and verification scope](Bildnachweise-en)
