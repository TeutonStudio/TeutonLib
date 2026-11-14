# Train docking

[Deutsch](Zug-Docking) · **English** · [Overview](English)

With **Create 6.0.10, Sable 2.0.6, Train Functionality 0.1.47 and Simulated 1.3.2**, native locked connectors can attach an independent mobile platform to a hybrid carriage. **IP Aeronautics 2.6.4** is additionally required for portals.

Align the native connectors, supply the desired signal and let Simulated align and lock them. The native physics constraint and item, fluid, FE and ComputerCraft transfer paths remain responsible for the connection. Verify both connectors are locked before moving.

For a rectangular portal, the **complete transformed payload**, including attached platforms, must fit. A fitting mobile platform travels with the carriage: connectors separate briefly for transfer, then physics and transfer bindings are restored in the destination. An oversized partner is detached at the first track portal boundary and stays behind. World-fixed docks and independently managed train carriages are not treated as mobile carriage cargo.

If transfer fails, already moved partners are rolled back and rebuilt. Native Sable transactions are used individually; there is no extra atomic group transaction against a server crash between transfers.

**Known limit:** attached loads may sag depending on mass and leverage despite a valid lock/constraint. This was also observed without hybrid mode. The demonstration distinguishes a fitting and an oversized portal case. See [analog ports](Analoge-Dockingports-en) and [portal transfer](Zug-Portaltransfer-en).

## Illustration


Native locking, partner after a fitting portal transfer, and platform left behind when the opening is too small.

![Train docking: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/dock-locked.png)

![Train docking: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/dock-arrived.png)

![Train docking: 3](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/dock-left-behind.png)


## Sources

- [TrainPortalDocking.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/integration/train/TrainPortalDocking.kt)

[Media provenance and verification scope](Bildnachweise-en)
