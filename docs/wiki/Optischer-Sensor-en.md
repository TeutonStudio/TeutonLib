# Optical sensor: local laser direction

[Deutsch](Optischer-Sensor) · **English** · [Overview](English)

TeutonLib adds `getLocalDirection()` to Simulated's optical sensor **only through a Peripheral Link**. Requires **CC:Tweaked, Create, Simulated and a linked optical sensor**; a direct peripheral does not receive this specific extension.

```lua
local s = assert(peripheral.find("optical_sensor"))
assert(type(s.getLocalDirection) == "function", "Use a Peripheral Link")
local d = s.getLocalDirection()
print(d.x, d.y, d.z)
```

The result is an integer unit vector with named `x/y/z` components, following the sensor's laser-facing direction. Local +Z yields `{x=0,y=0,z=1}`.

On Sable the result uses the plot-local coordinate frame, not the visible world direction of a rotated vehicle. Apply the full sublevel rotation when world direction is needed. Static sensors have matching local/world frames. Existing native measurements are unchanged. A disconnected link invalidates handles; find/wrap the sensor again after reattachment.

## Illustration


Actual local-direction Lua readback through a Peripheral Link and executed checks of all six directions.

![Optical sensor: local laser direction: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-optical.png)

![Optical sensor: local laser direction: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-OpticalSensorPeripheralExtensionsTest.png)


## Sources

- [OpticalSensorPeripheralExtensions.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/heli/OpticalSensorPeripheralExtensions.kt)
- [HeliPeripheralExtensions.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/peripheral/heli/HeliPeripheralExtensions.kt)

[Media provenance and verification scope](Bildnachweise-en)
