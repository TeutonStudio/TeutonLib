# Package metadata diagnostics

[Deutsch](Paketmetadaten-Diagnose) · **English** · [Overview](English)

When a mod resource pack fails to read `pack.mcmeta`, TeutonLib identifies the owning artifact in `TeutonLib/Compatibility`:

- mod ID and version;
- mod file name;
- SHA-256 fingerprint, or `missing` / `unreadable`;
- the original exception and stack trace.

Identical mod/fingerprint messages are limited within one run. Include that log section and the stated mod version in a bug report. No Android detection or config setting is needed.

**The original error is rethrown.** This is attribution, not automatic repair or invented metadata. Separate resource rules only repair explicitly known files.

The illustration is a screenshot of the actual implementation path and its diagnostic fields. It is labelled as code inspection rather than a naturally occurring failure in a personal world.

## Illustration


Screenshot of the actual diagnostic path: filename, mod version, fingerprint and rethrow of the original error. Code inspection, not a staged player crash.

![Package metadata diagnostics: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/metadata-diagnostic.png)

![Package metadata diagnostics: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/metadata-fingerprint.png)


## Sources

- [ResourceCompatibility.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/compat/resources/ResourceCompatibility.kt)
- [CompatibilityDiagnostics.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/compat/CompatibilityDiagnostics.kt)

[Media provenance and verification scope](Bildnachweise-en)
