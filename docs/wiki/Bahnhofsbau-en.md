# Station construction

[Deutsch](Bahnhofsbau) · **English** · [Overview](English)

With **Create 6.0.10, Sable 2.0.6 and Train Functionality 0.1.47**, TeutonLib 1.6.0 builds new hybrid trains on stationary Sable plots. Enable `[trains] hybridByDefault = true` in the world's server config. Portals are not required.

1. Set up a Create station on a suitable straight track and choose **Create new train**. A stationary construction plot opens before the first bogey.
2. Place bogeys using Create's normal track interaction. Two bogeys belong to one carriage; the third starts another plot.
3. Build each carriage on its plot. Separate components within that plot stay together during construction.
4. Meet Create's normal control, bogey, spacing and track requirements, then choose **Assemble train**.

Each plot contains real blocks, block entities and inventories. During native assembly they are handed to world positions synchronously; failed assembly returns the remaining blocks and data. Occupied world positions and overlapping plots reject the handover before moving carriages. Keep the construction area clear.

Normal cancellation returns blocks to the world. If that return is obstructed, clear the area; removing the station does not delete an obstructed plot. Unfinished construction and cargo are saved with the world. Disabling hybrid mode affects new sessions; open sessions can finish.

**Included in 1.6.2:** Station construction originated in 1.6.0. The November 12 repairs fix native preview offsets, empty-plot persistence and restored bearing payloads.

The preview, spacing check and actual placement use Create's native track offsets. Construction plots stay fixed at the station; automatic Sable splitting is suspended while building. Separate blocks within a carriage plot therefore stay together.

Empty plots remain saved before the first bogey and after removing the last block, without a helper block or nonzero mass. Plot identity and cargo survive restart. Prepared bearings returned on cancellation restart with their payload in the normal world.

## Developer verification

```sh
./gradlew build compileTrainPortalTestKotlin --offline
python3 tools/verify_station_build.py
python3 tools/verify_station_preview.py
```

The separate server test covers empty plots, native bogey placement, carriage separation, blocked/failed handover, cargo and running bearings, followed by restart/cancellation checks. The client test compares actual placements with renderer preview positions on four separate track directions. Test mods are not shipped; compiling a test does not establish a successful gameplay run.

[Current station runtime](https://github.com/TeutonStudio/TeutonLib/blob/master/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hybrid/StationBuildRuntime.kt) · [Empty-plot serialization](https://github.com/TeutonStudio/TeutonLib/blob/master/src/main/kotlin/de/TeutonStudio/TeutonLib/mixin/hybrid/HybridBuildSerializationMixin.kt).

 [Hybrid running mode](Hybridzuege-en) · [Bearings](Lager-und-Untercontraptions-en).

## Illustration

Historical first-carriage station scene from the 1.6.0 demo; this is not evidence of the November 12 local preview repair.

![Station construction: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/train-construction.png)

## Sources

- [wiki-review-2026-11-13.md](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/wiki-review-2026-11-13.md)
- [StationBuildRuntime.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hybrid/StationBuildRuntime.kt)
- [StationBuildVerification.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/trainPortalTest/kotlin/de/TeutonStudio/TeutonLib/traintest/StationBuildVerification.kt)

[Media provenance and verification scope](Bildnachweise-en)
