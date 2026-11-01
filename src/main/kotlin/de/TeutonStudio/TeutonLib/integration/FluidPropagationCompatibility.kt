package de.TeutonStudio.TeutonLib.integration

import com.bawnorton.mixinsquared.api.MixinCanceller
import net.neoforged.fml.loading.LoadingModList
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.security.MessageDigest

/** Independent of Factory Logistics: this conflict also occurs when that mod is absent. */
object FluidPropagationCompatibility {
    val supported = mapOf("minecraft" to "1.21.1", "create" to "6.0.10", "fluidlogistics" to "1.3.1", "tfmg" to "1.2.0")
    const val originalMixin = "com.yision.fluidlogistics.mixin.fluids.FluidPropagatorMixin"
    const val target = "com.simibubi.create.content.fluids.FluidPropagator"
    data class Contract(val mod: String, val path: String, val sha256: String)
    val contracts = listOf(
        Contract("create", "com/simibubi/create/content/fluids/FluidPropagator.class", "75cdb0d3e6c14ba33bafd077308b490ae7185b9e741552c5f5496d3f99506ca4"),
        Contract("fluidlogistics", "com/yision/fluidlogistics/mixin/fluids/FluidPropagatorMixin.class", "2c57b2008f185d6335c366c2d7dfb45c6ee02f44115bbc737b7ebd0b3eeca171"),
        Contract("fluidlogistics", "com/yision/fluidlogistics/content/fluids/fluidPump/FluidPumpNetworkUpdater.class", "46e168dc8382deefc442b7a2c067d246c8991be16613469eacb2ef7338755064"),
        Contract("fluidlogistics", "com/yision/fluidlogistics/content/fluids/fluidPump/FluidPumpNetworkUpdater\$PropagationContext.class", "c5f6999db3377f343c05b0f4c7da6929b89b9c6326ffbdc4f62129ab81dedfc7"),
        Contract("tfmg", "com/drmangotea/tfmg/mixin/FluidPropagatorMixin.class", "82c2181a971ebdd719d0d65d95c434e7a0cc016d5235e0f2b6c624b6ae1af98b"),
    )

    fun supports(versions: Map<String, String>): Boolean = supported.all { (id, version) -> versions[id] == version }

    fun validate(read: (String, String) -> ByteArray?) {
        for (contract in contracts) {
            val actual = read(contract.mod, contract.path)?.let { bytes ->
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            }
            check(actual == contract.sha256) {
                "TeutonLib FluidLogistics/TFMG adapter: unexpected ${contract.mod} class ${contract.path} " +
                    "(sha256=$actual, expected ${contract.sha256}). Use the supported original release JARs. " +
                    "The original FluidLogistics fluid propagator has not been replaced."
            }
        }
    }

    private val enabled: Boolean by lazy {
        val mods = LoadingModList.get() ?: error("TeutonLib: mod list unavailable during fluid mixin selection")
        val versions = mods.mods.associate { it.modId to it.version.toString() }
        if (!supports(versions)) {
            if ("fluidlogistics" in versions && "tfmg" in versions) {
                LoggerFactory.getLogger("TeutonLib/FluidPropagation").warn(
                    "Fluid propagation adapter disabled: requires {}; found {}", supported,
                    supported.keys.associateWith { versions[it] ?: "missing" })
            }
            false
        } else {
            validate { id, path ->
                val resource = mods.getModFileById(id)?.file?.findResource(path) ?: return@validate null
                if (Files.isRegularFile(resource)) Files.readAllBytes(resource) else null
            }
            LoggerFactory.getLogger("TeutonLib/FluidPropagation").info(
                "FluidLogistics/TFMG fluid propagation adapter enabled; original class fingerprints verified")
            true
        }
    }

    @JvmStatic fun active(): Boolean = enabled
}

class FluidPropagationMixinCanceller : MixinCanceller {
    override fun shouldCancel(targetClassNames: List<String>, mixinClassName: String): Boolean {
        if (mixinClassName != FluidPropagationCompatibility.originalMixin || !FluidPropagationCompatibility.active()) return false
        check(targetClassNames == listOf(FluidPropagationCompatibility.target)) {
            "TeutonLib fluid propagation adapter: unexpected targets for $mixinClassName: $targetClassNames"
        }
        return true
    }
}
