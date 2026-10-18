package de.TeutonStudio.TeutonLib.integration;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CsoStateTest {
    @Test void unavailableIntegrationNeverRunsCleanupOrEnables() {
        var state = new CsoState();
        assertFalse(state.apply(true, false, () -> fail("Optional module was loaded")));
        assertFalse(state.enabled());
    }
    @Test void disableCleansUpWhileOldEdgesAreStillUsable() {
        var state = new CsoState();
        assertTrue(state.apply(true, true, () -> fail("Enabling must not detach")));
        assertTrue(state.apply(false, true, () -> assertTrue(state.enabled())));
        assertFalse(state.enabled());
    }
    @Test void duplicateReloadsDoNotRepeatNetworkRebuilds() {
        var state = new CsoState();
        var cleanups = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            assertTrue(state.apply(true, true, cleanups::incrementAndGet));
            assertFalse(state.apply(true, true, cleanups::incrementAndGet));
            assertTrue(state.apply(false, true, cleanups::incrementAndGet));
            assertFalse(state.apply(false, true, cleanups::incrementAndGet));
        }
        assertEquals(10, cleanups.get());
    }
    @Test void failedCleanupDoesNotPublishAnInactiveState() {
        var state = new CsoState();
        state.apply(true, true, () -> {});
        assertThrows(IllegalStateException.class, () -> state.apply(false, true,
                () -> { throw new IllegalStateException("rebuild failed"); }));
        assertTrue(state.enabled());
    }
    @Test void clientAndIntegratedServerStatesAreIndependent() {
        var server = new CsoState(); var client = new CsoState();
        server.apply(true, true, () -> {});
        assertFalse(client.enabled());
        client.apply(true, true, () -> {});
        client.apply(false, true, () -> {});
        assertTrue(server.enabled());
    }
}
