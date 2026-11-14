# Sable tank fluid surface — development

[Deutsch](Sable-Tankoberflaeche) · **English** · [Overview](English)

**Included in TeutonLib 1.6.2. Images are from the November 5, 2026 development test.** The current development build renders Create window-tank fluid surfaces horizontally in world space on tilted Sable constructions.

The surface is clipped against the tilted tank interior while preserving the fill fraction. Unchanged fill level and orientation reuse cached geometry; translation updates position without recalculating the same shape. Existing tank storage/fluid operations remain native.

The screenshots come from a separate graphical development test with upright, sideways and tilted tanks at several fill fractions, plus cache/translation checks. They demonstrate those rendered development states. They are not release-download screenshots. See the [development report](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/sable-tank-fluid.md).

## Requirements and use

Requires **Create 6.0.10 and Sable 2.0.6** on Minecraft 1.21.1. The Create window tank must belong to a client-side Sable sublevel. The renderer applies automatically; Train Functionality and Immersive Portals are not required.

Upright, sideways and inverted tanks follow world gravity. Fluids declared lighter than air occupy the upper part instead. Ordinary world tanks and Create boilers retain native rendering.

## Limits and verification

The renderer shows equilibrium under world gravity. It changes no fluid quantities, capacities, pipe connections or physics bodies. Sloshing and acceleration forces are not simulated. This does not establish separate acceptance of portal projection.

```sh
./gradlew test --offline
python3 tools/verify_tank_fluid.py
```

The graphical driver requires OpenGL and a disposable copy of the local `Tank Test` world. The documented November 5 run covered twelve captures with water/chocolate, different fill levels and orientations, plus unchanged/translated cache reuse. The reported 174 JUnit tests belong to that development state. These checks do not measure a general FPS or physics performance gain.

## Illustration

Unreleased development build: actual rendered fluid surfaces in tilted and sideways tanks. Not included in release 1.6.0.

![Sable tank fluid surface — development: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/tank-tilted-development.png)

![Sable tank fluid surface — development: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/tank-sideways-development.png)

## Sources

- [sable-tank-fluid.md](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/sable-tank-fluid.md)

[Media provenance and verification scope](Bildnachweise-en)
