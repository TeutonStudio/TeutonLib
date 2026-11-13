# Hybrid trains

[Deutsch](Hybridzuege) · **English** · [Overview](English)

TeutonLib keeps a real Sable simulation plot behind each hybrid Create carriage. Machines, inventories, fluids and supported child contraptions continue ticking while the carriage structure is locked for travel.

Requires **Create 6.0.10, Sable 2.0.6 and Train Functionality 0.1.47**. Hybrid mode defaults to `[trains] hybridByDefault = true` and is synchronized to clients. Relevant original classes are hash-checked.

- Build a new train at a [station](Bahnhofsbau-en).
- Stop the train and use `/teutonlib train edit` to edit its real Sable blocks.
- Use `/teutonlib train run` to lock the entire train and permit travel again.
- `/teutonlib train status` reports the current mode. These commands require OP level 2 and all carriages loaded near the command user; transfers block mode changes.

Running mode uses a **ContraptionLevel**: block geometry is cached in 16³ sections; movement reuses buffers, while block changes rebuild affected sections. Block-entity renderers, Flywheel, animated models and fluids remain active. **F3+B** shows the orange transformed bounds. Editing uses Sable's normal renderer.

Mechanical/Clockwork bearings, supported Swivel children, radar and cannons have their own preparation/restart paths. This is not a second ticking copy of a carriage. The Sable physics/simulation costs remain.

Disabling hybrid mode returns new station sessions to normal Create behavior and changes the creative/search visibility of Functional Fob and Chunk Anchor Lever; existing blocks and recipes remain. It is not a general train-data conversion tool. [Portals](Zug-Portaltransfer-en) · [Docking](Zug-Docking-en).

## Illustration


Actual hybrid train with machines and orange ContraptionLevel bounds from the 1.6.0 demo.

![Hybrid trains: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/train-machines.png)

![Hybrid trains: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/train-bounds.png)


## Sources

- [HybridTrainRuntime.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hybrid/HybridTrainRuntime.kt)
- [ContraptionLevel.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/client/train/ContraptionLevel.kt)
- [HybridTrainCompatibility.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/HybridTrainCompatibility.kt)

[Media provenance and verification scope](Bildnachweise-en)
