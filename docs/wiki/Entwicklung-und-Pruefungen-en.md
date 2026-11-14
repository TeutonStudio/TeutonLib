# Development and verification

[Deutsch](Entwicklung-und-Pruefungen) · **English** · [Overview](English)

TeutonLib uses Kotlin, Gradle Kotlin DSL and Java 21, with small Java mixins where dependency isolation/static hooks help. CC runtime modules use Lua; external verification drivers use Python. The documentation baseline is **1.6.1, source commit `e6c27c6`**; local changes are labelled on their guides.

Provide unmodified local mod JARs under `dependencies/compile`, `runtime` and `verification`. They are intentionally not versioned. The [development guide](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/development-en.md) explains profiles and dependencies. Load Simulated through Aeronautics' bundle at runtime to avoid duplicate mod IDs.

```sh
./gradlew build releaseBundle --offline
python3 tools/verify_release.py
python3 tools/verify_wiki.py
```

Build compiles the example and runs JUnit. Release verification checks artifacts, metadata, licenses, dependencies and ZIP identity; documentation-only edits do not require publishing another release.

Existing opt-in drivers cover minimal/Create/full starts, peripherals, graphics, hose coupling, Mekanism models, hybrid trains and portals. They use disposable local worlds under `run/smoke`; do not run them against personal saves. Graphics checks need an OpenGL desktop. Test mods are not shipped. Run server checks sequentially.

| Area | Driver |
| --- | --- |
| Minimal/Create/full server start | `python3 tools/verify_server.py none` / `create` / `all` |
| Packaged hybrid trains | `python3 tools/verify_hybrid_trains.py --packaged` |
| Packaged train portals | `python3 tools/verify_train_portals.py --packaged` |
| Hose coupling | `python3 tools/verify_hose_connector.py` |
| Hose head rendering | `python3 tools/verify_hose_pulley_client.py` |
| Cable routing/overload release (development) | `python3 tools/verify_hose_cable.py` |
| Two-client cable rendering and Flux model (development) | `python3 tools/verify_hose_cable_client.py` |
| Station construction/empty-plot restart (development) | `python3 tools/verify_station_build.py` |
| Native bogey preview (development) | `python3 tools/verify_station_preview.py` |
| Antenna faces, locating endpoints and Sable rendering | `python3 tools/verify_peripheral_antenna_client.py --sable --visual` |
| Sable tank surfaces (development) | `python3 tools/verify_tank_fluid.py` |
| Mekanism models | `python3 tools/verify_mekanism_models.py` |

**Compiled is not executed.** Historical gameplay checks remain dated in release notes; the current wiki audit distinguishes reused screenshots, fresh captures, desktop contract tests and device-specific gaps.

Listing a driver does not establish that it passed against today's source. In particular, the new [cable client acceptance test](Physische-Schlauchrolle-en#developer-verification-and-limits) is still pending. Mobile Essential API extraction belongs to TerrArchitecture in local development; consult the [current build script](https://github.com/TeutonStudio/TeutonLib/blob/master/build.gradle.kts) for TeutonLib's remaining dependencies.

Wiki sources live in `docs/wiki`. These files and `assets/` form the separate GitHub Wiki repository's contents. Every German guide has an English counterpart, reciprocal links and an embedded screenshot/GIF. Local edits do not publish the Wiki. `verify_wiki.py` checks the inventory, language/internal/heading links, fences, images/provenance and source paths. Release sources stay pinned; unreleased code links use `master`. Check GitHub rendering and image loading after publication. [Media and verification scope](Bildnachweise-en).

## Illustration

Historical November 13, 2026 run: 160 JUnit tests against unchanged 1.6.0 sources, no failures or skipped tests. This is not a test result for 1.6.1 or local changes.

![Development and verification: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-summary.png)

## Sources

- [README.md](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/README.md)
- [build.gradle.kts](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/build.gradle.kts)
- [release-1.6.0.md](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/release-1.6.0.md)
- [release-1.6.1.md](https://github.com/TeutonStudio/TeutonLib/blob/e6c27c6/docs/release-1.6.1.md)
- [computer-ui](https://github.com/TeutonStudio/TeutonLib/tree/v1.6.0/examples/computer-ui)
- [wiki](https://github.com/TeutonStudio/TeutonLib/tree/v1.6.0/docs/wiki)

[Media provenance and verification scope](Bildnachweise-en)
