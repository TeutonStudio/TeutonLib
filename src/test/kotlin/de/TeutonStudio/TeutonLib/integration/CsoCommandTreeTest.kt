package de.TeutonStudio.TeutonLib.integration

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import java.util.function.ToIntFunction
import java.util.function.BiFunction
import java.util.ArrayList
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CsoCommandTreeTest {
    @Test
    @Throws(java.lang.Exception::class)
    fun onlyAdministratorsCanChangeState() {
        val dispatcher: CommandDispatcher<kotlin.Int> = CommandDispatcher<kotlin.Int>()
        val changes = java.util.ArrayList<Boolean>()
        CsoCommandTree.register<kotlin.Int>(
            dispatcher,
            java.util.function.Predicate { level: kotlin.Int? -> level!! >= 2 },
            ToIntFunction { level: kotlin.Int? -> 1 },
            BiFunction { level: kotlin.Int, enabled: Boolean ->
                changes.add(enabled)
                1
            })
        for (level in kotlin.collections.mutableListOf<kotlin.Int>(0, 1)) {
            assertEquals(1, dispatcher.execute("teutonlib cso status", level))
            assertThrows(CommandSyntaxException::class.java, { dispatcher.execute("teutonlib cso enable", level) })
            assertThrows(CommandSyntaxException::class.java, { dispatcher.execute("teutonlib cso disable", level) })
        }
        assertTrue(changes.isEmpty())
        assertEquals(1, dispatcher.execute("teutonlib cso enable", 2))
        assertEquals(1, dispatcher.execute("teutonlib cso disable", 4))
        assertEquals(kotlin.collections.mutableListOf<Boolean>(true, false), changes)
    }

    @Test
    fun invalidSubcommandsNeverChangeState() {
        val dispatcher: CommandDispatcher<kotlin.Int> = CommandDispatcher<kotlin.Int>()
        CsoCommandTree.register<kotlin.Int>(
            dispatcher,
            java.util.function.Predicate { level: kotlin.Int? -> level!! >= 2 },
            ToIntFunction { level: kotlin.Int? -> 1 },
            BiFunction { level: kotlin.Int, enabled: Boolean ->
                fail<Unit>("Invalid command changed state")
                0
            })
        assertThrows(CommandSyntaxException::class.java, { dispatcher.execute("teutonlib cso toggle", 4) })
    }
}
