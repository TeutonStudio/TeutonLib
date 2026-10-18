package de.TeutonStudio.TeutonLib.integration

import com.mojang.brigadier.CommandDispatcher
import de.TeutonStudio.TeutonLib.ServerConfig
import java.util.function.BiFunction
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.function.ToIntFunction
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component

object CsoCommands {
    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        CsoCommandTree.register(dispatcher,
            Predicate { it!!.hasPermission(2) }, ToIntFunction { status(it!!) },
            BiFunction { source, enabled -> set(source!!, enabled) })
    }

    @JvmStatic
    private fun set(source: CommandSourceStack, enabled: Boolean): Int {
        CsoRuntime.setRequested(enabled)
        source.sendSuccess(Supplier { Component.literal("CSO-Konfiguration gespeichert.") }, true)
        return status(source)
    }

    @JvmStatic
    private fun status(source: CommandSourceStack): Int {
        val problems = CsoAvailability.problems()
        val status = ("CSO: Config=" + ServerConfig.CSO_ENABLED.asBoolean
                + ", aktiv=" + CsoRuntime.enabled()
                + (if (problems.isEmpty()) "" else "; " + problems.joinToString("; ")))
        source.sendSuccess(Supplier { Component.literal(status) }, false)
        return 1
    }
}
