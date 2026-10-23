# Peripheral Link

TeutonLib adds `teutonlib:peripheral_link` when **CC:Tweaked and Create** are installed. **CC Aeroworks is optional**: installing it also enables links to embedded control desk computers. Avionics is not a dependency.

Craft a brass casing, an ender pearl, and a transmitter together using a shapeless recipe. The existing shapeless recipe using a Display Link and an ender pearl remains available; both variants have the same material cost. Right-click a normal or advanced CC computer with the item, then place the link against the peripheral. With CC Aeroworks, any member of an Aeroworks control desk row can be selected instead. Only the device on the link's attachment side is exposed. No receiver, frequency, or connection program is needed.

Right-click a placed link to open its status, forwarding details, Lua API, and nickname editor. Sneak-use the item to clear its selection before selecting another target. Break and replace a placed link to change its target. Dropped links do not retain their UUID, pairing, or nickname. Successful placement clears the selected target from the remaining item stack, including in creative mode. Failed placement preserves the selection. Pocket computers and turtles are not selectable targets.

The nickname is stored on the link and used by the connected computer's Sources view. It can be edited in either screen without changing the stable Lua address. Names from existing worlds which are still stored on a computer are migrated to the link on its first connection.

## Configuration

The world-specific `serverconfig/teutonlib-server.toml` contains:

```toml
[compat.peripheralLink]
    range = 256
    maxLinksPerNetwork = 64
```

The range boundary is inclusive. The active link limit is shared by all anchors in the same desk network. Configuration reloads take effect during play. Sable distance checks use current world positions, including vehicle movement and rotation. Plot coordinates are used only for block access.

The same central Sable geometry applies to CC:Tweaked's real wireless network. Normal modems
measure distance and altitude-dependent range in visible world coordinates, so `gps.locate()` on
a moving Sable sublevel returns its global world position. GPS hosts, payloads, and trilateration
remain unchanged in CC:Tweaked, and real dimension boundaries remain distance-less.

## Lua

The bundled CraftOS installs the extension automatically and once per boot:

```lua
local inventory = peripheral.find("inventory")
local sensor = peripheral.find("gimbal_sensor")
local gyro = peripheral.find("gyroscopic_propeller_bearing")
```

`getNames`, `isPresent`, `getType`, `hasType`, `getMethods`, `call`, `wrap`, and `find` include linked devices. Local sides and wired modems remain available. Wrapped devices retain the normal CraftOS metatable, including `peripheral.getName` and type checks. `find` retains multiple return values and its filter argument.

Custom operating systems which skip CraftOS autorun can install the extension explicitly:

```lua
require("teutonlib.peripheral_links").install()
```

A linked Simulated Optical Sensor additionally provides `getLocalDirection()`. It returns
the laser facing as a local unit vector `{ x = ..., y = ..., z = ... }`. On a Sable
sublevel, this vector uses plot coordinates rather than world coordinates, allowing a
program to derive the world direction from a gimbal sensor's rotation:

```lua
local sensor = assert(peripheral.find("optical_sensor"))
local direction = sensor.getLocalDirection()
print(direction.x, direction.y, direction.z) -- 0  0  1
```

This method is intentionally available through a Peripheral Link only. A stationary
sensor's local coordinate system is identical to the world coordinate system.

A **Create Simulated Directional Gearshift** is exposed as a complete
`directional_gearshift` peripheral through both Peripheral Links and direct CC connections.
`setOutput("left", boolean)` and `setOutput("right", boolean)` control its two inputs, while
`getOutput` reads them. The API also includes the 0/15 aliases `setAnalogOutput` and
`getAnalogOutput`, plus `setLeft`, `setRight`, `setOutputs`, `clear`, and `getStatus`:

```lua
local gearshift = assert(peripheral.find("directional_gearshift"))
gearshift.setOutput("left", true)
gearshift.setOutput("right", false)
```

The native API is registered through `ComputerCraftAPI.registerAPIFactory`; its global name is `teutonlib_links` and its module name is `teutonlib.links`.

Embedded desk computers additionally expose the same bindings through their existing API:

```lua
local sensor = peripherals.find("gimbal_sensor")
local inventories = peripherals.findAll("inventory")
local tree = peripherals.getTree()
```

Desk API return conventions remain unchanged: `find` returns one handle for one match and an address-indexed table for multiple matches; `findAll` always returns a table. Both APIs share each real attachment. The selected desk remains the anchor while the current embedded computer owns the connection. Without an embedded computer, pairing remains saved but inactive.

## Identity and lifecycle

Names persist as `cc_aeroworks:link_<uuid>`, independently of coordinates and CC computer IDs. Wrapped linked devices expose `getPeripheralInfo()` where that name is not already provided by the peripheral itself. Desk tree entries and peripheral info add `connectionType`, `linkPosition`, `targetPosition`, and `targetId`. Adjacent desk devices keep their side keys; wireless tree children use their full link names.

The connection registry tracks loaded links and anchors. It does not load chunks or scan for nearby links. Full CC peripheral resolution includes generic inventories and tanks. Results are cached until capability or neighbour changes, and validation uses already-loaded chunk tables to avoid refreshing chunk tickets.

CC's method system preserves main-thread requirements, yielding calls, results, errors, events, and mounts. Inventories and tanks can transfer between links and already-connected local or wired devices. No transmission delay or device reset commands are added.

Removal, unloading, dimension mismatch, excessive range, or a missing peripheral disconnects the binding. Availability restores it automatically. Old handles remain invalid after reconnection. Standard events are `peripheral` and `peripheral_detach`; desk networks also retain their existing attachment events.

UUIDs survive saves, restarts, and Sable block assembly/moves when block data is retained. Duplicate loaded target UUIDs are persisted as conflicts and remain disabled after unloading or restarting. Explicitly selecting the affected target again assigns a new identity; its links must then be paired again.

## Verification

```bash
./gradlew build --offline
lua tools/tests/peripheral_links.lua src/main/resources/data/computercraft/lua/rom/modules/main/teutonlib/peripheral_links.lua
python3 tools/verify_peripheral_links.py cc
python3 tools/verify_peripheral_links.py console
```

Run the server profiles sequentially. The first uses Create and CC without Sable or CC Aeroworks. The second also loads Sable and CC Aeroworks. It exercises a real CraftOS `gps.locate()` against four global hosts before and after moving the Sable sublevel, plus dimension isolation. They use disposable worlds under `run/peripheral`, a localhost-only RCON endpoint, and a development verification mod excluded from the release JAR. Results are saved under `build/reports/smoke`.

For exact supported dependency versions, implementation details, and additional client/device acceptance checks, see the [German documentation](peripheral-link.md).
