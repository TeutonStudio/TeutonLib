package de.TeutonStudio.TeutonLib.api.network

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Domain-neutral, TTL-bounded state owned by one player per caller-defined key. */
class PlayerSessionState<K, V>(
    private val playerForKey: (K) -> UUID,
    private val ttlTicks: Long
) {
    private data class Entry<V>(val value: V, val lastTouchedTick: Long)
    private val entries = ConcurrentHashMap<K, Entry<V>>()

    init {
        require(ttlTicks >= 0) { "ttlTicks must not be negative" }
    }

    fun get(key: K): V? = entries[key]?.value

    fun touch(key: K, tick: Long): V? = entries.computeIfPresent(key) { _, entry ->
        entry.copy(lastTouchedTick = tick)
    }?.value

    fun put(key: K, value: V, tick: Long): V? = entries.put(key, Entry(value, tick))?.value
    fun remove(key: K): V? = entries.remove(key)?.value
    fun removePlayer(playerId: UUID) = entries.keys.removeIf { playerForKey(it) == playerId }.let { Unit }
    fun expire(tick: Long) = entries.entries.removeIf { (_, entry) -> tick - entry.lastTouchedTick > ttlTicks }.let { Unit }
    fun removeIf(predicate: (K) -> Boolean) = entries.keys.removeIf(predicate).let { Unit }
    fun clear() = entries.clear()
    fun size(): Int = entries.size
}
