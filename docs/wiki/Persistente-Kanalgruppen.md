# Persistente Kanalgruppen

TeutonLib v1.6.0 bietet mit `de.TeutonStudio.TeutonLib.api.computer.channel.PersistentChannelGroupBank` eine Entwickler-API für addondefinierte Gruppen aus Aliasen und stabilen Ziel-IDs. Das Addon stellt Bedienoberfläche, Zielauflösung und Speicherung bereit. Diese Klasse erzeugt keine zusätzlichen Standard-Lua-Befehle.

## Gruppen mit stabilen Zielbindungen

`PersistentChannelGroupBank` speichert Gruppen mit UUID, Namen und Bindungen aus Alias und stabiler Ziel-ID. Fehlende Ziele bleiben absichtlich gespeichert, damit eine vorübergehend getrennte Geräteverbindung ihre Zuordnung behalten kann.

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

Das Addon entscheidet über gültige Namen und Ziel-IDs und speichert `serialized` in seinen eigenen Blockdaten. Standardgrenzen sind 32 Gruppen und 64 Bindungen pro Gruppe, beide konfigurierbar. `bind` ersetzt bei bestehendem Alias dessen Ziel. `renameBinding`, `unbind`, `renameGroup` und `removeGroup` ändern den Bestand; `group` kann per UUID oder Name lesen. Doppelte Gruppennamen und Alias-Kollisionen werden abgewiesen.

`encodedDefinitions()` verwendet zeilenweise `G|uuid|name` und `B|uuid|alias|targetId`. `loadEncodedDefinitions(...)` prüft IDs, Namen und Limits und überspringt ungültige Einträge. Die vom Addon gewählten Namens-/Zielformate müssen zum Trennzeichenformat passen.

## Implementierung

[Gruppenbank und Definitionstypen](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/computer/channel/PersistentChannelGroupBank.kt), [Vertragstests](https://github.com/TeutonStudio/TeutonLib/blob/v1.6.0/src/test/kotlin/de/TeutonStudio/TeutonLib/api/computer/channel/PersistentChannelBanksTest.kt).

Siehe auch [Signalbank](Signalbank), [Computer-Kanäle](Computer-Kanaele) und [gemeinsame API-Übersicht](Persistente-Kanalgruppen-und-Signale).

[Zur Startseite](Home)
