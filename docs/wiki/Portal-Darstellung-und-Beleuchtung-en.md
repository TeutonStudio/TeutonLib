# Portal rendering and lighting

[Deutsch](Portal-Darstellung-und-Beleuchtung) · **English** · [Overview](English)

TeutonLib projects the existing hybrid train build onto both visible sides of an IP Aeronautics portal and clips it at the portal plane. The integration targets **Create 6.0.10, Sable 2.0.6, Train Functionality 0.1.47, IP Aeronautics 2.6.4 and Flywheel 1.0.6**; Sodium is covered by the documented render checks.

ContraptionLevel running mode reuses cached block sections while updating transforms. During editing, normal Sable rendering applies. The orange **F3+B** bounds visualize the transformed running build.

Lighting is sampled from the world currently visible through that side of the portal, including block/entity and Flywheel paths. Couplings between carriages are projected to the matching side and clipped at the portal plane. Visibility is retained when the train stops, reverses or changes portal side.

Additional visible drawings cost rendering work. The bridge reuses native straddling meshes and avoids scanning the whole build every frame or creating a second simulation. Successful screenshots verify the shown view and configuration; they do not establish performance for every shader or hardware combination. See [portal transfer](Zug-Portaltransfer-en).

## Illustration


Portal clipping, destination-world lighting and ContraptionLevel bounds in the actual test scene.

![Portal rendering and lighting: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/train-portal-source.png)

![Portal rendering and lighting: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/train-portal-destination.png)

![Portal rendering and lighting: 3](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/train-bounds.png)


## Sources

- [train-portal-compatibility.md](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/train-portal-compatibility.md)
- [ContraptionLevel.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/client/train/ContraptionLevel.kt)

[Media provenance and verification scope](Bildnachweise-en)
