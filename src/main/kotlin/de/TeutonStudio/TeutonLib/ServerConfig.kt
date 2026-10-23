package de.TeutonStudio.TeutonLib

import com.electronwill.nightconfig.core.CommentedConfig
import com.electronwill.nightconfig.core.io.WritingMode
import com.electronwill.nightconfig.toml.TomlWriter
import java.io.IOException
import java.lang.IllegalStateException
import java.nio.file.Files
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.ModConfigSpec

object ServerConfig {
    const val FILE_NAME = "teutonlib-server.toml"
    @JvmField val SPEC: ModConfigSpec
    @JvmField val CSO_ENABLED: ModConfigSpec.BooleanValue
    @JvmField val ENABLE_ANALOG_DOCKING: ModConfigSpec.BooleanValue
    @JvmField val MOTOR_EXTENSION_ENERGY_TRANSFER: ModConfigSpec.BooleanValue
    @JvmField val PERIPHERAL_LINK_RANGE: ModConfigSpec.IntValue
    @JvmField val PERIPHERAL_LINK_LIMIT: ModConfigSpec.IntValue
    init {
        val builder = ModConfigSpec.Builder()
        builder.translation("teutonlib.configuration.modules").push("modules")
        builder.translation("teutonlib.configuration.createSableOptimization").push("createSableOptimization")
        CSO_ENABLED = builder.comment("Enable cogwheel bridges across locked Sable/Simulated docks.",
            "Takes effect during play. The server controls all clients.",
            "Requires Create 6.0.10, Sable 2.0.5 and Simulated 1.3.2.").translation("teutonlib.configuration.createSableOptimization.enabled").define("enabled", true)
        builder.pop(2)
        builder.translation("teutonlib.configuration.compat").push("compat")
        builder.translation("teutonlib.configuration.simulated").push("simulated")
        ENABLE_ANALOG_DOCKING = builder.comment(
            "Analog docking: signals 0-5 retract, 6-10 extend half a block, 11-15 extend one block.",
            "Both connectors use the shorter requested length. Independent of cogwheel bridge enablement.",
            "Disable to restore Simulated 1.3.2 docking behavior. Takes effect during play."
        ).translation("teutonlib.configuration.enableAnalogDocking").define("enableAnalogDocking", true)
        builder.pop(2)
        builder.translation("teutonlib.configuration.compat").push("compat")
        builder.translation("teutonlib.configuration.createNewAge").push("createNewAge")
        MOTOR_EXTENSION_ENERGY_TRANSFER = builder.comment(
            "Allow basic and advanced Create New Age motor extensions to accept Forge Energy.",
            "Energy goes directly to the attached, correctly aligned motor; no extra buffer is added.",
            "Supports Flux Points and other FE sources on every accessible side.",
            "Takes effect when the server config is loaded or reloaded."
        ).translation("teutonlib.configuration.motorExtensionEnergyTransfer").define("motorExtensionEnergyTransfer", true)
        builder.pop(2)
        builder.translation("teutonlib.configuration.compat").push("compat")
        builder.translation("teutonlib.configuration.peripheralLink").push("peripheralLink")
        PERIPHERAL_LINK_RANGE = builder.comment("Wireless range in world blocks, including moving Sable sublevels.")
            .translation("teutonlib.configuration.range").defineInRange("range", 256, 1, 30000000)
        PERIPHERAL_LINK_LIMIT = builder.comment("Maximum active Peripheral Links per computer or control desk network.")
            .translation("teutonlib.configuration.maxLinksPerNetwork").defineInRange("maxLinksPerNetwork", 64, 1, 4096)
        builder.pop(2)
        SPEC = builder.build()
    }

    /** NeoForge 21.1.234 only loads a world override if the file already exists.  */
    @JvmStatic
    fun prepareWorld(server: MinecraftServer) {
        val target = server.getWorldPath(LevelResource("serverconfig")).resolve(FILE_NAME)
        if (Files.exists(target)) return
        try {
            Files.createDirectories(target.parent)
            val global = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME)
            if (Files.isRegularFile(global)) Files.copy(global, target)
            else {
                val defaults = CommentedConfig.inMemory()
                SPEC.correct(defaults)
                TomlWriter().write(defaults, target, WritingMode.REPLACE)
            }
        } catch (e: IOException) {
            throw IllegalStateException("Cannot create world config " + target, e)
        }
    }
}
