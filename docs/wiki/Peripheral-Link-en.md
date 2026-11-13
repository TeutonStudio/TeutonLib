# Peripheral Link

[Deutsch](Peripheral-Link) · **English** · [Overview](English)

With **CC:Tweaked and Create**, `teutonlib:peripheral_link` attaches a remote peripheral to a normal or advanced block computer. No receiver block is needed. Supported addon anchors can participate; pockets and turtles cannot be selected.

Both recipes are shapeless: **Brass Casing + Ender Pearl + Transmitter**, or **Display Link + Ender Pearl**.

1. Right-click the target computer with the link item.
2. Place the link against the device to connect; only the attachment-side device is used.
3. Right-click the placed link for status, forwarded API and nickname.

Sneak-use clears held selection. Successful placement clears the remaining stack's selection even in Creative; failed placement retains it. Rebind by breaking, selecting a new target and replacing. Drops retain no identity, binding or nickname. Server checks cover reach, loaded blocks and permissions.

CraftOS autorun extends native `peripheral.getNames/isPresent/getType/hasType/getMethods/call/wrap/find`; local and wired devices remain available. Custom OSes call `require("teutonlib.peripheral_links").install()`. Native link API is `teutonlib.links`. Addresses persist as `cc_aeroworks:link_<uuid>`; that historical prefix **does not require Aeroworks**. Nicknames never change the address.

Default world config `[compat.peripheralLink] range = 256`, `maxLinksPerNetwork = 64`; limits apply per active target owner. Range uses visible world positions including Sable motion, while metadata block positions may be plot coordinates. No chunks are loaded.

Unloading, range loss, another dimension or a missing device detaches; reconnection is automatic, but old wrapped handles remain invalid: find/wrap again after `peripheral` / `peripheral_detach`. Duplicate loaded target UUIDs are permanently blocked in saved world data; new deliberate selection creates a new target ID and requires rebinding. Device transfer, event and mount lifetimes follow the [attachment runtime](Peripheral-Topologie-und-Attachments-en).

## Illustration


Actual Peripheral Link screen with UI verification fixture data; methods/status here are test data, not a live inventory.

![Peripheral Link: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/peripheral-link-ui.png)


## Sources

- [peripheral_link_from_components.json](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/resources/data/teutonlib/recipe/peripheral_link_from_components.json)
- [PeripheralLinkItem.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/PeripheralLinkItem.kt)
- [LinkRegistry.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/LinkRegistry.kt)
- [peripheral_links.lua](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/resources/data/computercraft/lua/rom/modules/main/teutonlib/peripheral_links.lua)

[Media provenance and verification scope](Bildnachweise-en)
