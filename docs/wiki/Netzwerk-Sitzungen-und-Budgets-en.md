# Network sessions and budgets

[Deutsch](Netzwerk-Sitzungen-und-Budgets) · **English** · [Overview](English)

Three helpers in `api.network` provide server-side state primitives. They add no packet registration, storage or GUI. Addons must still validate packet content, menu identity and permissions.

**PlayerSessionState<K,V>(playerForKey,ttlTicks)** associates keys with players, values and last-touch time. TTL must be non-negative. Operations: `put/get/touch/remove/removePlayer/removeIf/expire/clear/size`. Expiry occurs only when the caller invokes `expire(tick)` and `tick-lastTouchedTick > ttlTicks`. Reading does not renew TTL. Call cleanup on logout and server shutdown.

**SingleGestureSessionState<K,V>** allows one gesture ID and sequence per slot key. `start(key,id,value,tick)` starts at sequence 0 and rejects the already active ID. `advance` accepts only the next sequence and returns `ACCEPTED`, `MISSING_START`, `STALE_GESTURE` or `OUT_OF_SEQUENCE` (with expected sequence). Accepted steps renew touch time and return the previous value. The addon owns expiry, cancellation and logout cleanup.

**PlayerTickBudget(maxPacketsPerTick,maxUnitsPerTick)** requires positive limits. `tryConsume(playerId,tick,units)` rejects negative/excess units, exhausted units or too many packets. Even zero units consumes a packet slot. A new tick resets counts; `clearPlayer` / `clear` release identities.

```kotlin
val budget = PlayerTickBudget(16,256)
if (!budget.tryConsume(player.uuid,serverTick,requestedEntries)) return
// Then validate the actual action and permissions.
```

The screenshot shows actual contract-test results for expiry, gesture order and budgets; these helpers have no standalone in-game panel.

## Illustration


Executed desktop contract tests for session expiry, gesture order and per-player tick budgets; no standalone game UI.

![Network sessions and budgets: 1](https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/assets/screenshots/test-SessionPrimitivesTest.png)


## Sources

- [network](https://github.com/TeutonStudio/TeutonLib/tree/v1.6.0/src/main/kotlin/de/TeutonStudio/TeutonLib/api/network)

[Media provenance and verification scope](Bildnachweise-en)
