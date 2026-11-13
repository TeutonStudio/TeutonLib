# Station construction

[Deutsch](Bahnhofsbau) · **English** · [Overview](English)

With **Create 6.0.10, Sable 2.0.6 and Train Functionality 0.1.47**, TeutonLib 1.6.0 builds new hybrid trains on stationary Sable plots. Enable `[trains] hybridByDefault = true` in the world's server config. Portals are not required.

1. Set up a Create station on a suitable straight track and choose **Create new train**. A stationary construction plot opens before the first bogey.
2. Place bogeys using Create's normal track interaction. Two bogeys belong to one carriage; the third starts another plot.
3. Build each carriage on its plot. Separate components within that plot stay together during construction.
4. Meet Create's normal control, bogey, spacing and track requirements, then choose **Assemble train**.

Each plot contains real blocks, block entities and inventories. During native assembly they are handed to world positions synchronously; failed assembly returns the remaining blocks and data. Occupied world positions and overlapping plots reject the handover before moving carriages. Keep the construction area clear.

Normal cancellation returns blocks to the world. If that return is obstructed, clear the area; removing the station does not delete an obstructed plot. Unfinished construction and cargo are saved with the world. Disabling hybrid mode affects new sessions; open sessions can finish.

**Development notes:** the local November 12 repair additionally verifies native preview offsets, empty-plot persistence and restored bearing payloads after cancellation. It is not a new published release. [Hybrid running mode](Hybridzuege-en) · [Bearings](Lager-und-Untercontraptions-en).

## Illustration


Historical first-carriage station scene from the 1.6.0 demo; this is not evidence of the November 12 local preview repair.

![Station construction: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/train-construction.png)


## Sources

- [wiki-review-2026-11-13.md](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/wiki-review-2026-11-13.md)
- [StationBuildRuntime.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hybrid/StationBuildRuntime.kt)
- [StationBuildVerification.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/trainPortalTest/kotlin/de/TeutonStudio/TeutonLib/traintest/StationBuildVerification.kt)

[Media provenance and verification scope](Bildnachweise-en)
