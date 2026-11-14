package de.TeutonStudio.TeutonLib.integration

import net.neoforged.fml.loading.LoadingModList
import java.nio.file.Files
import java.security.MessageDigest

/** The tank renderer is optional and independent of trains, docking and portals. */
object TankFluidCompatibility {
    const val RENDERER = "com/simibubi/create/content/fluids/tank/FluidTankRenderer.class"
    const val RENDERER_SHA256 = "4207c9433780699a5adaa1c6febcc586888ef58b7674992578e0d512a00c7dfc"

    fun supports(versions: Map<String, String>): Boolean =
        versions["create"] == "6.0.10" && versions["sable"] == "2.0.6"

    fun validate(renderer: ByteArray) {
        val actual = MessageDigest.getInstance("SHA-256").digest(renderer).joinToString("") { "%02x".format(it) }
        check(actual == RENDERER_SHA256) { "TeutonLib tank fluid: unexpected Create tank renderer ($actual)" }
    }

    private val enabled by lazy {
        val mods = LoadingModList.get()
        if (mods == null || !supports(mods.mods.associate { it.modId to it.version.toString() })) false
        else {
            validate(Files.readAllBytes(mods.getModFileById("create").file.findResource(RENDERER)))
            true
        }
    }

    @JvmStatic fun active(): Boolean = enabled
}
