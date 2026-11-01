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
    @JvmField val HOSE_HEAD_MODE: ModConfigSpec.EnumValue<HoseHeadMode>
    @JvmField val HYBRID_TRAINS: ModConfigSpec.BooleanValue
    @JvmField val HOSE_SPRING_STIFFNESS: ModConfigSpec.DoubleValue
    @JvmField val HOSE_DAMPING: ModConfigSpec.DoubleValue
    @JvmField val HOSE_BREAK_FORCE: ModConfigSpec.DoubleValue
    @JvmField val HOSE_BREAK_DELAY: ModConfigSpec.DoubleValue
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
        builder.translation("teutonlib.configuration.compat").push("compat")
        builder.translation("teutonlib.configuration.hosePulley").push("hosePulley")
        HOSE_HEAD_MODE = builder.comment(
            "Physical hose head: ALWAYS, SABLE_ONLY or NEVER.",
            "ALWAYS enables entity heads everywhere; SABLE_ONLY only on Sable sublevels; NEVER uses Create's native hose.",
            "The server controls all clients. Changes apply during play."
        ).translation("teutonlib.configuration.hoseHeadMode").defineEnum("entityHeadMode", HoseHeadMode.ALWAYS)
        HOSE_SPRING_STIFFNESS = builder.comment("Docked hose tensile stiffness in Sable force units per block.")
            .defineInRange("springStiffness", 10000.0, 0.0, 1000000000.0)
        HOSE_DAMPING = builder.comment("Docked hose axial damping in Sable force units per block/second.")
            .defineInRange("damping", 2000.0, 0.0, 1000000000.0)
        HOSE_BREAK_FORCE = builder.comment("Maximum connector holding force in Sable force units.")
            .defineInRange("breakForce", 50000.0, 1.0, 1000000000.0)
        HOSE_BREAK_DELAY = builder.comment("Continuous overload duration in physics seconds before release.")
            .defineInRange("breakDelaySeconds", 0.25, 0.01, 60.0)
        builder.pop(2)
        builder.push("trains")
        HYBRID_TRAINS = builder.comment(
            "Use hybrid carriages by default: fixed structure, live Sable simulation and explicit editing mode.",
            "Overrides CTF trainHandling for automatic conversion. Existing inventories and child contraptions stay in one plot.",
            "Use /teutonlib train edit or /teutonlib train run beside a stopped train."
        ).define("hybridByDefault", true)
        builder.pop()
        SPEC = builder.build()
    }

    @JvmStatic
    fun hoseHeadMode(): HoseHeadMode = if (SPEC.isLoaded) HOSE_HEAD_MODE.get() else HoseHeadMode.ALWAYS

    @JvmStatic fun hybridTrains(): Boolean = if (SPEC.isLoaded) HYBRID_TRAINS.get() else true

    @JvmStatic fun hoseSpringStiffness() = if (SPEC.isLoaded) HOSE_SPRING_STIFFNESS.get() else 10000.0
    @JvmStatic fun hoseDamping() = if (SPEC.isLoaded) HOSE_DAMPING.get() else 2000.0
    @JvmStatic fun hoseBreakForce() = if (SPEC.isLoaded) HOSE_BREAK_FORCE.get() else 50000.0
    @JvmStatic fun hoseBreakDelay() = if (SPEC.isLoaded) HOSE_BREAK_DELAY.get() else 0.25

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
