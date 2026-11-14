# Radio and GPS on Sable

[Deutsch](Funk-und-GPS) · **English** · [Overview](English)

TeutonLib makes CC wireless modem positions, range and `gps.locate()` use visible **world coordinates** for moving/rotating Sable constructions instead of remote plot-storage addresses. Requires **CC:Tweaked and Sable**; native modem/GPS rules still apply.

Build ordinary GPS hosts with coordinates in the visible world. A receiver needs enough reachable, non-degenerate host positions to solve its location; TeutonLib does not replace GPS triangulation or invent hosts.

```lua
local x,y,z = gps.locate(2, false)
if x then print(x,y,z) else print("No GPS fix") end
```

Modem range compares projected positions, including rotation, and uses the visible height rules of the existing modem implementation. The same-world/dimension restrictions and native wireless/interdimensional modem abilities remain. TeutonLib does not force distant chunks to load.

The illustration shows a real Sable-mounted addon computer receiving an actual GPS response from four native hosts. It demonstrates the shared TeutonLib coordinate integration; an Aeroworks terminal is the consuming addon, not a TeutonLib-only block. [Geometry API](Sable-Geometrie-API-en).

## Illustration


Actual GPS fix from a Sable-mounted Aeroworks computer and four GPS hosts. Aeroworks is an additional consumer of TeutonLib integration.

![Radio and GPS on Sable: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/addon-wiki-sable-gps.png)


## Sources

- [SubLevelGeometry.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/sable/SubLevelGeometry.kt)
- [SableSubLevelGeometry.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/sable/SableSubLevelGeometry.kt)
- [WirelessModemRangeMixin.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/computer/WirelessModemRangeMixin.kt)
- [SableVerification.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/peripheralTest/kotlin/de/TeutonStudio/TeutonLib/peripheraltest/SableVerification.kt)

[Media provenance and verification scope](Bildnachweise-en)
