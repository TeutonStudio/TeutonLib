package de.TeutonStudio.TeutonLib.api.network

import java.util.UUID

/** Sequenced gesture state with one active gesture per caller-defined slot key. */
class SingleGestureSessionState<K, V>(playerForKey: (K) -> UUID, ttlTicks: Long) {
    enum class AdvanceStatus { ACCEPTED, MISSING_START, STALE_GESTURE, OUT_OF_SEQUENCE }
    data class AdvanceResult<V>(val status: AdvanceStatus, val previous: V? = null, val expectedSequence: Int? = null)
    private data class Entry<V>(val gestureId: Long, val lastSequence: Int, val value: V)
    private val sessions = PlayerSessionState<K, Entry<V>>(playerForKey, ttlTicks)

    @Synchronized
    fun start(key: K, gestureId: Long, value: V, tick: Long): Boolean {
        val existing = sessions.get(key)
        if (existing?.gestureId == gestureId) return false
        sessions.put(key, Entry(gestureId, 0, value), tick)
        return true
    }

    @Synchronized
    fun advance(key: K, gestureId: Long, sequence: Int, tick: Long, update: (V) -> V): AdvanceResult<V> {
        val existing = sessions.get(key) ?: return AdvanceResult(AdvanceStatus.MISSING_START)
        if (existing.gestureId != gestureId) return AdvanceResult(AdvanceStatus.STALE_GESTURE)
        val expected = existing.lastSequence + 1
        if (sequence != expected) return AdvanceResult(AdvanceStatus.OUT_OF_SEQUENCE, expectedSequence = expected)
        sessions.put(key, existing.copy(lastSequence = sequence, value = update(existing.value)), tick)
        return AdvanceResult(AdvanceStatus.ACCEPTED, previous = existing.value)
    }

    fun remove(key: K): V? = sessions.remove(key)?.value
    fun removePlayer(playerId: UUID) = sessions.removePlayer(playerId)
    fun expire(tick: Long) = sessions.expire(tick)
    fun removeIf(predicate: (K) -> Boolean) = sessions.removeIf(predicate)
    fun clear() = sessions.clear()
    fun size(): Int = sessions.size()
}
