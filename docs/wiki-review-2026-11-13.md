# TeutonLib documentation review — 2026-11-13

## Result and scope

- Concise English README with German/English Wiki, releases, issues, development and example links.
- German-primary Wiki with 52 matching English guide pages, reciprocal language links and German-first navigation.
- All 51 existing guides retained; a separate, explicitly unreleased Sable tank-surface guide added. Installation/development/overview pages are counted as guides, not as additional gameplay features.
- Every guide embeds PNG screenshot or GIF evidence. Captions distinguish gameplay, downstream addon examples, desktop contract results and source inspection.
- SableSchlauch and CreateSableOptimization are documented as integrated functionality; no separate installation is recommended.
- Existing detailed README moved to `development-de.md`; English development entry point added.
- No mod implementation, build settings, version metadata or release assets are changed by this review.

The released baseline is **1.6.0**, commit `15f1a2a`, published November 2, 2026. Local November 12 station/wrench repairs and the tank renderer are not silently described as new released functionality. Their existing local documentation is preserved and the relevant Wiki pages identify the development scope.

## Behavior found during capture

A native directly attached Aeronautics `gyroscopic_propeller_bearing` in the released source configuration exposes no `setTargetDirection` method (Lua receives `nil`). The earlier Wiki promised direct/wired access. The current guide records this limitation, selects a peripheral with the required method, and uses the verified Peripheral Link path.

The linked bearing accepted actual Lua `setTargetDirection` and `adjustTilt` calls. The temporary capture program renews its request every 0.25 seconds. This establishes method acceptance, not a complete flight-control or loaded-flight test. A directly attached gimbal returned actual pitch/yaw/roll, retaining the two-value legacy angle format. A linked optical sensor returned `{x=0,y=0,z=-1}` for its actual facing.

The fresh CC terminal graphics image executes the shipped `teutonlib.svg` and `teutonlib.font` modules, with no CCVisual or CC-Aeroworks installed. Native computer channels were created and assigned values 12/3 using `teutonlib.channels`; their actual UI and source panel were captured. The gearshift readback reflects actual `setOutputs(true,false)` state, not an invented status table.

## Media provenance

The machine-readable [inventory](wiki-media-coverage.json) records each attachment's source, kind and scope and maps every German/English guide to its media. The Wiki's `Bildnachweise` pages explain the same distinctions to readers.

- Historical native test captures illustrate hybrid train machines, bearings, radar, orange bounds, source/destination portal views, fitting/oversized docked cargo, Item Drain coupling and moving Mekanism models.
- Existing real gameplay GIFs illustrate wrench carry/throw and Flux/New Age operation. Existing captioning is retained.
- The historical cogwheel scene comes from the now-integrated CSO predecessor. It is labelled historical and is not presented as a fresh TeutonLib acceptance test.
- Aeroworks screenshots are explicitly identified as a downstream addon consuming TeutonLib's hosted runtime, workspace, groups, docking and world-coordinate integration. They do not imply that TeutonLib registers an Aeroworks desk.
- The historical Peripheral Link UI screenshot uses known fixture data. It is explicitly labelled as UI verification, not live inventory telemetry.
- Tank screenshots are from local, unreleased development.
- Test-report screenshots are actual browser captures of freshly executed Gradle reports. Screenshot output was normalized to PNG format without modifying pixel content.
- Package-metadata illustrations are actual GitHub source views of the release implementation. No artificial player crash is claimed.

## Checks actually executed

In an independent clean checkout with the original local dependency JARs:

```sh
JAVA_HOME=<Java 21> ./gradlew test --offline --no-configuration-cache
```

**160 tests passed, 0 failed, 0 ignored**. The mod source is the unchanged released baseline. The screenshots identify the actual test names; a signature/hash check is not described as a full gameplay verification.

Fresh gameplay screenshots were made with a temporary external Java fixture added only to a disposable `runClient` invocation through a temporary Gradle init script. It used new flat test worlds, a 1280×800 client, German UI and native Minecraft screenshot capture. No personal world was opened. The fixture is not part of the repository or published mod. The final capture run completed successfully and stopped its client.

`python3 tools/verify_wiki.py` checks guide inventory, reciprocal language navigation, internal destinations, code fences, real image headers/dimensions, embedded media coverage/provenance, version-pinned repository paths and the short README. Remote GitHub rendering and image loading are checked after publication.

## Limits

There are **no Android/Zalith device recordings** in this review. Desktop Android-detection and target-signature tests do not establish Android GPU stencil behavior, full PowerGrid circuits, MIDI playback or launcher behavior. The Essential adapter test does execute disconnected socket semantics, on desktop.

Pure library APIs have no independent game GUI. Contract results and actual addon consumers illustrate their usage while avoiding a claim to test every attach/detach failure case. Cannon and New Age compatibility screenshots identify the checked integration contracts; historical gameplay tests remain dated in the original documentation and were not all rerun for this documentation edit.

The known standing-rider position error at Aether carriage handover and possible mass/leverage-dependent docked-load sag remain documented. New illustrations do not resolve those implementation limits. Ordinary SVG `path` parsing remains limited as documented.
