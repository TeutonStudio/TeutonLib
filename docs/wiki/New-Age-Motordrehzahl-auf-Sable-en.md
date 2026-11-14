# New Age motor speed on Sable

[Deutsch](New-Age-Motordrehzahl-auf-Sable) · **English** · [Overview](English)

With **Create New Age 1.1.7c, Create 6.0.10 and Sable 2.0.6**, TeutonLib reconnects native motor rotational networks after Sable assembly. Basic, advanced and reinforced motors are supported.

Build and power the motor normally, assemble the construction onto Sable, and change speed or direction with New Age's own controls. The correction uses the real kinetic speed and rebuilds the native connection so attached shafts keep being driven.

There is no extra player block, speed source or dedicated setting. Optional-mod version gates select the adapter automatically. This is separate from [FE input through motor extensions](New-Age-FE-Eingang-en); the energy-transfer toggle is not a general motor-rotation switch. Historical verification covers all three motor variants, speed/direction changes, assembly and native network propagation.

## Illustration


Executed desktop checks of supported motor variants and the version-gated rotation integration; not a new movement recording.

![New Age motor speed on Sable: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-NewAgeMotorCompatibilityTest.png)


## Sources

- [NewAgeMotorCompatibility.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/NewAgeMotorCompatibility.kt)
- [NewAgeMotorRotationMixin.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/newage/NewAgeMotorRotationMixin.kt)
- [release-1.5.13.md](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/release-1.5.13.md)

[Media provenance and verification scope](Bildnachweise-en)
