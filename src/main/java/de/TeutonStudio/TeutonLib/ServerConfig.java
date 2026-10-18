package de.TeutonStudio.TeutonLib;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
    public static final String FILE_NAME = "teutonlib-server.toml";
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CSO_ENABLED;
    public static final ModConfigSpec.BooleanValue MOTOR_EXTENSION_ENERGY_TRANSFER;
    public static final ModConfigSpec.IntValue PERIPHERAL_LINK_RANGE;
    public static final ModConfigSpec.IntValue PERIPHERAL_LINK_LIMIT;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("modules").push("createSableOptimization");
        CSO_ENABLED = builder.comment("Enable cogwheel bridges across locked Sable/Simulated docks.",
                "Takes effect during play. The server controls all clients.",
                "Requires Create 6.0.10, Sable 2.0.5 and Simulated 1.3.2.")
                .define("enabled", true);
        builder.pop(2);
        builder.push("compat").push("createNewAge");
        MOTOR_EXTENSION_ENERGY_TRANSFER = builder
                .comment("Allow basic and advanced Create New Age motor extensions to accept Forge Energy.",
                        "Energy goes directly to the attached, correctly aligned motor; no extra buffer is added.",
                        "Supports Flux Points and other FE sources on every accessible side.",
                        "Takes effect when the server config is loaded or reloaded.")
                .define("motorExtensionEnergyTransfer", true);
        builder.pop(2);
        builder.push("compat").push("peripheralLink");
        PERIPHERAL_LINK_RANGE = builder.comment("Wireless range in world blocks, including moving Sable sublevels.")
                .defineInRange("range", 256, 1, 30000000);
        PERIPHERAL_LINK_LIMIT = builder.comment("Maximum active links per computer or control desk network.")
                .defineInRange("maxLinksPerNetwork", 64, 1, 4096);
        builder.pop(2);
        SPEC = builder.build();
    }

    private ServerConfig() {}

    /** NeoForge 21.1.234 only loads a world override if the file already exists. */
    public static void prepareWorld(MinecraftServer server) {
        Path target = server.getWorldPath(new LevelResource("serverconfig")).resolve(FILE_NAME);
        if (Files.exists(target)) return;
        try {
            Files.createDirectories(target.getParent());
            Path global = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
            if (Files.isRegularFile(global)) Files.copy(global, target);
            else {
                var defaults = CommentedConfig.inMemory();
                SPEC.correct(defaults);
                new TomlWriter().write(defaults, target, WritingMode.REPLACE);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create world config " + target, e);
        }
    }
}
