# Configuration and commands

[Deutsch](Konfiguration-und-Befehle) · **English** · [Overview](English)

Open **Mods → TeutonLib → Config** to use NeoForge's built-in editor. Configured is not required. Labels and help are available in German and English.

Client options live in `config/teutonlib-client.toml`; world/server options in `<world>/serverconfig/teutonlib-server.toml`. A global server config is only a template when creating a new world. The editor saves when leaving each page; NeoForge's enabled file watcher reloads changes while playing. Remote-server and LAN server options are locked in the editor: edit the server's file. No world config is loaded at the title screen.

| Section / key | Default | Meaning |
| --- | --- | --- |
| `modules.createSableOptimization.enabled` | `true` | Cogwheel bridges |
| `compat.simulated.enableAnalogDocking` | `true` | Analog connector extension |
| `compat.createNewAge.motorExtensionEnergyTransfer` | `true` | FE input through motor extensions |
| `compat.peripheralLink.range` | `256` | World blocks, 1–30,000,000 |
| `compat.peripheralLink.maxLinksPerNetwork` | `64` | Active links, 1–4096 |
| `compat.hosePulley.entityHeadMode` | `"ALWAYS"` | `ALWAYS`, `SABLE_ONLY`, `NEVER` |
| `compat.hosePulley.springStiffness` | `10000.0` | Hose spring stiffness |
| `compat.hosePulley.damping` | `2000.0` | Axial damping |
| `compat.hosePulley.breakForce` | `50000.0` | Coupling holding force |
| `compat.hosePulley.breakDelaySeconds` | `0.25` | Sustained overload before release |
| `trains.hybridByDefault` | `true` | Station plots and hybrid running mode |

Client `[computerUi] enabled = true` controls the extra native CC tabs. Disabling them leaves Lua, stored channels and links available. Addon workspaces are controlled by their addon.

`/teutonlib cso status` is available to everyone. `cso enable` and `cso disable` save the setting and require OP level 2. `/teutonlib train status`, `edit` and `run` also require level 2 and act on the **whole nearest loaded train within 32 blocks**. All carriages must be loaded; stop before editing. Mode changes are blocked during portal transfers. Cheats are needed in single player. Config values do not bypass optional-mod version gates.

Antenna faces are selected in the [Peripheral Link antenna UI](Peripheral-Link-en#antenna-and-locating-endpoints) and saved per anchor, without a global TOML option.

From TeutonLib 1.6.2, mobile JVM options belong to TerrArchitecture: `-Dterrarchitecture.mobile.mode=auto|on|off` and `-Dterrarchitecture.mobile.render.colorMask=true`. The old `teutonlib.mobile.*` names remain aliases there. See [migration and historical settings](Android-und-Zalith-Kompatibilitaet-en).

## Illustration

TeutonLib in the native configuration editor. Server options require an appropriate loaded world.

![Configuration and commands: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-config.png)

## Sources

- [ServerConfig.kt](https://github.com/TeutonStudio/TeutonLib/blob/e6c27c6/src/main/kotlin/de/TeutonStudio/TeutonLib/ServerConfig.kt)
- [ClientConfig.kt](https://github.com/TeutonStudio/TeutonLib/blob/e6c27c6/src/main/kotlin/de/TeutonStudio/TeutonLib/ClientConfig.kt)
- [HybridTrainCommands.kt](https://github.com/TeutonStudio/TeutonLib/blob/e6c27c6/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hybrid/HybridTrainCommands.kt)

[Media provenance and verification scope](Bildnachweise-en)
