package de.TeutonStudio.TeutonLib.integration;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.BiFunction;

/** Shared command tree; permission predicates also control parsing and suggestions. */
final class CsoCommandTree {
    private CsoCommandTree() {}

    static <S> void register(CommandDispatcher<S> dispatcher, Predicate<S> administrator,
                             ToIntFunction<S> status, BiFunction<S, Boolean, Integer> change) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal("teutonlib")
                .then(LiteralArgumentBuilder.<S>literal("cso")
                        .then(LiteralArgumentBuilder.<S>literal("status").executes(c -> status.applyAsInt(c.getSource())))
                        .then(LiteralArgumentBuilder.<S>literal("enable").requires(administrator)
                                .executes(c -> change.apply(c.getSource(), true)))
                        .then(LiteralArgumentBuilder.<S>literal("disable").requires(administrator)
                                .executes(c -> change.apply(c.getSource(), false)))));
    }
}
