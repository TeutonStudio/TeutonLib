# Helicopter peripherals: gimbal sensor

[Deutsch](Helikopter-Peripherie) · **English** · [Overview](English)

With **CC:Tweaked, Create and Simulated**, the gimbal sensor exposes separate pitch, yaw and roll directly, over cable and via links. Sable orientation is used when mounted on a sublevel.

```lua
local s = assert(peripheral.find("gimbal_sensor"))
print(s.getPitch(), s.getYaw(), s.getRoll())
local rotation = s.getRotation() -- {pitch, yaw, roll}, degrees
```

`getAngles()` and `getAnglesRad()` keep their original two-element `{pitch,roll}` format. The misspelled legacy `getRotaion()` remains an alias for `getRotation()`.

Yaw is world-relative in `[0,360)`: south/+Z 0°, west/−X 90°, north/−Z 180°, east/+X 270°. On Sable it follows real world rotation rather than sensor mounting. A static sensor uses its positive horizontal block axis (Z south, X east). Vertical/non-finite forward directions make yaw and rotation raise a Lua error; use `pcall` where needed.

Create Engineer's Goggles additionally show the Y angle between X and Z: red X, green Y, blue Z; Y has two decimals and degrees, or a gray dash when undefined. This display needs Create, Simulated and Sable, but no running computer. The extension is a sensor interface, not a complete flight controller.

## Illustration


Actual pitch/yaw/roll readback and unchanged two-axis legacy format from the native gimbal sensor.

![Helicopter peripherals: gimbal sensor: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-gimbal.png)


## Sources

- [GimbalSensorPeripheralMixin.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/peripheral/simulated/GimbalSensorPeripheralMixin.kt)
- [GimbalHeading.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/heli/GimbalHeading.kt)
- [GimbalSensorTooltipMixin.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/simulated/GimbalSensorTooltipMixin.kt)
- [HeliMath.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/heli/HeliMath.kt)

[Media provenance and verification scope](Bildnachweise-en)
