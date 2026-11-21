# FE input through New Age motor extensions

[Deutsch](New-Age-FE-Eingang) · **English** · [Overview](English)

Connect **FE source → motor extension → motor**. Basic and advanced New Age motor extensions accept FE on accessible sides and forward it directly into the motor's existing storage. The extension must sit immediately behind the motor and face in the same direction, as required by native stress boosting.

Tested with **Create New Age 1.1.7c** and **Flux Networks 8.0.0**, covering basic, advanced and reinforced motors. Flux is optional: any supported FE source can supply the extension.

The extension has no separate energy buffer and does not allow extraction. It refuses energy when the motor is missing, unloaded, full or misaligned. Chained extensions and cross-Sable-sublevel connections are outside this feature's scope.

`[compat.createNewAge] motorExtensionEnergyTransfer = true` defaults on. Config reload updates existing source capabilities; native stress amplification remains independent. The historical Flux test exercises real Point detection and transfer against a known network buffer; it does not prove full Plug-to-Point network setup.

**Since TeutonLib 1.6.3:** Flux Points fit their flanges to every accessible motor-extension face. The rear inset is 2/16 block; side flanges follow the stepped casing, trim and horizontal mounting foot. Rendering, selection and collision share the same geometry and account for the neighboring extension’s orientation, including subsequent rotations. Existing saved blockstate properties remain compatible; motor and configuration changes update the connections.

The images below show the original motor arrangement. Dedicated Flux model run 20261114-220349-74040066 (tools/verify_flux_models.py) passed for both extension types in all six orientations, each with five simultaneous Flux inputs. It checks actual baked models, network tint overlays and collision bounds and produces twelve close-up screenshots. A separate geometry test compares every flange patch against the original New Age model resources. Server checks also cover rotating an extension without changing the fitted connection state. This run makes no claims about portal or hose physics.

## Illustration

Actual FE source → motor extension → motor arrangement and existing Flux demonstration.

![FE input through New Age motor extensions: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/motor-extension.png)

![FE input through New Age motor extensions: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/gifs/flux-motor.gif)

## Sources

- [NewAgeMotorEnergy.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/energy/NewAgeMotorEnergy.kt)
- [MotorEnergyInput.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/energy/MotorEnergyInput.kt)
- [ServerConfig.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/ServerConfig.kt)

[Media provenance and verification scope](Bildnachweise-en)
