# Computer workspaces and hosted runtime

[Deutsch](Computer-Workspace-und-Kernel) · **English** · [Overview](English)

Addon API package: `de.TeutonStudio.TeutonLib.api.computer`. These interfaces create no standalone computer block.

A `ComputerWorkspace` implements `owns(computer)`, `snapshot(player,computer,page)` and `execute(player,computer,command)`. Register with `ComputerExtensions.registerWorkspace(id,backend)`. Callbacks run on the server thread after menu validation; recheck selected entry, authority and action against current state before mutations.

Snapshots contain title, status, stable hierarchical entries and actions. Commands carry page/action/selection/group/target/text. Decoding limits: depth 8, 8192 entries, 16 actions per list. Standard page numbers: terminal 0, sources 1, channels 2.

Client-only `ComputerWorkspaceClients.register(...)` binds a descriptor to menus, tabs and client actions. Keep client classes off dedicated servers. The compiled UI example demonstrates focus, terminal switching and resize.

`ComputerExtensions` also registers hosts, sources, existing peripheral metadata, method documentation, channels and channel authority. Registrations return closeable handles. Match host identity using CC's `mainThreadMonitor`, not computer ID alone; providers must not recursively query `computer.peripherals()`. Existing devices must not be attached again. Addon channels keep their own persistence/ranges/authority and remain read-only in the shared editor. Use prefixes to avoid collisions.

`sourceEntry(...)` and `SourceApi.encode()` feed the API viewer. [HostedComputer](Embedded-Computer-Kernel-en) owns the optional embedded CC instance, while the addon owns block registration and lifecycle. [Template](https://github.com/TeutonStudio/TeutonLib/tree/master/examples/computer-ui).

## Illustration


Real Aeroworks workspace as an addon consumer plus executed snapshot/command checks. The addon supplies the domain UI.

![Computer workspaces and hosted runtime: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/addon-wiki-operator-terminal.png)

![Computer workspaces and hosted runtime: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-ComputerWorkspaceTest.png)


## Sources

- [ComputerWorkspace.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/ComputerWorkspace.kt)
- [ComputerExtensions.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/ComputerExtensions.kt)
- [ComputerWorkspaceClients.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/client/ComputerWorkspaceClients.kt)
- [HostedComputer.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/hosted/HostedComputer.kt)
- [TemplateComputerScreen.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/examples/computer-ui/src/main/kotlin/example/teutonlib/client/TemplateComputerScreen.kt)

[Media provenance and verification scope](Bildnachweise-en)
