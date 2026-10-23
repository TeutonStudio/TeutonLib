# CC:Tweaked sources and channels

TeutonLib adds **Sources** and **Channels** icon tabs in the native CC sidebar to normal and advanced block computers. CC:Tweaked is sufficient; Create is only required for Peripheral Links. Sources group local, wired and linked devices in collapsible sections without creating new attachments. Select a device to assign or remove a user nickname. This only changes its displayed label: the immutable peripheral address, block name, position, connection type, types and link position remain visible in its expanded details, and its Lua identity is unchanged. Nicknames are saved on the owning computer or control desk. CC Aeroworks 1.4.0 also receives these peripherals in its existing sources directory, preserving display links, GPS and scripts.

The workspace matches the Aeroworks sidebar and CC computer casing: monochrome on normal computers, with restrained colour accents on advanced computers. Both ordinary computers and Aeroworks control desks use the same collapsible workspace lists and contextual actions. **New channel** starts a channel; selecting an existing channel exposes rename, public/private, value and confirmed delete actions. Control desks add their control channels, display links, GPS and scripts. Escape returns to the terminal.

Each source has an info icon on the right. It opens a scrollable API window with actual Lua methods and any registered documentation. Groups and detail rows have no info icon. Escape or the close icon restores the previous list. Wired, wireless and Ender modems appear individually with their address and modem kind; their API windows include connection, known position, dimension, wireless range and interdimensional capability. Wired peripherals remain separate. Inspection never executes a Lua method or sends discovery packets.

Method signatures include argument names, Lua types and return types from registered documentation or annotated Java/Kotlin methods. Internal context parameters are omitted. Dynamic arguments and returns without documentation are marked `unknown`.

The channel editor creates named outputs. Existing side inputs remain visible and can still be defined through Lua.

The separate **Redstone · internal** source category automatically lists all six standard CC Redstone inputs with their current analog values (0–15), matching `redstone.getAnalogInput(side)`. These inputs require neither Drive By Wire nor named input channels. Its **Outputs** section lists existing user-defined output channels, including addon-provided outputs; manage them in the channel tab. This category is also included in addon workspaces.

Set `computerUi.enabled = false` in `config/teutonlib-client.toml` to hide the automatic
CC block-computer tabs and the example screen's tabs. The default is `true`. Reloading
the config updates open screens and restores the terminal; without file watching,
restart the client. Channel data, Lua APIs and Peripheral Links remain available.
Custom addon workspaces, including Aeroworks desk screens, are controlled by their addon.

Each computer supports up to 64 named channels. Names use 1–32 uppercase or lowercase
letters including `ÄÖÜäöüß`, digits, `_`, `.` or `-`; `world` is reserved.
Addon-provided channel groups support the same characters. New channels are private.
Output values are integers from 0 to 15. Inputs read a selected computer side (`front`,
`back`, `left`, `right`, `top`, `bottom`) through DbW. Outputs can be selected with the
DbW cable tool; scrolling cycles channels. Definitions, visibility and output values
survive shutdown, unloading and restart without an automatic reset. Renaming migrates
output cables; deletion removes them and requires a second click in the GUI.

Without DbW, definitions, outputs and remote queries remain usable. Inputs return 0 and the UI marks the backend unavailable. The tested DbW version is 0.3.0.

```lua
local c = require("teutonlib.channels")
c.create("engine", "output")
c.set("engine", 12)
c.setPublic("engine", true)
c.create("sensor", "input", "top")
print(c.read("sensor"))
local own = c.list()
local public, privateCount = c.list(5)
print(c.read(5, "engine"))
print(c.read(5, "known_private_name"))
c.rename("engine", "throttle")
c.remove("sensor")
```

Both running computers need a reachable modem peripheral, locally, via cable or via Peripheral Link. No modem channels or Rednet connections need to be open. Queries have unlimited distance within the same dimension, never load chunks, and fail for unavailable or ambiguous IDs. Remote writes are not supported.

**Private means unlisted, not access protected.** Remote lists contain only public descriptors and a separate private count. Known private names remain readable. Descriptors include `name`, `direction`, `side`, `public`, `value`, `connections` and `provider`. Local `list()` returns all local channels and a second return value of 0.

Run `channels`, `channels 5` or `channels 5 engine` in CraftOS. Remote listings end with the number of hidden private channels. CC Aeroworks retains its own `channels` alias; use `rom/programs/channels.lua` explicitly there. Custom operating systems can require the native module directly. Local mutations queue `teutonlib_channels_changed`.

The public `api.computer` package exposes `ComputerContext`, `SourceProvider`, `ChannelProvider` and immutable view records. Register providers once through `ComputerExtensions`; registrations return unregister handles. Provider channels retain their owner's behavior and are read-only in the shared editor. Use unique names; native definitions take precedence. `registerHosts` lets an addon resolve its own running `IComputerSystem` to the correct loaded host block, enabling the shared runtime and persistent bank. Providers must not create peripheral attachments.

`client.computer.ComputerPanels` can be embedded into an addon `AbstractComputerMenu` screen implementing `ComputerPanels.Owner`. Call `show(0/1/2)` for terminal/sources/channels and `tick()` for refreshes. Manage the addon's terminal visibility separately. Set the computer family with `family(...)`. Use `ComputerSidebarTabs.standardTab(...)` for the top terminal tab and `ComputerSidebarTabs.tab(...)` for the Sources and Channels tabs below it; all three use the native CC casing. Apply `takeRequestedFocus()` after dispatching input events to focus the new-channel name field. Each controller owns its session, rejecting responses for other menus. Server requests validate the actual open menu, permission and computer instance. Existing Aeroworks channels, overrides and script behavior remain owned by that addon; TeutonLib 1.1.0 additionally supports its complete UI through an authoritative addon workspace.

See [the German integration guide](computer-channels.md) for limits, integration details and server verification commands.

## Authoritative addon workspaces (1.1.0)

Register `ComputerWorkspace` through `ComputerExtensions.registerWorkspace(id, backend)` and create the client controller using `ComputerPanels.workspace(...)`. Implement `WorkspacePanels.Owner` on the screen, update terminal visibility on page changes, call `tick()` and apply `takeRequestedFocus()` after input or snapshot delivery. Responses are scoped to menu, controller session, backend and page. Rebuild the controller on resize.

Snapshots contain stable entry IDs, hierarchical children, details, selection context and action descriptions. Commands are revalidated against the current computer and backend model on the server. Addon data, persistence and value semantics remain with the addon. `registerChannelAuthority` prevents a second writable TeutonLib channel bank on addon hosts, while registered channel providers remain readable. `ComputerContext.host()` resolves only loaded hosts belonging to the running instance.
