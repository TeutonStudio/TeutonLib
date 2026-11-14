# FluidLogistics and Factory Logistics

[Deutsch](FluidLogistics-und-Factory-Logistics) · **English** · [Overview](English)

The automatic adapter supports **Create 6.0.10, FluidLogistics 1.3.1 and Factory Logistics / Factory Abstractions 1.4.9**. Factory Abstractions is contained in the Factory Logistics JAR. There is no extra block, command or config switch: use existing ordering and storage networks.

TeutonLib replaces only the two conflicting inventory/promise-queue mixins. Full method signatures select the right overloads. Factory Logistics remains owner of the live shared resource store and promise collection; no second persistent index is created.

Equivalent contained resources are compared across carriers, while different resource components stay distinct. Partial delivery, expiry and forced clearing update the same collection that is saved and counted. Inventory changes invalidate sorted caches; arithmetic uses `long` with Create's infinity limit. Item views and codecs read the common store without overwriting component variants.

Both versions and original-class SHA-256 values must match. Missing/other versions leave the adapter inactive; modified classes with matching versions produce a diagnostic before disabling originals. The [TFMG adapter](FluidLogistics-und-TFMG-en) is independent and can run alongside it. The illustration is explicitly labelled as a compatibility/test result rather than an invented new logistics UI.

## Illustration


Executed desktop version/class contract checks for the shared resource adapter; no new ordering UI is introduced.

![FluidLogistics and Factory Logistics: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-LogisticsCompatibilityTest.png)


## Sources

- [LogisticsCompatibility.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/LogisticsCompatibility.kt)
- [LogisticsResourceAdapter.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/logistics/LogisticsResourceAdapter.kt)
- [logistics-compatibility.md](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/logistics-compatibility.md)

[Media provenance and verification scope](Bildnachweise-en)
