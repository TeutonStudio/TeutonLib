# Computer channels

[Deutsch](Computer-Kanaele) · **English** · [Overview](English)

Each native CC block computer supports up to **64 named channels** through `require("teutonlib.channels")` (also `teutonlib_channels`). Outputs store integer 0–15 values; inputs read a named DbW side and cannot be written. Drive by Wire 0.3.0 is optional: without it, definitions, outputs and remote queries work, while DbW inputs read 0.

Names have 1–32 letters (including German umlauts/ß), digits, `_`, `.` or `-`; `world` is reserved. Input sides are `front`, `back`, `left`, `right`, `top`, `bottom`.

```lua
local c = require("teutonlib.channels")
c.create("engine", "output") -- create once, not every startup
c.set("engine", 12)
c.setPublic("engine", true)
c.create("sensor", "input", "top")
print(c.read("sensor"))
for _, entry in ipairs(c.list()) do
  print(entry.name, entry.direction, entry.value)
end
```

`rename`, `remove`, `setPublic`, `list` and `read` manage definitions. UI **New channel → name → Create** makes a private output; rename preserves DbW bindings and deletion needs a second click. Definitions, visibility and output values persist through shutdown/unload. Local changes emit `teutonlib_channels_changed`; remote computers are not kept loaded.

`list(id)` returns public channels plus a private count; `read(id,name)` reads even a private channel if its name is known. **Private is listing visibility, not access control.** Remote reading requires reachable modems on both running computers, in the same dimension, without Rednet or open modem channels. Ambiguous/unavailable IDs error. Remote writing is absent.

CraftOS provides `channels`, `channels 5`, `channels 5 engine`. Aeroworks retains its own alias; explicitly use `rom/programs/channels.lua` there. Addon channels remain read-only in the shared editor and retain addon value ranges.

## Illustration


Actual CC block computer with two channels created through the native Lua API.

![Computer channels: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-channels.png)

![Computer channels: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-channel-ui.png)


## Sources

- [ComputerRuntime.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/computer/ComputerRuntime.kt)
- [ChannelBank.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/computer/ChannelBank.kt)
- [channels.lua](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/resources/data/computercraft/lua/rom/programs/channels.lua)

[Media provenance and verification scope](Bildnachweise-en)
