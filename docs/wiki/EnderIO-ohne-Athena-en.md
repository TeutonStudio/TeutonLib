# EnderIO resources without Athena

[Deutsch](EnderIO-ohne-Athena) · **English** · [Overview](English)

For **EnderIO 8.2.10-beta**, only when **Athena is absent**, TeutonLib filters the three verified Athena-only capacitor submodels under `models/block/athena/`: basic, advanced and vibrant capacitor bank parts.

The ordinary non-Athena model path remains. This removes inappropriate resource loading, not an EnderIO block or energy mechanic. With Athena installed the filter is not selected.

Rules require exact mod/version/path/hash. JARs and user packs remain untouched; changed/fixed files are kept with a diagnostic. The repair works automatically and independently of Android, without a new player switch.

The screenshot is the actual resource-byte/hash check for the published files, not an in-game capacitor network measurement.

## Illustration


Actually executed desktop checks of original file hashes and concrete resource rules. Tests identify the affected resources.

![EnderIO resources without Athena: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-ResourceCompatibilityTest.png)


## Sources

- [ResourceCompatibility.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/compat/resources/ResourceCompatibility.kt)

[Media provenance and verification scope](Bildnachweise-en)
