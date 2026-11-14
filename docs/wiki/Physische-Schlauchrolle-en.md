# Physical hose pulley

[Deutsch](Physische-Schlauchrolle) · **English** · [Overview](English)

TeutonLib incorporates **SableSchlauch / Hose Pulley Sable Bridge** for **Create 6.0.10 and Sable 2.0.6**. Remove the separate `hosepulley_sable_bridge` JAR. Install TeutonLib on the server and all clients.

**Updated November 14, 2026:** The base feature is included in the 1.6.1 source baseline. Release 1.6.2 adds wrench/hotbar fixes and cable routing with sag and obstacles. The historical GIF only shows carrying and throwing.

## Using the head

- Right-click an extended head with a Create wrench to carry it.
- Wrench-right-click in the air to throw it, or on an [Item Drain](Schlauchkupplung-am-Item-Drain-en) to couple it.
- Switching away from the wrench drops the head, including switching to another hotbar slot containing a wrench.
- With the hose fully retracted, a normal wrench interaction lets the player ride; sneaking selects carrying instead.

Carrying retains the length limit and does not block interaction with a block behind the head. The head is fireproof, including in lava. Gravity and collision act on it; moving or rotated Sable constructions use the projected world anchor.

Fluid intake/output uses the actual world block at the bottom of the living head, including sideways deflection. Native Create source search, quantities and simulation rules remain. Missing or unloaded heads pause world-fluid intake and output.

## Configuration

In `<world>/serverconfig/teutonlib-server.toml`:

```toml
[compat.hosePulley]
entityHeadMode = "ALWAYS"
```

| Value | Behavior |
| --- | --- |
| `ALWAYS` | Physical heads on all pulleys; default |
| `SABLE_ONLY` | Physical heads only on Sable sublevels |
| `NEVER` | Native Create hoses without entity heads |

The server reloads and synchronizes the mode. Rendering, length limit and fluid targeting switch together; existing pulley tank contents are retained. Reserved UUIDs prevent duplicate heads when entities are unloaded.

## Cable routing and persistence

The physical hose has a fixed **256-block maximum**, independent of ground distance, blocks below the pulley and the lower build limit. The head still collides with obstacles.

**Unreleased:** Server cable simulation adds sag, inertia and collisions with blocks and Sable constructions. Obstacle bends consume cable length and determine endpoint tension. Blocked retraction builds tension; solid obstacles are not penetrated. Self-collision and knotting are excluded.

Pulley/head UUIDs survive unloading and restarts. Carrying ends after restart; removing a loaded pulley removes its head. Clients receive the shared segment route at most ten times per second and interpolate it. Rendering uses Create's existing models and is designed to keep the cable visible outside head-entity tracking range.

## Developer verification and limits

```sh
python3 tools/verify_hose_pulley.py
python3 tools/verify_hose_pulley_client.py
python3 tools/verify_hose_cable.py
python3 tools/verify_hose_cable_client.py
```

The separate cable server test passed on November 14: routing/collision samples, segment NBT roundtrip with endpoint identities, blocked retraction and overload release. Later client run `20261114-203614-8e820559` produced six passing partial captures: sag, Flux model and a long cable without a tracked head, each for two observers. Compared cable points matched exactly. The overall run still failed when the subsequent untracked-chunk pause check timed out. The images below establish the pictured cases, not complete client acceptance.

[Current physics implementation](https://github.com/TeutonStudio/TeutonLib/blob/master/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/sable/HoseCablePhysics.kt) · [Development scope and test drivers](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/sable-hose-pulley.md).

## Cable development captures

Sag and routing over an obstacle during development of 1.6.2:

![Cable routing over an obstacle](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/hose-cable-development.png)

In this partial case, the long cable remains visible without the head entity being tracked:

![Long cable without a tracked head](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/hose-cable-long-development.png)

Unmodified images from the partially successful client run described above, not complete acceptance of the new cable physics.

## Historical illustration

Actual gameplay: carry and throw the hose head with a wrench.

![Physical hose pulley: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/gifs/hose-wrench.gif)

## Sources

- [HoseHeadEntity.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hose/HoseHeadEntity.kt)
- [HosePulleyCompatibility.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/HosePulleyCompatibility.kt)
- [sable-hose-pulley.md](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/sable-hose-pulley.md)

[Media provenance and verification scope](Bildnachweise-en)
