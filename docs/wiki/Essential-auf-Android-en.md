# Essential on Android

[Deutsch](Essential-auf-Android) · **English** · [Overview](English)

For **Essential 1.3.10.9** on Android, the local Discord IPC path is treated as optionally unavailable. A correctly typed, safely closeable socket adapter reports `connected=false` instead of trying an unsuitable desktop socket.

The integration is automatic with active mobile detection. Connect, read and write fail with defined `IOException` results; closing is safe. No Discord connection is established and only this local IPC path is affected. There is no extra game panel or config switch.

Essential's staged loader requires a runtime version check in addition to early mixin selection. Only the verified version is adapted; unknown/other versions warn and remain unchanged. Mobile JVM `on/off/auto` applies here too.

The screenshot shows the **actual executed adapter contract test** against the nested Essential socket API. It demonstrates disconnected semantics on the desktop, not an Android Essential session.

## Illustration


The actual Essential socket test checks connected=false, defined errors and safe close. Desktop capture, not an Android gameplay test.

![Essential on Android: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-MobileDependencyCompatibilityTest.png)


## Sources

- [EssentialSocketAdapter.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/compat/EssentialSocketAdapter.kt)
- [EssentialDiscordIpcMixin.java](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/java/de/TeutonStudio/TeutonLib/mixin/mobile/EssentialDiscordIpcMixin.java)

[Media provenance and verification scope](Bildnachweise-en)
