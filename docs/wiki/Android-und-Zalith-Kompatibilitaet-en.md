# Android and Zalith compatibility

[Deutsch](Android-und-Zalith-Kompatibilitaet) · **English** · [Overview](English)

> **Migration in TeutonLib 1.6.2:** These adapters now belong to TerrArchitecture’s [`mobilecompat` module](https://github.com/TeutonStudio/TerrArchitecture/blob/master/src/main/kotlin/de/TeutonStudio/mobilecompat/SPECS.md). TeutonLib no longer ships them. The description and desktop evidence below refer to earlier TeutonLib releases. Existing `teutonlib.mobile.*` options remain aliases in TerrArchitecture.

## Migration from 1.6.2

Build TeutonLib and TerrArchitecture from matching source states and replace both in the pack. Current launcher arguments are `-Dterrarchitecture.mobile.mode=auto`, `on` or `off`, and `-Dterrarchitecture.mobile.render.colorMask=true` for the optional color-mask fallback. The old names remain aliases in TerrArchitecture. Restart the game after changing them.

This migration is included in 1.6.2. Android detection, mobile mixins and Essential socket API extraction have been removed from the current TeutonLib build. [Resource repairs](Ressourcenreparaturen-en) remain in TeutonLib independently.

## Historical TeutonLib behavior through 1.6.1

TeutonLib selects narrowly versioned compatibility paths on Android before mixin selection. Explicit Android/Dalvik JVM markers, both `ANDROID_ROOT` and `ANDROID_DATA`, or typical Android directories trigger detection. Linux or aarch64 alone do not.

Launcher JVM options: `-Dteutonlib.mobile.mode=auto` (default), `on` (force detection), `off` (disable mobile changes). Unknown values warn and behave as auto. Changes require restart. Forcing detection does not bypass mod-version gates.

| Integration | Verified version |
| --- | --- |
| [Ponder/Catnip buffers](Ponder-und-Catnip-auf-Android-en) | 1.0.82+mc1.21.1 |
| [PowerGrid Java solver](PowerGrid-auf-Android-en) | 0.5.5.1 |
| [Essential optional Discord IPC](Essential-auf-Android-en) | 1.3.10.9 |
| [Immersive Melodies hardware MIDI](Immersive-Melodies-auf-Android-en) | 0.6.4+1.21.1 |
| [Visual Recipe Editor backup](Visual-Recipe-Editor-auf-Android-en) | 1.2.1 |

C2ME Native Math is not forced; its own Java fallback/protection remains. Resource repairs are independent of mobile detection. `TeutonLib/Compatibility` logs active fallbacks and unverified versions, limiting identical repeats per run.

**Evidence scope:** the illustrated detection/signature checks were run on a desktop. They do not demonstrate Android graphics-driver behavior or constitute a recording from a Zalith device. No general compatibility promise is made for arbitrary mods/versions.

## Illustration

Executed desktop Android-detection tests. This is not an Android/Zalith device capture.

![Android and Zalith compatibility: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-MobileEnvironmentTest.png)

## Sources

- [MobileEnvironment.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/compat/MobileEnvironment.kt)
- [CsoMixinPlugin.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/CsoMixinPlugin.kt)
- [release-1.5.3.md](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/release-1.5.3.md)

[Media provenance and verification scope](Bildnachweise-en)
