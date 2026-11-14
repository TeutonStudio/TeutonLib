# Embedded computer kernel

[Deutsch](Embedded-Computer-Kernel) · **English** · [Overview](English)

`api.computer.hosted.HostedComputer` supplies an actual CC:Tweaked runtime inside an addon's block entity. It does not register a block, installation interaction or access policy for the addon.

```kotlin
val runtime = HostedComputer(
    host = blockEntity,
    installed = computerStack,
    family = ComputerFamily.ADVANCED,
    changed = { blockEntity.setChanged() }
)
runtime.tick()  // server host tick
// At final removal/unload:
runtime.close()
```

`createServerComputer()` allocates an instance/storage ID when needed. `tick()` updates position, inputs, keep-alive, label and power. An unused powered-off host without an ID does not create an instance. `configure` changes CC properties; `refreshInputs` refreshes addon inputs.

`queueEventWhenReady` starts the computer if necessary and buffers events (default maximum 64, oldest dropped on overflow). `prepareMenu(player,usable)` installs the per-player permission check; `createMenu` denies access without that prepared check.

`read` / `write` use addon-defined `HostedComputerPersistenceKeys`; `writeStandardComponents` transfers standard computer item components. The addon must invoke lifecycle and persistence hooks. `close()` clears queued events/menu access and shuts the instance down. Register identity-checked host/workspace providers and optional [peripheral topology](Peripheral-Topologie-und-Attachments-en) for integration. The screenshot identifies an actual consuming addon, not an extra block registered by TeutonLib.

## Illustration


Actual embedded CC computer in the additional Aeroworks addon consuming HostedComputer. TeutonLib registers no such desk block.

![Embedded computer kernel: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/addon-wiki-operator-terminal.png)


## Sources

- [HostedComputer.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/hosted/HostedComputer.kt)
- [ComputerExtensions.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/ComputerExtensions.kt)

[Media provenance and verification scope](Bildnachweise-en)
