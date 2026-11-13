# Physical hose pulley

[Deutsch](Physische-Schlauchrolle) · **English** · [Overview](English)

TeutonLib incorporates **SableSchlauch / Hose Pulley Sable Bridge** for **Create 6.0.10 and Sable 2.0.6**. Remove the separate `hosepulley_sable_bridge` JAR. TeutonLib belongs on server and clients.

The hose head is a persistent fireproof entity with gravity and collision. Use the Create wrench to pick it up, carry it and throw it. A retracted head travels with its pulley; obstacles, tether length and the world bottom constrain movement. On Sable, the anchor is projected into the visible world. Fluid intake/output uses the actual head position, including sideways deflection and moving trains.

World config `[compat.hosePulley] entityHeadMode` accepts `ALWAYS` (default), `SABLE_ONLY` and `NEVER`; server reload synchronizes the effective mode. Without an entity head, native Create hose behavior and fluid intake apply. The head is fireproof, not a promise that surrounding builds survive lava.

The shown GIF demonstrates carrying and throwing in a test scene. [Item Drain coupling](Schlauchkupplung-am-Item-Drain-en) and ship pumping have separate illustrations. Historical persistence and client tests cover the documented cases. **Development:** the local wrench/hotbar handling repair is labelled separately from released 1.6.0.

## Illustration


Actual gameplay: carry and throw the hose head with a wrench.

![Physical hose pulley: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/gifs/hose-wrench.gif)


## Sources

- [HoseHeadEntity.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hose/HoseHeadEntity.kt)
- [HosePulleyCompatibility.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/HosePulleyCompatibility.kt)
- [sable-hose-pulley.md](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/sable-hose-pulley.md)

[Media provenance and verification scope](Bildnachweise-en)
