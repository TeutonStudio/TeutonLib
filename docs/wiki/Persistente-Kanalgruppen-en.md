# Persistent channel groups

[Deutsch](Persistente-Kanalgruppen) · **English** · [Overview](English)

`api.computer.channel.PersistentChannelGroupBank` stores groups with stable UUIDs, names and alias→target-ID bindings. The addon supplies validation, target resolution, UI and persistence. Missing targets intentionally remain in the definitions.

```kotlin
val groups = PersistentChannelGroupBank(
    validName = { Regex("[a-z][a-z0-9_-]{0,31}").matches(it) },
    validTarget = { it.isNotBlank() },
    changed = { host.setChanged() }
)
val group = groups.addGroup("flight")
groups.bind(group.id, "altitude", "my_mod:sensor_42")
val serialized = groups.encodedDefinitions()
```

Defaults: 32 groups, 64 bindings per group; both configurable. Binding an existing alias replaces its target. `renameBinding`, `unbind`, `renameGroup`, `removeGroup` mutate definitions; lookup accepts UUID or name. Duplicate group names and colliding aliases are rejected.

Serialization uses lines `G|uuid|name` and `B|uuid|alias|targetId`. `loadEncodedDefinitions` validates IDs, names and limits, skipping invalid entries. Addon naming formats must respect that delimiter format. Store the serialized definitions with the host. No standard Lua command or physical output is generated. [Signal bank](Signalbank-en).

## Illustration


Aeroworks as an actual group-bank consumer; tests cover retained missing targets and definition persistence.

![Persistent channel groups: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/addon-wiki-channel-groups.png)

![Persistent channel groups: 2](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-PersistentChannelBanksTest.png)


## Sources

- [PersistentChannelGroupBank.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/channel/PersistentChannelGroupBank.kt)
- [PersistentChannelBanksTest.kt](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/test/kotlin/de/TeutonStudio/TeutonLib/api/computer/channel/PersistentChannelBanksTest.kt)

[Media provenance and verification scope](Bildnachweise-en)
