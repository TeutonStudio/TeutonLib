# Gyroscopic bearing control

[Deutsch](Gyroskop-Steuerung) · **English** · [Overview](English)

Aeronautics' Gyroscopic Propeller Bearing exposes `gyroscopic_propeller_bearing` to CC:Tweaked. Generic extra methods also apply to subclasses such as Simulated Addition's directional bearing, retaining their existing types/methods. Requires **CC:Tweaked, Aeronautics, Simulated and Sable**; the verified access path is through a **Peripheral Link**. **November 13, 2026 check:** the directly attached native bearing in release 1.6.0 exposes no `setTargetDirection` (`nil`). Direct/wired extra-method support is therefore not verified; use a link and check available methods before calling.

```lua
local b = assert(peripheral.find("gyroscopic_propeller_bearing",
  function(_, p) return type(p.setTargetDirection) == "function" end))
while true do
  b.setTargetDirection({x=0.08, y=1, z=-0.03})
  sleep(0.25)
end
```

`setTargetDirection` requires exactly named finite `x/y/z` fields, rejects zero/non-normalizable vectors, normalizes world direction and transforms it into the current Sable-local frame. Without a sublevel, it uses the vector directly.

`adjustTilt({deltaRoll=1.5,deltaPitch=-0.5})` requires exactly those finite degree values. Roll acts around local +Z, pitch around local +X. Calls increment the active override, otherwise starting from current thrust direction.

Both update one transient override, constrained to the existing **12° cone**. It expires immediately before the **20th server tick** after its last update, restoring native redstone/stabilization. Renew sooner; no NBT persistence and unload clears it. This supplies thrust-direction requests, not full flight stabilization. Use a known name with `peripheral.wrap` for subclasses with another type.

## Illustration


Actual setTargetDirection and adjustTilt Lua calls accepted by the native bearing through a Peripheral Link; this is not a complete flight controller.

![Gyroscopic bearing control: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-gyroscope.png)


## Sources

- [HeliPeripheralIntegration.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/heli/HeliPeripheralIntegration.kt)
- [BearingGenericMethods.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/heli/BearingGenericMethods.kt)
- [BearingControl.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/heli/BearingControl.kt)
- [HeliMath.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/heli/HeliMath.kt)

[Media provenance and verification scope](Bildnachweise-en)
