# PowerGrid on Android

[Deutsch](PowerGrid-auf-Android) · **English** · [Overview](English)

For **PowerGrid 0.5.5.1** on detected Android, TeutonLib skips `NativeMNA.tryLoad` so the existing **JavaMNA** solver remains active. It supplies no Android native library and does not replace the Java solver.

Install the exact version and use normal PowerGrid blocks/circuits. There is no added block, game switch or solver UI. The compatibility log reports once that the desktop native path was skipped.

Other versions keep their original behavior. Launcher `-Dteutonlib.mobile.mode=off` disables mobile changes; `on` forces Android selection without bypassing version checks. Restart after changing JVM options.

The screenshot identifies the verified native entry-point signature used by the adapter. It is a **desktop contract check**, not proof that a complete PowerGrid circuit was run on Android.

## Illustration


Executed desktop check of the exact supported target methods. Not a functional validation on an Android device.

![PowerGrid on Android: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-MobileDependencyCompatibilityTest.png)


## Sources

- [PowerGridNativeMixin.java](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/java/de/TeutonStudio/TeutonLib/mixin/mobile/PowerGridNativeMixin.java)
- [CsoMixinPlugin.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/CsoMixinPlugin.kt)

[Media provenance and verification scope](Bildnachweise-en)
