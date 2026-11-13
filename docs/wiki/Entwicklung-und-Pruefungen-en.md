# Development and verification

[Deutsch](Entwicklung-und-Pruefungen) · **English** · [Overview](English)

TeutonLib uses Kotlin, Gradle Kotlin DSL and Java 21, with small Java mixins where dependency isolation/static hooks help. CC runtime modules use Lua; external verification drivers use Python.

Provide unmodified local mod JARs under `dependencies/compile`, `runtime` and `verification`. They are intentionally not versioned. The [development guide](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/development-en.md) explains profiles and dependencies. Load Simulated through Aeronautics' bundle at runtime to avoid duplicate mod IDs.

```sh
./gradlew build releaseBundle --offline
python3 tools/verify_release.py
python3 tools/verify_wiki.py
```

Build compiles the example and runs JUnit. Release verification checks artifacts, metadata, licenses, dependencies and ZIP identity; documentation-only edits do not require publishing another release.

Existing opt-in drivers cover minimal/Create/full starts, peripherals, graphics, hose coupling, Mekanism models, hybrid trains and portals. They use disposable local worlds under `run/smoke`; do not run them against personal saves. Graphics checks need an OpenGL desktop. Test mods are not shipped. Run server checks sequentially.

**Compiled is not executed.** Historical gameplay checks remain dated in release notes; the current wiki audit distinguishes reused screenshots, fresh captures, desktop contract tests and device-specific gaps.

Wiki sources live in `docs/wiki` and are mirrored into the separate GitHub Wiki repository. Every German feature page has an English counterpart, reciprocal language links and an embedded screenshot/GIF. [Media and verification scope](Bildnachweise-en).

## Illustration


Current execution of 160 JUnit tests from unchanged release sources: no failures or skipped tests.

![Development and verification: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-summary.png)


## Sources

- [README.md](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/README.md)
- [build.gradle.kts](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/build.gradle.kts)
- [release-1.6.0.md](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/release-1.6.0.md)
- [computer-ui](https://github.com/TeutonStudio/TeutonLib/tree/v1.6.0/examples/computer-ui)
- [wiki](https://github.com/TeutonStudio/TeutonLib/tree/v1.6.0/docs/wiki)

[Media provenance and verification scope](Bildnachweise-en)
