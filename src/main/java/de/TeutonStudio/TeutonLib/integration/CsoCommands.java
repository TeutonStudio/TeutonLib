package de.TeutonStudio.TeutonLib.integration;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import de.TeutonStudio.TeutonLib.ServerConfig;

public final class CsoCommands {
    private CsoCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CsoCommandTree.register(dispatcher, source -> source.hasPermission(2),
                CsoCommands::status, CsoCommands::set);
    }

    private static int set(CommandSourceStack source, boolean enabled) {
        CsoRuntime.setRequested(enabled);
        source.sendSuccess(() -> Component.literal("CSO-Konfiguration gespeichert."), true);
        return status(source);
    }

    private static int status(CommandSourceStack source) {
        var problems = CsoAvailability.problems();
        String status = "CSO: Config=" + ServerConfig.CSO_ENABLED.getAsBoolean()
                + ", aktiv=" + CsoRuntime.enabled()
                + (problems.isEmpty() ? "" : "; " + String.join("; ", problems));
        source.sendSuccess(() -> Component.literal(status), false);
        return 1;
    }
}
