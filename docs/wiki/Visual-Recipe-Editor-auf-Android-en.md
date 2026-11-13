# Visual Recipe Editor on Android

[Deutsch](Visual-Recipe-Editor-auf-Android) · **English** · [Overview](English)

With detected Android and exactly **Visual Recipe Editor 1.2.1**, TeutonLib returns immediately from `WorkspaceBackupProcedure.execute()` and logs that the bundled **developer-workspace backup** was skipped.

There is no additional game setting or backup file for players to manage. This is not a world-backup system: the change affects only that bundled developer procedure and does not alter saved worlds or user resources.

Other versions do not activate the mixin. Mobile `on/off/auto` launcher JVM options select the environment before startup without bypassing version checks.

The screenshot shows the verified target method in a desktop compatibility check. It does not imply an Android launcher recording or a test of unrelated backup software.

## Illustration


Executed desktop check of the exact supported target methods. Not a functional validation on an Android device.

![Visual Recipe Editor on Android: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-MobileDependencyCompatibilityTest.png)


## Sources

- [VisualRecipeEditorBackupMixin.java](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/java/de/TeutonStudio/TeutonLib/mixin/mobile/VisualRecipeEditorBackupMixin.java)
- [CsoMixinPlugin.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/CsoMixinPlugin.kt)

[Media provenance and verification scope](Bildnachweise-en)
