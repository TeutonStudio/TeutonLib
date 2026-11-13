# Directional Gearshift

[Deutsch](Directional-Gearshift) · **English** · [Overview](English)

Simulated's Directional Gearshift becomes a CC peripheral named `directional_gearshift`, available directly, over cable or through a Peripheral Link. Requires **CC:Tweaked, Create and Simulated**. Native Simulated direction logic is retained.

```lua
local g = assert(peripheral.find("directional_gearshift"))
g.setOutputs(true, false)
print(g.getOutput("left"), g.getAnalogOutput("right"))
g.clear()
```

| Methods | Meaning |
| --- | --- |
| `setOutput(side,bool)`, `getOutput(side)` | Left/right input |
| `setLeft(bool)`, `setRight(bool)` | Change one input |
| `setOutputs(left,right)` | Change both |
| `isLeftPowered()`, `isRightPowered()` | Read inputs |
| `clear()` | Both off |
| `setAnalogOutput(side,0..15)` | 0 off; any positive value on |
| `getAnalogOutput(side)` | 0 or 15, not variable speed |

Sides accept case-insensitive `left`/`right`; invalid sides/strengths error. Changes trigger native kinetic recalculation. `getStatus()` returns powered flags, source facing, block position and class name; `getPosition()` can return a Sable plot address. `getFacing()` returns `"none"` without a source; `getRotationModifier(face)` reads native factors. `help()` lists methods.

Compatibility `getName()` returns an empty string and `setName` does nothing. Use UI/link nicknames instead.

## Illustration


Actual Lua readback after setOutputs(true, false) on the native Directional Gearshift.

![Directional Gearshift: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-gearshift.png)


## Sources

- [DirectionalGearshiftPeripheral.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/simulated/DirectionalGearshiftPeripheral.kt)
- [DirectionalGearshiftIntegration.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/simulated/DirectionalGearshiftIntegration.kt)

[Media provenance and verification scope](Bildnachweise-en)
