package de.TeutonStudio.TeutonLib.api.network

import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionPrimitivesTest {
    private data class Key(val player: UUID, val slot: String)

    @Test
    fun `player state expires and cleans up per owner`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val state = PlayerSessionState<Key, String>(Key::player, 40)
        val stale = Key(first, "stale")
        val active = Key(first, "active")
        val other = Key(second, "other")
        state.put(stale, "old", 10)
        state.put(active, "fresh", 10)
        state.put(other, "other", 10)
        state.touch(active, 50)
        state.touch(other, 50)
        state.expire(51)
        assertNull(state.get(stale))
        assertEquals("fresh", state.get(active))
        state.removePlayer(first)
        assertNull(state.get(active))
        assertEquals("other", state.get(other))
    }

    @Test
    fun `tick budget is isolated and resets`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val budget = PlayerTickBudget(2, 4)
        assertTrue(budget.tryConsume(first, 1, 2))
        assertTrue(budget.tryConsume(first, 1, 2))
        assertFalse(budget.tryConsume(first, 1, 1))
        assertTrue(budget.tryConsume(second, 1, 4))
        assertFalse(budget.tryConsume(second, 1, 1))
        assertTrue(budget.tryConsume(first, 2, 4))
        assertFalse(budget.tryConsume(first, 3, -1))
    }

    @Test
    fun `gesture state rejects stale and skipped continuations`() {
        val player = UUID.randomUUID()
        val key = Key(player, "display")
        val sessions = SingleGestureSessionState<Key, String>(Key::player, 40)
        assertEquals(
            SingleGestureSessionState.AdvanceStatus.MISSING_START,
            sessions.advance(key, 10, 1, 1) { it }.status
        )
        assertTrue(sessions.start(key, 10, "start", 2))
        assertFalse(sessions.start(key, 10, "duplicate", 2))
        assertEquals(
            SingleGestureSessionState.AdvanceStatus.OUT_OF_SEQUENCE,
            sessions.advance(key, 10, 2, 3) { it }.status
        )
        assertTrue(sessions.start(key, 11, "replacement", 4))
        assertEquals(
            SingleGestureSessionState.AdvanceStatus.STALE_GESTURE,
            sessions.advance(key, 10, 1, 5) { it }.status
        )
        val accepted = sessions.advance(key, 11, 1, 5) { "$it-next" }
        assertEquals(SingleGestureSessionState.AdvanceStatus.ACCEPTED, accepted.status)
        assertEquals("replacement", accepted.previous)
    }
}
