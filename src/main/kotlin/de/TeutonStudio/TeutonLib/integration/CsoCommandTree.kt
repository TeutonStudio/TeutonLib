package de.TeutonStudio.TeutonLib.integration

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import java.util.function.BiFunction
import java.util.function.Predicate
import java.util.function.ToIntFunction

/** Shared command tree; permission predicates also control parsing and suggestions.  */
object CsoCommandTree {
    @JvmStatic
    fun <S> register(
        dispatcher: CommandDispatcher<S>, administrator: Predicate<S>,
        status: ToIntFunction<S>, change: BiFunction<S, Boolean, Int>
    ) {
        dispatcher.register(
            LiteralArgumentBuilder.literal<S>("teutonlib")
                .then(
                    LiteralArgumentBuilder.literal<S>("cso")
                        .then(
                            LiteralArgumentBuilder.literal<S>("status")
                                .executes(Command { c: CommandContext<S> -> status.applyAsInt(c!!.source) })
                        )
                        .then(
                            LiteralArgumentBuilder.literal<S>("enable").requires(administrator)
                                .executes(Command { c: CommandContext<S> -> change.apply(c!!.source, true)!! })
                        )
                        .then(
                            LiteralArgumentBuilder.literal<S>("disable").requires(administrator)
                                .executes(Command { c: CommandContext<S> -> change.apply(c!!.source, false)!! })
                        )
                )
        )
    }
}
