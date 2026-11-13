# Radar type and persistence

[Deutsch](Radar-Typ-und-Speicherung) · **English** · [Overview](English)

With **Create: Radars 0.4.9.2-1.21.1**, Create 6.0.10, Sable 2.0.6 and Train Functionality 0.1.47, TeutonLib preserves radar contraptions across saving and loading. Relevant classes are hash-checked; IP Aeronautics is only needed for portal travel.

The original radar identifies its contraption as an ordinary Create bearing. TeutonLib registers `teutonlib:radar` so loading reconstructs an actual radar contraption. Its `teutonlib:radar` NBT also preserves dish count, receiver presence, receiver facing and creative status.

Use the existing radar blocks normally; there is no extra command or config key. Type registration remains readable independently of `[trains] hybridByDefault`, which controls hybrid operation. On a hybrid train the real Sable-mounted radar keeps operating. Portal transfer prepares and restarts the bearing; radar type and controller association must survive. TeutonLib does not replace native target detection or world-coordinate calculations.

The [radar resource repair](Create-Radar-Ressourcenreparatur-en) is a separate compatibility feature.

## Illustration


Actual radar on the hybrid train. The image shows the payload; saved type is checked by the separately documented gameplay test.

![Radar type and persistence: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/train-radar.png)


## Sources

- [HybridRadarTypes.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hybrid/HybridRadarTypes.kt)
- [HybridRadarPersistenceMixin.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/hybrid/HybridRadarPersistenceMixin.kt)
- [HybridTrainCompatibility.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/HybridTrainCompatibility.kt)

[Media provenance and verification scope](Bildnachweise-en)
