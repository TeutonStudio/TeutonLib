package de.TeutonStudio.TeutonLib

import net.neoforged.neoforge.common.ModConfigSpec

/** Local UI preference; storage, Lua APIs and peripheral links remain available. */
object ClientConfig {
    const val FILE_NAME = "teutonlib-client.toml"
    @JvmField val SPEC: ModConfigSpec
    @JvmField val COMPUTER_UI_ENABLED: ModConfigSpec.BooleanValue
    init {
        val builder = ModConfigSpec.Builder()
        builder.translation("teutonlib.configuration.computerUi").push("computerUi")
        COMPUTER_UI_ENABLED = builder.comment(
            "Show TeutonLib's extra Sources and Channels tabs on CC block computers.",
            "Also used by the example addon screen. Addons control their own custom workspaces.",
            "Does not disable channel storage, Lua APIs or peripheral links.",
            "Changes apply to open computer screens when this config is reloaded."
        ).translation("teutonlib.configuration.computerUi.enabled").define("enabled", true)
        builder.pop()
        SPEC = builder.build()
    }
}
