package de.TeutonStudio.TeutonLib.integration

import de.TeutonStudio.TeutonLib.integration.train.client.TrainPortalLightMath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrainPortalLightingTest {
    private fun pack(block: Int, sky: Int) = block shl 4 or (sky shl 20)

    @Test fun `nether environment retains lamps and removes sky without fullbright`() {
        assertEquals(pack(11, 0), TrainPortalLightMath.combine(pack(4, 15), pack(11, 0)))
        assertEquals(pack(0, 0), TrainPortalLightMath.combine(pack(0, 15), pack(0, 0)))
    }

    @Test fun `daylight from destination respects local skylight occlusion`() {
        assertEquals(pack(0, 15), TrainPortalLightMath.combine(pack(0, 15), pack(0, 15)))
        assertEquals(pack(0, 0), TrainPortalLightMath.combine(pack(0, 0), pack(0, 15)))
        assertEquals(pack(0, 7), TrainPortalLightMath.combine(pack(0, 7), pack(0, 15)))
    }

    @Test fun `night and ambient shadows attenuate rather than enable daylight`() {
        assertEquals(pack(9, 0), TrainPortalLightMath.combine(pack(9, 15), pack(0, 0)))
        assertEquals(pack(1, 3), TrainPortalLightMath.combine(pack(1, 15), pack(0, 3)))
    }

    @Test fun `local lamps and fractional baked light remain independent of environment sky`() {
        val local = 109 or (153 shl 16)
        assertEquals(109 or (153 shl 16), TrainPortalLightMath.combine(local, pack(0, 15)))
        assertEquals(192 or (77 shl 16), TrainPortalLightMath.combine(local, 192 or (120 shl 16)))
    }
}
