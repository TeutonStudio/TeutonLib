# Persistent groups and signals

[Deutsch](Persistente-Kanalgruppen-und-Signale) · **English** · [Overview](English)

These are two independent addon APIs, not new standard Lua commands. The addon supplies its own UI, persistence, target resolver and physical output backend.

- [PersistentChannelGroupBank](Persistente-Kanalgruppen-en) serializes named groups and stable target bindings. Missing targets intentionally remain bound for later reconnection.
- [SignalBank](Signalbank-en) serializes output definitions, while output values and active timed pulses remain transient. Authority loss clears outputs.

This differs from [native computer channels](Computer-Kanaele-en), which persist output values. The illustration shows an actual consumer UI; it is not automatically created by the helper classes.

## Illustration


Actual addon group editor and executed bank tests. Group and signal banks are separate APIs.

![Persistent groups and signals: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/addon-wiki-channel-groups.png)

![Persistent groups and signals: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-PersistentChannelBanksTest.png)


## Sources

- [PersistentChannelGroupBank.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/channel/PersistentChannelGroupBank.kt)
- [SignalBank.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/channel/SignalBank.kt)

[Media provenance and verification scope](Bildnachweise-en)
