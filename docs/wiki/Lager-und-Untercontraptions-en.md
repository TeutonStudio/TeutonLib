# Bearings and child contraptions

[Deutsch](Lager-und-Untercontraptions) · **English** · [Overview](English)

Mechanical Create bearings and supported child contraptions can assemble and disassemble their captured payload on hybrid trains. The exception is limited to the captured positions in the **same carriage plot** during the native operation; the rest of the train remains structurally locked.

Requires **Create 6.0.10, Sable 2.0.6, Train Functionality 0.1.47**; Swivel Bearings additionally require **Simulated 1.3.2**. Use normal bearing controls. Stop and enter `/teutonlib train edit` before changing the carriage itself, then `train run` to travel.

A Swivel Bearing retains its own Sable child plot. Its actual parent bearing determines the inherited structure lock; a detached child does not permanently remain part of the train. Missing kinetic sources on restored swivel gears are reconnected through Create after neighbors initialize, without inventing an extra speed source.

Station assembly and portal travel temporarily place supported running payloads back into their parent. Swivel children are handled recursively. Restart intent is saved with the controller and restored after destination initialization or rollback, including swivel target orientation. Reassembled contraption entities may receive new UUIDs. Cargo stays in real inventories. See [hybrid trains](Hybridzuege-en).

## Illustration


Native running bearing payload on the hybrid test train; persistence/restart have separate historical gameplay checks.

![Bearings and child contraptions: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/train-bearings.png)


## Sources

- [HybridTrainRuntime.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hybrid/HybridTrainRuntime.kt)
- [HybridSwivelMixin.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/hybrid/swivel/HybridSwivelMixin.kt)
- [TrainPortalMachines.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/train/TrainPortalMachines.kt)

[Media provenance and verification scope](Bildnachweise-en)
