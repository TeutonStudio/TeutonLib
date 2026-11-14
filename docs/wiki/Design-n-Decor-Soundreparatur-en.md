# Design 'n' Decor sound resource

[Deutsch](Design-n-Decor-Soundreparatur) · **English** · [Overview](English)

For exactly **Design 'n' Decor 2.2b**, the known hash-matched `dndecor:sounds.json` is replaced with an empty JSON object `{}` while reading its client resource pack.

The broken definitions from that one file are therefore not registered. The rule **does not create replacement sounds**. It activates automatically on desktop and Android, with no extra command or setting, leaving the original JAR unchanged.

Only the exact mod ID/version/path/hash matches. Other versions, altered resources and user-pack sound definitions are not broadly replaced. Hash mismatch keeps original bytes and logs a warning.

The illustration shows the executed transform test confirming that the repaired resource is an empty JSON object. It is not an audio playback recording.

## Illustration


Actually executed desktop checks of original file hashes and concrete resource rules. Tests identify the affected resources.

![Design 'n' Decor sound resource: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-ResourceCompatibilityTest.png)


## Sources

- [ResourceCompatibility.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/compat/resources/ResourceCompatibility.kt)

[Media provenance and verification scope](Bildnachweise-en)
