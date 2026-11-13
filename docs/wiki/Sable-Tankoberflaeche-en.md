# Sable tank fluid surface — development

[Deutsch](Sable-Tankoberflaeche) · **English** · [Overview](English)

**Local development feature, not part of the published 1.6.0 JAR.** The current development build renders Create window-tank fluid surfaces horizontally in world space on tilted Sable constructions.

The surface is clipped against the tilted tank interior while preserving the fill fraction. Unchanged fill level and orientation reuse cached geometry; translation updates position without recalculating the same shape. Existing tank storage/fluid operations remain native.

The screenshots come from a separate graphical development test with upright, sideways and tilted tanks at several fill fractions, plus cache/translation checks. They demonstrate those rendered development states. They are not release-download screenshots. See the [development report](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/sable-tank-fluid.md).

## Illustration


Unreleased development build: actual rendered fluid surfaces in tilted and sideways tanks. Not included in release 1.6.0.

![Sable tank fluid surface — development: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/tank-tilted-development.png)

![Sable tank fluid surface — development: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/tank-sideways-development.png)


## Sources

- [sable-tank-fluid.md](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/sable-tank-fluid.md)

[Media provenance and verification scope](Bildnachweise-en)
