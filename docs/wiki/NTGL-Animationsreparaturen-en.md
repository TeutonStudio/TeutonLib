# NTGL animation repairs

[Deutsch](NTGL-Animationsreparaturen) · **English** · [Overview](English)

For exactly **NTGL 3.1.5**, TeutonLib repairs two verified animation resources as they are read:

| Resource | Exact correction |
| --- | --- |
| `animations/example_chassis.animation.json` | `power_punch` → `body_top` → rotation at `0.5`: Y `-17.5++NaN` becomes numeric `-17.5` |
| `animations/weapons/fatman.animation.json` | `shot` → `bullet` → scale at `0.25`: three invalid `0+` expressions become `[1,1,1]` |

The mod version, path and original SHA-256 must match. This works on desktop and Android without a command/config setting and does not modify the original JAR. Already fixed/changed files are kept. Read or transformation failure preserves original content and logs diagnostics.

The screenshot shows actual tests parsing transformed JSON and checking the resulting numeric values; it does not claim visual verification of every NTGL animation.

## Illustration


Actually executed desktop checks of original file hashes and concrete resource rules. Tests identify the affected resources.

![NTGL animation repairs: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-ResourceCompatibilityTest.png)


## Sources

- [ResourceCompatibility.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/compat/resources/ResourceCompatibility.kt)

[Media provenance and verification scope](Bildnachweise-en)
