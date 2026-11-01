package de.TeutonStudio.TeutonLib.integration

import de.TeutonStudio.TeutonLib.integration.hose.HoseFlowBudget
import de.TeutonStudio.TeutonLib.integration.hose.HoseTension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HoseConnectorMathTest {
    @Test fun `slack and compression cannot push ships apart`() {
        assertEquals(0.0, HoseTension.force(4.0, 5.0, 20.0, 10000.0, 2000.0))
        assertEquals(0.0, HoseTension.force(6.0, 5.0, -20.0, 10000.0, 2000.0))
        assertEquals(14000.0, HoseTension.force(6.0, 5.0, 2.0, 10000.0, 2000.0))
    }
    @Test fun `implicit spring stays stable across lightweight bodies and physics step sizes`() {
        for (dt in listOf(.005, .01, .025, .05)) {
            var distance = 9.0
            var velocity = 0.0
            repeat((1 / dt).toInt()) {
                val force = HoseTension.force(distance, 8.0, velocity, 10000.0, 2000.0)
                velocity -= 2 * HoseTension.impulse(force, dt, 2.0, 10000.0, 2000.0)
                distance += velocity * dt
                assertTrue(distance.isFinite() && kotlin.math.abs(velocity) < 20.0)
            }
            assertTrue(distance < 8.95, "spring must pull at dt=$dt")
        }
    }
    @Test fun `overload accumulates physics time and resets on recovery`() {
        var time = 0.0
        repeat(24) { time = HoseTension.overload(time, 51000.0, 50000.0, .01) }
        assertTrue(time < .25)
        time = HoseTension.overload(time, 51000.0, 50000.0, .01)
        assertEquals(.25, time, 1e-9)
        assertEquals(0.0, HoseTension.overload(time, 50000.0, 50000.0, .01))
    }
    @Test fun `all outputs share a half rate allowance and repeated probes do not replenish it`() {
        val budget = HoseFlowBudget()
        assertEquals(16, budget.available(1, 32))
        budget.consume(9)
        assertEquals(7, budget.available(1, 32))
        budget.consume(7)
        assertEquals(0, budget.available(1, 32))
        assertEquals(16, budget.available(2, 32))
        assertEquals(16, budget.available(1000, 32))
    }
    @Test fun `odd rates retain fractions without accumulating unused whole buckets`() {
        val budget = HoseFlowBudget()
        assertEquals(listOf(0, 1, 0, 1, 0, 1), (0L..5L).map { budget.available(it, 1) })
        assertEquals(16, budget.available(1000, 32))
    }
}
