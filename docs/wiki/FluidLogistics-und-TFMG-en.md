# FluidLogistics and TFMG

[Deutsch](FluidLogistics-und-TFMG) · **English** · [Overview](English)

The automatic adapter requires exactly **Create 6.0.10, FluidLogistics 1.3.1 and TFMG 1.2.0**. Factory Logistics is not required. Use native pipes, pumps and tanks; there is no new TeutonLib pump or setting.

TFMG overwrites Create's propagation method, conflicting with FluidLogistics' original injection. TeutonLib replaces only that FluidLogistics mixin while leaving TFMG's own mixin active. It preserves previous pressure, gathers search starts at Create's range boundary and lets FluidLogistics' existing updater continue the search.

TFMG's pump search also recognizes FluidLogistics pumps, including beyond Create's normal reach. Native TFMG mechanical/electric pumps, axis checks and flow rules remain. FluidLogistics owns its range configuration; `fluidPumpEnabled` remains respected by the extended search and does not itself fix the class-transform conflict.

Relevant classes must match verified SHA-256 values as well as versions. Other versions do not activate the replacement; altered matching-version classes fail diagnostically before original code is disabled. The illustration identifies the actually executed compatibility checks and their scope.

## Illustration


Executed desktop checks of pump/propagation target classes; the broader fluid-flow verification is documented separately.

![FluidLogistics and TFMG: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-FluidPropagationCompatibilityTest.png)


## Sources

- [FluidPropagationCompatibility.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/FluidPropagationCompatibility.kt)
- [TfmgFluidPropagatorMixin.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/fluids/TfmgFluidPropagatorMixin.kt)
- [fluid-propagation-compatibility.md](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/fluid-propagation-compatibility.md)

[Media provenance and verification scope](Bildnachweise-en)
