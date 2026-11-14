# Computer UI: sources and Lua API

[Deutsch](Computer-UI) · **English** · [Overview](English)

Normal and advanced **CC:Tweaked block computers** gain **Sources** and **Channels** tabs in their native sidebar. CC:Tweaked alone is enough for this UI; pockets and turtles do not gain these tabs.

1. Open a block computer and choose **Sources**.
2. Expand local, wired or Peripheral Link groups.
3. Select a device for address, block name, position and attachment information.
4. Open its info icon for available Lua methods. Close/Escape returns to the list, then terminal.

The UI describes already attached devices without attaching them again. Nicknames only change labels, not Lua addresses or identity. Computer-local nicknames live on the computer; link nicknames live on the link and can also be edited in its right-click window. Open views refresh periodically.

**Redstone · internal** shows the six native CC sides and 0–15 analog inputs without Drive by Wire. Modems include available position, range and interdimensional metadata. Inspection calls no device methods and broadcasts no discovery packets. Undocumented dynamic types can appear as `unknown`.

Client `[computerUi] enabled = false` removes the automatic tabs after reload and returns open panels to terminal. Lua, channels and links stay active. Normal computers use monochrome styling; advanced computers use color. [Channel editing](Computer-Kanaele-en) · [Addon workspaces](Computer-Workspace-und-Kernel-en).

## Illustration


Actual CC block computer with TeutonLib Sources tab in the disposable capture world.

![Computer UI: sources and Lua API: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-sources.png)


## Sources

- [StandardComputerWorkspace.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/computer/StandardComputerWorkspace.kt)
- [WorkspacePanels.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/client/computer/WorkspacePanels.kt)
- [ComputerScreenMixin.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/computer/client/ComputerScreenMixin.kt)
- [computer-channels.md](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/computer-channels.md)

[Media provenance and verification scope](Bildnachweise-en)
