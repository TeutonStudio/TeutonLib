package de.TeutonStudio.TeutonLib.integration.cso.docking

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DockingLengthTest {
    @Test fun `all redstone strengths including both boundaries`() {
        for (signal in 0..15) assertEquals(
            when { signal <= 5 -> DockingLength.OFF; signal <= 10 -> DockingLength.HALF; else -> DockingLength.FULL },
            DockingLength.fromSignal(signal), "signal=$signal")
    }

    @Test fun `all nine combinations are symmetric and stable`() {
        val expected = arrayOf(
            arrayOf(DockingLength.OFF, DockingLength.OFF, DockingLength.OFF),
            arrayOf(DockingLength.OFF, DockingLength.HALF, DockingLength.HALF),
            arrayOf(DockingLength.OFF, DockingLength.HALF, DockingLength.FULL))
        for (a in DockingLength.entries) for (b in DockingLength.entries) {
            val result = a.negotiate(b)
            assertEquals(expected[a.ordinal][b.ordinal], result)
            assertEquals(b.negotiate(a), result)
            repeat(10) { assertEquals(result, a.negotiate(b)) }
            for (distance in 0..5) assertEquals(
                result != DockingLength.OFF && distance == if (result == DockingLength.HALF) 2 else 3,
                result.acceptsGridDistance(distance), "$a/$b at $distance")
        }
    }

    @Test fun `contact planes meet at required base separation`() {
        assertEquals(1.0, DockingLength.HALF.contactOffset)
        assertEquals(1.5, DockingLength.FULL.contactOffset)
        for (length in listOf(DockingLength.HALF, DockingLength.FULL)) {
            assertEquals(length.contactOffset, length.baseDistance - length.contactOffset)
        }
    }

    @Test fun `saved lengths round trip and invalid tags fail closed`() {
        for (length in DockingLength.entries) assertEquals(length, DockingLength.fromSaved(length.ordinal))
        for (invalid in listOf(-1, 3, 100, Int.MAX_VALUE)) assertEquals(DockingLength.OFF, DockingLength.fromSaved(invalid))
    }
}
