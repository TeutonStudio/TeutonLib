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

## Antenna and locating endpoints

**Since 1.6.1.** Antennas are part of pairing; existing Lua addresses remain unchanged.

A small antenna appears automatically on the selected computer or desk, on the face clicked during pairing. It needs no additional item or neighbouring block. Existing pairings use the top face. Every enabled antenna face lists the same links paired to that anchor and remains visible while their chunks are unloaded.

Right-click the antenna itself to see link nicknames, connection states and distances. The rest of the computer retains its normal interaction; holding a Peripheral Link gives pairing priority. The link screen shows the antenna position and provides **Locate antenna**.

Both screens use the CC computer frame with a dark interior. The antenna UI’s **Antenna sides** tab lets you toggle each of the six block faces. Multiple faces can be enabled together; at least one must remain enabled. The selection is saved on the computer or selected desk and survives pairing additional links and Sable assembly.

**Locate link** and **Locate antenna** close the screen and display a target marker to that player for 15 seconds, including through obstacles. A new marker replaces the previous one. Loaded targets follow their current world positions, including moving Sable vehicles. Unloaded targets explicitly show their **last known position**. For another dimension, the UI shows dimension and position, without distance or a world marker.

Positions refresh once per second. World coordinates are used for navigation; internal block positions remain available for block access. Known links and last positions persist in world data; confirmed destruction removes entries. Existing worlds populate the catalog as their links load. Neither the overview nor locating endpoints loads remote chunks.

## Troubleshooting

| Observation | Explanation or next step |
| --- | --- |
| Right-click opens the computer. | Aim directly at the antenna. Holding a link item gives pairing priority. |
| An unloaded link remains listed. | The catalog remembers links; an entry does not imply an active device connection. |
| A target shows its last known position. | Its chunk is unloaded. The catalog does not load it. |
| Distance or marker is unavailable. | Check the target dimension; world markers require the same dimension. |
| The final antenna face cannot be disabled. | At least one face must remain enabled. |

## Developer verification

```sh
python3 tools/verify_peripheral_links.py cc
python3 tools/verify_peripheral_links.py console
python3 tools/verify_peripheral_antenna_client.py --sable --visual
```

Server profiles cover unloading, restart and desk rows. The separate graphical test covers face selection, both locating directions and marker expiry. The documented CC/Create/Sable client run passed; the full additional addon stack timed out during connection. [1.6.1 verification scope](https://github.com/TeutonStudio/TeutonLib/blob/e6c27c6/docs/release-1.6.1.md).

## Antenna client-test images

Several antenna faces enabled together; at least one must stay enabled. The screenshot includes Minecraft's normal movement tutorial overlay.

![Selecting antenna faces](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/peripheral-antenna-sides.png)

Antennas on a rotated Sable computer, viewed from below. Mounting follows the local block faces.

![Antennas on Sable](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/peripheral-antenna-sable.png)

Private target marker through a wall in the isolated test world; it expires after 15 seconds.

![Locating a Peripheral Link](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/peripheral-link-marker.png)

These unmodified screenshots come from successful run `2372da0bb22a` with CC:Tweaked, Create and Sable. They were not freshly captured for this Wiki edit.

## Historical Link UI

Actual Peripheral Link screen with UI verification fixture data; methods/status here are test data, not a live inventory.

![Peripheral Link: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/peripheral-link-ui.png)

## Sources

- [peripheral_link_from_components.json](https://github.com/TeutonStudio/TeutonLib/blob/e6c27c6/src/main/resources/data/teutonlib/recipe/peripheral_link_from_components.json)
- [PeripheralLinkItem.kt](https://github.com/TeutonStudio/TeutonLib/blob/e6c27c6/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/PeripheralLinkItem.kt)
- [LinkRegistry.kt](https://github.com/TeutonStudio/TeutonLib/blob/e6c27c6/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/LinkRegistry.kt)
- [peripheral_links.lua](https://github.com/TeutonStudio/TeutonLib/blob/e6c27c6/src/main/resources/data/computercraft/lua/rom/modules/main/teutonlib/peripheral_links.lua)

[Media provenance and verification scope](Bildnachweise-en)
