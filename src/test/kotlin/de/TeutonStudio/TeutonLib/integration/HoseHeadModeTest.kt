package de.TeutonStudio.TeutonLib.integration

import de.TeutonStudio.TeutonLib.HoseHeadMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HoseHeadModeTest {
    @Test
    fun `configured modes distinguish ordinary and Sable pulleys`() {
        val cases = listOf(
            Triple(HoseHeadMode.ALWAYS, false, true),
            Triple(HoseHeadMode.ALWAYS, true, true),
            Triple(HoseHeadMode.SABLE_ONLY, false, false),
            Triple(HoseHeadMode.SABLE_ONLY, true, true),
            Triple(HoseHeadMode.NEVER, false, false),
            Triple(HoseHeadMode.NEVER, true, false)
        )
        for ((mode, onSable, expected) in cases) {
            assertEquals(expected, mode.permits(onSable), "$mode onSable=$onSable")
        }
    }
}
