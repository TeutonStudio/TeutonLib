package de.TeutonStudio.TeutonLib.integration

import com.bawnorton.mixinsquared.api.MixinCanceller
import net.neoforged.fml.loading.LoadingModList
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.security.MessageDigest

/** Early selection must never load Minecraft or either optional mod's classes. */
object LogisticsCompatibility {
    val supported = mapOf(
        "minecraft" to "1.21.1", "create" to "6.0.10", "fluidlogistics" to "1.3.1",
        "create_factory_logistics" to "1.4.9", "create_factory_abstractions" to "1.4.9"
    )
    val replacements = mapOf(
        "com.yision.fluidlogistics.mixin.logistics.RequestPromiseQueueMixin" to
            "com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue",
        "com.yision.fluidlogistics.mixin.logistics.InventorySummaryMixin" to
            "com.simibubi.create.content.logistics.packager.InventorySummary"
    )
    data class Contract(val mod: String, val path: String, val sha256: String)
    val contracts = listOf(
        Contract("fluidlogistics", "com/yision/fluidlogistics/mixin/logistics/RequestPromiseQueueMixin.class", "fe7c8d54477b4f051d83100bf0a5cf7474d9795cb5f10e876109651a057ce368"),
        Contract("fluidlogistics", "com/yision/fluidlogistics/mixin/logistics/InventorySummaryMixin.class", "ad4b081898445314367d03a105cd45d3be2561ffc5ba99708a82dd06e97bcbc6"),
        Contract("fluidlogistics", "com/yision/fluidlogistics/api/packager/PackageResources.class", "513e00f1a85baa8d1ee3fed2baff30daecca146a6d6d0afa24812c8cf0122b7f"),
        Contract("fluidlogistics", "com/yision/fluidlogistics/content/logistics/packageResource/PackageResourceKey.class", "07c4da5f4c294b2eb18f454ba14f202956756fa429bffd663a37068a6c5c462c"),
        Contract("create_factory_logistics", "ru/zznty/create_factory_logistics/mixin/logistics/packager/GenericPromiseQueueMixin.class", "8d88926ba9325ff90132c4497af647d84a6c30a8b1628944011401aa023146bd"),
        Contract("create_factory_logistics", "ru/zznty/create_factory_logistics/mixin/logistics/stock/InventorySummaryMixin.class", "1c306c22cdce5c9942a1136247360e9f036b0334cd51d57363f436131a78451a"),
        Contract("create_factory_abstractions", "ru/zznty/create_factory_abstractions/api/generic/stack/GenericStack.class", "725e20d2cb9a5bd452b6a667022560629c96ba71f84331fbbe5f7705904161c3"),
        Contract("create_factory_abstractions", "ru/zznty/create_factory_abstractions/generic/support/BigGenericStack.class", "4829d774f9d9bf46094bb0fa1a2f1a8eab60c7b7a89720c84582c2ed6ffafd1e"),
        Contract("create_factory_abstractions", "ru/zznty/create_factory_abstractions/generic/key/item/ItemKey.class", "aa8307896e403347e90f5824352902b4d8cf9a26fbb96e743935c25575259f36"),
        Contract("create_factory_abstractions", "ru/zznty/create_factory_abstractions/generic/key/item/ItemKeyProvider.class", "641342903d77b8a68b8493d04479a46f98a4e1d00751e8396067f91d130e2193"),
        Contract("create", "com/simibubi/create/content/logistics/packagerLink/RequestPromiseQueue.class", "051e855d83d01a9753df42bcedc5cb8275a7b7c0c808062a7ad89f525eeeb01c"),
        Contract("create", "com/simibubi/create/content/logistics/packager/InventorySummary.class", "1314eccf8e04f953b9237d511cd929b4768f008c02a3007be3eeca7e82488f88"),
    )

    fun supports(versions: Map<String, String>): Boolean = supported.all { (id, version) -> versions[id] == version }

    /** Changed binaries in an otherwise supported combination abort before cancelling either mixin. */
    fun validate(read: (String, String) -> ByteArray?) {
        for (contract in contracts) {
            val bytes = read(contract.mod, contract.path)
            val actual = bytes?.let { data -> MessageDigest.getInstance("SHA-256").digest(data)
                .joinToString("") { "%02x".format(it) } }
            check(actual == contract.sha256) {
                "TeutonLib FluidLogistics/Factory Logistics adapter: unexpected ${contract.mod} class " +
                    "${contract.path} (sha256=$actual, expected ${contract.sha256}). " +
                    "Use the original supported release JARs; neither FluidLogistics mixin has been replaced."
            }
        }
    }

    private val enabled: Boolean by lazy {
        val mods = LoadingModList.get() ?: error("TeutonLib: mod list unavailable during logistics mixin selection")
        val versions = mods.mods.associate { it.modId to it.version.toString() }
        if (!supports(versions)) {
            if ("fluidlogistics" in versions && "create_factory_logistics" in versions) {
                LoggerFactory.getLogger("TeutonLib/Logistics").warn(
                    "Logistics adapter disabled: requires {}; found {}", supported,
                    supported.keys.associateWith { versions[it] ?: "missing" })
            }
            false
        } else {
            validate { id, path ->
                val file = mods.getModFileById(id)?.file ?: return@validate null
                val resource = file.findResource(path)
                if (Files.isRegularFile(resource)) Files.readAllBytes(resource) else null
            }
            LoggerFactory.getLogger("TeutonLib/Logistics").info(
                "FluidLogistics/Factory Logistics adapter enabled; original class fingerprints verified")
            true
        }
    }

    @JvmStatic fun active(): Boolean = enabled
}

class LogisticsMixinCanceller : MixinCanceller {
    override fun shouldCancel(targetClassNames: List<String>, mixinClassName: String): Boolean {
        val target = LogisticsCompatibility.replacements[mixinClassName] ?: return false
        if (!LogisticsCompatibility.active()) return false
        check(targetClassNames == listOf(target)) {
            "TeutonLib logistics adapter: unexpected targets for $mixinClassName: $targetClassNames"
        }
        return true
    }
}
