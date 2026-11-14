# Sable geometry API

[Deutsch](Sable-Geometrie-API) · **English** · [Overview](English)

The dependency-safe public facade under `api.sable` uses Minecraft types in its signatures. Sable implementation is accessed only after checking that it is loaded.

```kotlin
val worldPosition = SubLevelGeometry.projectToWorld(level, blockPos.center)
val distance = SubLevelGeometry.distance(level, player.position(), blockPos.center)
```

`projectToWorld` projects a possible plot position into visible world space; without Sable it returns the input. `distanceSquared` / `distance` project both positions within one world. They do not check dimensions or load chunks.

`SubLevelInteractionGeometry.withinReach(player,level,position,padding)` uses block-interaction reach plus padding (default 1 block). `mayInteract` asks `level.mayInteract` at the projected world position. Addons still need menu identity, dimension and their own permission checks.

Client-only `SubLevelRenderPose.apply(poseStack,blockEntity,x,y,z,camera,partialTicks)` applies the render projection and returns `Result(worldPosition,usedSubLevel)`. Without Sable it applies camera-relative translation. Keep render classes off dedicated servers. The illustration uses the real [GPS integration](Funk-und-GPS-en), which consumes world projection.

## Illustration


Visible world position read in a real Sable GPS consumer; this does not separately validate every interaction/render helper.

![Sable geometry API: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/addon-wiki-sable-gps.png)


## Sources

- [SubLevelGeometry.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/sable/SubLevelGeometry.kt)
- [SubLevelInteractionGeometry.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/sable/SubLevelInteractionGeometry.kt)
- [SubLevelRenderPose.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/sable/SubLevelRenderPose.kt)

[Media provenance and verification scope](Bildnachweise-en)
