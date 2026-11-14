# Create Radar resource repair

[Deutsch](Create-Radar-Ressourcenreparatur) · **English** · [Overview](English)

For exactly **Create Radar 0.4.9.2-1.21.1**, TeutonLib filters eight verified zero-byte files: blockstates for `radome`, `shield_jammer`, `siren`, `sky_radar`, `smart_mount`, `sonar_bearing`, `sonar_panel`, plus `models/block/cannon_mount_radar.json`.

Each rule requires the empty-file SHA-256 and the exact resource path. Normal nonempty models remain available. This prevents the resource loader from parsing those broken empty JSON files; it does not add missing blocks or replace radar gameplay.

The repair is automatic on desktop and Android, with no extra setting. JARs remain unchanged. Other versions, fixed files and user packs are not filtered; mismatch or failure retains the original resource and logs a diagnostic.

The screenshot shows the actual test that matches all eight original empty resources and rejects an unverified version. [Radar persistence](Radar-Typ-und-Speicherung-en) is a separate train integration.

## Illustration


Actually executed desktop checks of original file hashes and concrete resource rules. Tests identify the affected resources.

![Create Radar resource repair: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-ResourceCompatibilityTest.png)


## Sources

- [ResourceCompatibility.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/compat/resources/ResourceCompatibility.kt)

[Media provenance and verification scope](Bildnachweise-en)
