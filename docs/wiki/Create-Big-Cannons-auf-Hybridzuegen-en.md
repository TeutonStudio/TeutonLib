# Create Big Cannons on hybrid trains

[Deutsch](Create-Big-Cannons-auf-Hybridzuegen) · **English** · [Overview](English)

The hybrid train integration permits supported **Create Big Cannons 5.11.7+mc.1.21.1** cannon contraptions with **Ritchie's Projectile Library 2.1.2**, **Create 6.0.10, Sable 2.0.6 and Train Functionality 0.1.47**. The optional integration checks the relevant original classes.

Build and operate the cannon using its native controls. TeutonLib allows its native capture/placement only within the owning carriage plot; it does not unlock arbitrary carriage structure. Use stopped-train `train edit` / `train run` for rebuilding.

Portal travel prepares supported cannon payloads and restores them after destination initialization or rollback, along with their saved operating intent. Ammunition and machine data remain in the actual payload; this does not provide a separate weapons API or change native firing rules.

The historical hybrid test covers assembly, payload preservation and transfer/restart behavior. A successful transfer does not by itself verify every cannon type or firing configuration. See [bearings and child contraptions](Lager-und-Untercontraptions-en).

## Illustration


Executed desktop check of version-gated hybrid integration including cannon target classes; not a firing recording.

![Create Big Cannons on hybrid trains: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-HybridTrainCompatibilityTest.png)


## Sources

- [CannonPortalAdapter.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/train/CannonPortalAdapter.kt)
- [TrainPortalMachines.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/train/TrainPortalMachines.kt)
- [HybridTrainCompatibility.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/HybridTrainCompatibility.kt)

[Media provenance and verification scope](Bildnachweise-en)
