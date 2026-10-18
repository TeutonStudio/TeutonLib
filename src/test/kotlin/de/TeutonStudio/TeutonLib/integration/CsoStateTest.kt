package de.TeutonStudio.TeutonLib.integration

import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CsoStateTest {
    @Test
    fun unavailableIntegrationNeverRunsCleanupOrEnables() {
        val state = CsoState()
        assertFalse(state.apply(true, false, java.lang.Runnable { fail("Optional module was loaded") }))
        assertFalse(state.enabled())
    }

    @Test
    fun disableCleansUpWhileOldEdgesAreStillUsable() {
        val state = CsoState()
        assertTrue(state.apply(true, true, java.lang.Runnable { fail("Enabling must not detach") }))
        assertTrue(state.apply(false, true, java.lang.Runnable { assertTrue(state.enabled()) }))
        assertFalse(state.enabled())
    }

    @Test
    fun duplicateReloadsDoNotRepeatNetworkRebuilds() {
        val state = CsoState()
        val cleanups: AtomicInteger = AtomicInteger()
        for (i in 0..9) {
            assertTrue(state.apply(true, true, java.lang.Runnable { cleanups.incrementAndGet() }))
            assertFalse(state.apply(true, true, java.lang.Runnable { cleanups.incrementAndGet() }))
            assertTrue(state.apply(false, true, java.lang.Runnable { cleanups.incrementAndGet() }))
            assertFalse(state.apply(false, true, java.lang.Runnable { cleanups.incrementAndGet() }))
        }
        assertEquals(10, cleanups.get())
    }

    @Test
    fun failedCleanupDoesNotPublishAnInactiveState() {
        val state = CsoState()
        state.apply(true, true, java.lang.Runnable {})
        assertThrows(java.lang.IllegalStateException::class.java, {
            state.apply(
                false, true,
                java.lang.Runnable { throw java.lang.IllegalStateException("rebuild failed") })
        })
        assertTrue(state.enabled())
    }

    @Test
    fun clientAndIntegratedServerStatesAreIndependent() {
        val server = CsoState()
        val client = CsoState()
        server.apply(true, true, java.lang.Runnable {})
        assertFalse(client.enabled())
        client.apply(true, true, java.lang.Runnable {})
        client.apply(false, true, java.lang.Runnable {})
        assertTrue(server.enabled())
    }
}
