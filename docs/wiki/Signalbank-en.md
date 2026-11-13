# Signal bank

[Deutsch](Signalbank) · **English** · [Overview](English)

`api.computer.channel.SignalBank` manages addon-defined 0–15 outputs and timed pulses through an addon-provided `SignalBackend`. The addon owns UI, physical output and authority checks.

```kotlin
val signals = SignalBank(
    clock = { level.gameTime },
    authority = { controllerActive },
    backendFactory = { addonBackend },
    changed = { host.setChanged() }
)
signals.addChannel("warning")
signals.tick()
signals.pulse("warning", durationTicks = 20, value = 15)
```

Call `tick()` regularly on the server: it checks authority and resets expired pulses to zero. Losing authority clears values/pulses and blocks further output. `setValue` accepts 0–15; `pulse` requires 1–15 and a duration from 1 to the configured maximum. `reset`, `resetAll` and `close` clear output.

Defaults are 32 channels, at most 1200 pulse ticks and names matching `[a-z][a-z0-9_-]{0,31}`. Callbacks/rules are configurable. The backend implements set/remove/rename, connection counts and full reset; snapshot includes stable definition UUID, name, value and connections.

**Only definitions persist. Values and active pulses do not.** Reloaded definitions start at zero, unlike native CC channel output values. Rename/removal notify the backend so physical mappings remain consistent.

## Illustration


Executed checks of signal authority, pulse expiry and reset after loading. SignalBank has no standalone GUI.

![Signal bank: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-PersistentChannelBanksTest.png)


## Sources

- [SignalBank.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/channel/SignalBank.kt)
- [PersistentChannelBanksTest.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/test/kotlin/de/TeutonStudio/TeutonLib/api/computer/channel/PersistentChannelBanksTest.kt)

[Media provenance and verification scope](Bildnachweise-en)
