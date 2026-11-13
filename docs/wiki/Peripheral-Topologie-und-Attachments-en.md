# Peripheral topology and attachments

[Deutsch](Peripheral-Topologie-und-Attachments) · **English** · [Overview](English)

`api.computer.peripheral` describes addon-owned peripheral networks without exposing Aeroworks-specific classes.

`PeripheralHost` carries opaque host identity/address/metadata. `PeripheralEndpoint` carries a unique address, primary/all types, `IPeripheral`, owner key and optional descriptor/metadata. `PeripheralTopology` is a revisioned snapshot; `PeripheralTopologyProvider.snapshot(ComputerContext)` supplies it. Duplicate endpoint addresses are rejected. Keys/revisions remain the addon's responsibility; contexts are server-thread objects and borrowed existing devices must not be reattached.

```kotlin
val attachments = PeripheralAttachmentRuntime(
    system = system,
    context = { computerContext },
    provider = PeripheralTopologyProvider { currentTopology() }
)
attachments.refresh()
val binding = attachments.binding(deviceAddress)
// Final teardown:
attachments.close()
```

The host calls `refresh()` and `close()`. Equivalent bindings survive refresh; changed/removed endpoints detach. Identity comparison includes types, owner and peripheral identity/mutual equality. Unless forced, snapshot refresh occurs at most once per server tick.

`PeripheralDeviceBinding` runs methods through CC's method system, guards main-thread tasks against stale bindings and owns its mounts. Closing is repeatable and cleans mounts even if detach fails. Old handles remain invalid after reconnect. Attach/detach callbacks start after initialization; `invalidate(queueEvents)` closes current bindings. Existing bindings can be reused by `system.mainThreadMonitor` identity.

`PeripheralProviders.registerTargets` and `registerEndpoints` return closeable registrations. Resolve loaded hosts only; an inactive supported anchor should still report `supports=true`. Link metadata keys include `LINK`, `ANCHOR_POSITION`, `DEVICE_POSITION`, `SIDE`. See [Peripheral Link](Peripheral-Link-en).

## Illustration


Actual device view and executed public API signature check. The image does not establish every attach/detach failure case.

![Peripheral topology and attachments: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/wiki-sources.png)

![Peripheral topology and attachments: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-PublicApiCompatibilityTest.png)


## Sources

- [PeripheralTopology.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/peripheral/PeripheralTopology.kt)
- [PeripheralAttachmentRuntime.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/peripheral/PeripheralAttachmentRuntime.kt)
- [PeripheralDeviceBinding.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/peripheral/PeripheralDeviceBinding.kt)
- [PeripheralProviders.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/peripheral/PeripheralProviders.kt)

[Media provenance and verification scope](Bildnachweise-en)
