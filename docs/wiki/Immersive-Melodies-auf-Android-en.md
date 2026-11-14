# Immersive Melodies on Android

[Deutsch](Immersive-Melodies-auf-Android) · **English** · [Overview](English)

> **Migration in TeutonLib 1.6.2:** These adapters now belong to TerrArchitecture’s [`mobilecompat` module](https://github.com/TeutonStudio/TerrArchitecture/blob/master/src/main/kotlin/de/TeutonStudio/mobilecompat/SPECS.md). TeutonLib no longer ships them. The description and desktop evidence below refer to earlier TeutonLib releases. Existing `teutonlib.mobile.*` options remain aliases in TerrArchitecture.

## Historical TeutonLib behavior through 1.6.1

For exactly **Immersive Melodies 0.6.4+1.21.1** on detected Android, TeutonLib skips only **hardware MIDI device discovery** by stopping the hardware listener before enumeration.

MIDI files, instruments and Minecraft sound playback remain available. Use them normally; no new block or config entry is added. The compatibility log reports unavailable hardware MIDI once.

Other versions do not receive this mixin. `-Dteutonlib.mobile.mode=off` disables mobile changes before startup; `on` forces mobile selection but not the version gate.

The illustration shows the real listener-signature compatibility check on desktop. It does not claim to record instrument playback or hardware MIDI operation on an Android device.

## Illustration

Executed desktop check of the exact supported target methods. Not a functional validation on an Android device.

![Immersive Melodies on Android: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-MobileDependencyCompatibilityTest.png)

## Sources

- [ImmersiveMelodiesMidiMixin.java](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/java/de/TeutonStudio/TeutonLib/mixin/mobile/ImmersiveMelodiesMidiMixin.java)
- [CsoMixinPlugin.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/CsoMixinPlugin.kt)

[Media provenance and verification scope](Bildnachweise-en)
