# TeutonLib development

Use Java 21. The project uses Kotlin, Gradle Kotlin DSL, small Java mixins and CC:Tweaked Lua modules. External test drivers use Python. The [wiki](https://github.com/TeutonStudio/TeutonLib/wiki/Entwicklung-und-Pruefungen-en) describes the checks and their scope.

## Local dependencies

Provide original, unmodified mod JARs under `dependencies/compile`, `dependencies/runtime` and `dependencies/verification`. They are not embedded or committed. Load Simulated through the Aeronautics bundle at runtime. KotlinForForge, Create, Aeronautics, Sable and the optional integration fixtures must match the versions in the build script. The detailed [German technical guide](development-de.md) retains the full dependency/profile list and release history.

```sh
./gradlew build
./gradlew runClient
./gradlew runServer
python3 tools/verify_wiki.py
```

Development runtime profiles are `-PcsoRuntime=none`, `create` and `all` (default). Optional test mods are loaded only with their explicit Gradle properties. Gameplay drivers use disposable worlds under `run/smoke`, not personal saves. Run server scenarios sequentially; graphical checks need OpenGL. A compiled test is not an executed test.

## Documentation

`docs/wiki` is mirrored to the separate GitHub Wiki repository. German pages are primary, with matching `-en` pages and an English overview. Every feature page embeds real screenshot/GIF evidence; captions distinguish gameplay, addon consumers, desktop contracts and source inspection. `wiki-media-coverage.json` records provenance. Android desktop checks do not substitute for device captures. Unreleased development is explicitly labelled.

The November 13 review changes documentation only. It does not publish a new mod release or incorporate local gameplay-code changes. Public extension points are the documented provider, context, workspace and client interfaces; private mixin/integration internals are not stable addon APIs.
