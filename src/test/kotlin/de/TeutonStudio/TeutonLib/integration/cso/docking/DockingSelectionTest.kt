package de.TeutonStudio.TeutonLib.integration.cso.docking

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DockingSelectionTest {
    @Test fun `missing candidates leave connector waiting`() {
        assertNull(DockingSelection.choose(emptySequence<DockingSelection.Candidate<String>>()))
    }
    @Test fun `closest prospective contact wins regardless of iteration order`() {
        val candidates = listOf(DockingSelection.Candidate("far", 4.0, 1), DockingSelection.Candidate("near", 0.2, 2), DockingSelection.Candidate("middle", 1.0, 3))
        for (order in listOf(candidates, candidates.reversed(), candidates.drop(1) + candidates.take(1))) {
            assertEquals("near", DockingSelection.choose(order.asSequence()))
        }
    }
    @Test fun `equal distance uses stable position order and does not oscillate`() {
        val a = DockingSelection.Candidate("A", 0.0, 1)
        val b = DockingSelection.Candidate("B", 0.0, 2)
        repeat(10) {
            assertEquals("A", DockingSelection.choose(sequenceOf(a, b)))
            assertEquals("A", DockingSelection.choose(sequenceOf(b, a)))
        }
    }
    @Test fun `invalid geometry fails closed`() {
        assertNull(DockingSelection.choose(sequenceOf(
            DockingSelection.Candidate("invalid", Double.NaN, 1),
            DockingSelection.Candidate("invalid", Double.POSITIVE_INFINITY, 2),
            DockingSelection.Candidate("invalid", -1.0, 3))))
    }
}
