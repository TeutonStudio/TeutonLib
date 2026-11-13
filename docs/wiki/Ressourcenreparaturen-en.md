# Resource repairs

[Deutsch](Ressourcenreparaturen) · **English** · [Overview](English)

Known broken client resources are handled only when **mod ID, version, path and original SHA-256 all match**. Rules are independent of Android detection and act while reading mod resource packs, without modifying JARs or server data.

| Mod | Version | Repair |
| --- | --- | --- |
| [Create Radar](Create-Radar-Ressourcenreparatur-en) | 0.4.9.2-1.21.1 | Filter eight empty blockstate/model files |
| [EnderIO without Athena](EnderIO-ohne-Athena-en) | 8.2.10-beta | Filter three Athena-only capacitor submodels |
| [NTGL](NTGL-Animationsreparaturen-en) | 3.1.5 | Repair two invalid animation expressions |
| [Design 'n' Decor](Design-n-Decor-Soundreparatur-en) | 2.2b | Replace the known sounds.json with `{}` |

No extra game command or config switch exists. User packs and unknown versions are not broadly filtered. Hash mismatch warns and retains original bytes; read/transform failure also falls back to original content.

[Package metadata diagnostics](Paketmetadaten-Diagnose-en) identify bad `pack.mcmeta` files but do not invent replacements. The illustration shows actual resource-transform tests, not a decorative game scene.

## Illustration


Actually executed desktop checks of original file hashes and concrete resource rules. Tests identify the affected resources.

![Resource repairs: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-ResourceCompatibilityTest.png)


## Sources

- [ResourceCompatibility.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/compat/resources/ResourceCompatibility.kt)
- [ResourcePackLoaderMixin.java](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/java/de/TeutonStudio/TeutonLib/mixin/resource/ResourcePackLoaderMixin.java)
- [release-1.5.3.md](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/release-1.5.3.md)

[Media provenance and verification scope](Bildnachweise-en)
