# Train portal transfer

[Deutsch](Zug-Portaltransfer) · **English** · [Overview](English)

Supported train stack: **Create 6.0.10, Train Functionality 0.1.47, Sable 2.0.6 and IP Aeronautics 2.6.4**. Install TeutonLib on server and clients. Steam ’n’ Rails support follows the documented versions; optional integrations are version- and hash-gated.

Build a valid native portal track route with sufficient clearance, then drive the train normally. Carriage, existing Sable build, inventories and saved ownership transfer together. Independent ship transfers of train-owned builds stay blocked; ordinary ships keep their native behavior.

The existing payload is rendered clipped on both sides while touching the portal. Supported running bearings, radars and cannon payloads are prepared, transferred and restarted after the destination is ready. Rollback uses the same restoration path. No second ticking CTF build is created. [Docked mobile cargo](Zug-Docking-en) is checked against the whole portal opening.

**Known rider limit:** the documented Aether test still shows approximately **2–2.2 blocks** of position error for **standing** riders during carriage-body handover. A passing seated-rider or rendering test does not establish a fix. Wider destination/portal combinations still require their own testing.

See [rendering and lighting](Portal-Darstellung-und-Beleuchtung-en). Historical test commands and exact evidence remain linked from the German counterpart and release notes.

## Illustration


Actual portal crossing with the same machine payload on source/destination sides; the known standing-rider issue remains.

![Train portal transfer: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/train-portal-source.png)

![Train portal transfer: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/train-portal-destination.png)


## Sources

- [train-portal-compatibility.md](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/train-portal-compatibility.md)
- [TrainPortalTransfers.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/train/TrainPortalTransfers.kt)

[Media provenance and verification scope](Bildnachweise-en)
