# Netzwerk-Sitzungen und Budgets

TeutonLib 1.6.0 stellt drei allgemeine Hilfsklassen unter `de.TeutonStudio.TeutonLib.api.network` bereit. Sie haben keine eigene Paketregistrierung, Speicherung oder GUI; ein Addon bindet sie in seine vorhandenen Server-Pakethandler ein.

## PlayerSessionState

`PlayerSessionState<K, V>(playerForKey, ttlTicks)` ordnet einem frei definierten Schlüssel einen Spieler und einen Wert mit letztem Berührungszeitpunkt zu. Die TTL muss nicht negativ sein.

`put`, `get`, `touch`, `remove`, `removePlayer`, `removeIf`, `expire`, `clear` und `size` verwalten den Bestand. **Ablauf erfolgt erst durch den Aufruf von `expire(tick)`**, sobald `tick - lastTouchedTick > ttlTicks` ist. `get` erneuert die TTL nicht; dafür `touch` verwenden. Logout und Serverende müssen vom aufrufenden Addon bereinigt werden.

## SingleGestureSessionState

`SingleGestureSessionState<K, V>` erlaubt pro Slot-Schlüssel eine aktive Geste mit Gesten-ID und Sequenz. `start(key, gestureId, value, tick)` setzt Sequenz 0; dieselbe bereits aktive Gesten-ID wird abgewiesen.

`advance(key, gestureId, sequence, tick, update)` akzeptiert ausschließlich die nächste Sequenz. Die Ergebnisse unterscheiden `ACCEPTED`, `MISSING_START`, `STALE_GESTURE` und `OUT_OF_SEQUENCE`; beim letzten Fall wird `expectedSequence` geliefert. Ein erfolgreicher Schritt erneuert den Berührungszeitpunkt und liefert den vorherigen Wert. Der Aufrufer muss `expire`, Abbruch und Logout selbst ausführen.

## PlayerTickBudget

`PlayerTickBudget(maxPacketsPerTick, maxUnitsPerTick)` begrenzt Paketanzahl und Arbeitseinheiten je Spieler und Servertick; beide Grenzen müssen positiv sein. `tryConsume(playerId, tick, units)` liefert `false` bei negativen, zu großen oder insgesamt überschrittenen Einheiten sowie zu vielen Paketen. Auch `units = 0` verbraucht einen Paketplatz. Ein neuer Tick startet einen frischen Zähler. `clearPlayer` und `clear` bereinigen die Identitäten.

```kotlin
val budget = PlayerTickBudget(16, 256)
if (!budget.tryConsume(player.uuid, serverTick, requestedEntries)) return
// Anschließend Menü, Besitz und konkrete Aktion serverseitig prüfen.
```

Diese Helfer ersetzen keine Prüfung von Paketinhalt, Menüidentität oder Berechtigungen und dürfen nicht als clientseitige Autorisierung verwendet werden.

Quelle: [Netzwerk-API](https://github.com/TeutonStudio/TeutonLib/tree/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/network). [Alle Funktionen](Home).
