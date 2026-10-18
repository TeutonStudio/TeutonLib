package de.TeutonStudio.TeutonLib.api.computer

import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WorkspaceSessionTest {
    @Test
    fun isolatesMenuControllerBackendAndPage() {
        val id: UUID = UUID.randomUUID()
        val current = WorkspaceSession(7, id, "addon:desk", 2)
        assertTrue(current.accepts(WorkspaceSession(7, id, "addon:desk", 2)))
        assertFalse(current.accepts(WorkspaceSession(8, id, "addon:desk", 2)))
        assertFalse(current.accepts(WorkspaceSession(7, UUID.randomUUID(), "addon:desk", 2)))
        assertFalse(current.accepts(WorkspaceSession(7, id, "other:desk", 2)))
        assertFalse(current.accepts(WorkspaceSession(7, id, "addon:desk", 1)))
    }

    @Test
    fun terminalRejectsRepliesAndReturningToTheSamePageNeedsANewSession() {
        val terminal = WorkspaceSession(7, UUID.randomUUID(), "addon:desk", 0)
        assertFalse(terminal.accepts(terminal))
        val before = WorkspaceSession(7, UUID.randomUUID(), "addon:desk", 2)
        val after = WorkspaceSession(7, UUID.randomUUID(), "addon:desk", 2)
        assertFalse(after.accepts(before))
    }
}
