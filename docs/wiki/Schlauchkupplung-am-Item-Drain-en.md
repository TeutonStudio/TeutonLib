# Hose coupling on the Item Drain

[Deutsch](Schlauchkupplung-am-Item-Drain) · **English** · [Overview](English)

Create's existing **Item Drain** accepts a carried physical hose head. Requires **Create 6.0.10 and Sable 2.0.6**, plus the enabled entity-head mode. There is no separate TeutonLib coupling block or recipe.

1. Pick up the hose head with a Create wrench.
2. Wrench-right-click the Item Drain to seat the head.
3. Connect pipes to the five available sides and supply a pump.
4. Use the wrench interaction to release the coupling as described by the native carried-head state.

The four horizontal sides and underside accept pipes. One pump anywhere along the connection is sufficient in either flow direction. Flow is halved across a shared budget for all five connections; normal pump pressure is retained. Hose length does not consume pump range, while pipe sections keep Create's native range checks.

While coupled, the head does not take in or output world fluid. The pulley tank is retained, as are the drain's 1,500 mB tank and normal item/bucket processing. Without a head, the drain works normally and the hose connection is closed.

**Release 1.6.2:** The routed cable sags and bends around obstacles. Bends consume available length and determine the endpoint pull direction. The screenshot shows the original coupling.

A taut hose applies spring/damping force between Sable constructions. Sustained overload releases the coupling. `[compat.hosePulley]` defaults: `springStiffness = 10000.0`, `damping = 2000.0`, `breakForce = 50000.0`, `breakDelaySeconds = 0.25`; these are Sable force units. Reload applies changes.

Slack cable does not push constructions apart; fixed world blocks act as immovable anchors. UUID bindings survive assembly and restarts. Unloaded chunks pause tension and transfer without loading more chunks; both identities must match before resuming. Self-collision and knotting are not simulated.

The screenshot shows the actual seated head model. Ship-pumping GIFs illustrate native fluid movement, not a guarantee that every load/lever configuration will remain coupled.

## Developer verification

```sh
python3 tools/verify_hose_connector.py
python3 tools/verify_hose_cable.py
python3 tools/verify_hose_cable_client.py
```

The connector test uses native Create pumps/tanks for both directions, shared throughput, fluid conservation and release. Cable routing has a separate server test; graphical acceptance is still pending. [Verification limits](Physische-Schlauchrolle-en#developer-verification-and-limits) · [Current development scope](https://github.com/TeutonStudio/TeutonLib/blob/master/docs/sable-hose-pulley.md).

## Illustration

Actual entity head seated in the Item Drain in the separate graphical hose test.

![Hose coupling on the Item Drain: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/hose-seated.png)

## Sources

- [HoseDrainConnection.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hose/HoseDrainConnection.kt)
- [HoseTension.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/hose/HoseTension.kt)
- [sable-hose-pulley.md](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/docs/sable-hose-pulley.md)

[Media provenance and verification scope](Bildnachweise-en)
