# Hose coupling on the Item Drain

[Deutsch](Schlauchkupplung-am-Item-Drain) · **English** · [Overview](English)

Create's existing **Item Drain** accepts a carried physical hose head. Requires **Create 6.0.10 and Sable 2.0.6**, plus the enabled entity-head mode. There is no separate TeutonLib coupling block or recipe.

1. Pick up the hose head with a Create wrench.
2. Wrench-right-click the Item Drain to seat the head.
3. Connect pipes to the five available sides and supply a pump.
4. Use the wrench interaction to release the coupling as described by the native carried-head state.

The drain's own tank and normal item processing remain available. The connection works in both fluid directions. Flow is halved; pump pressure is retained. Fluid operations use the physical head's actual position.

A taut hose applies spring/damping force between Sable constructions. Sustained overload releases the coupling. `[compat.hosePulley]` defaults: `springStiffness = 10000.0`, `damping = 2000.0`, `breakForce = 50000.0`, `breakDelaySeconds = 0.25`; these are Sable force units. Reload applies changes.

The screenshot shows the actual seated head model. Ship-pumping GIFs illustrate native fluid movement, not a guarantee that every load/lever configuration will remain coupled.

## Illustration


Actual entity head seated in the Item Drain in the separate graphical hose test.

![Hose coupling on the Item Drain: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/hose-seated.png)


## Sources

- [HoseDrainConnection.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hose/HoseDrainConnection.kt)
- [HoseTension.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hose/HoseTension.kt)
- [sable-hose-pulley.md](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/sable-hose-pulley.md)

[Media provenance and verification scope](Bildnachweise-en)
