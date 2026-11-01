package de.TeutonStudio.TeutonLib.integration

import net.neoforged.fml.loading.LoadingModList
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.security.MessageDigest
import com.bawnorton.mixinsquared.api.MixinCanceller

/** These private IP entry points are verified against the original installed releases. */
object TrainPortalCompatibility {
    val supported = mapOf("minecraft" to "1.21.1", "create" to "6.0.10", "sable" to "2.0.6",
        "create_train_functionality" to "0.1.47", "ip_aeronautics" to "2.6.4", "flywheel" to "1.0.6")
    data class Contract(val mod: String, val path: String, val sha256: String)
    val contracts = listOf(
        Contract("flywheel", "dev/engine_room/flywheel/backend/engine/EngineImpl.class", "b72d78710e7d2cdd82d4e285a8cb7194c384a83e514e278ade73438aeb1dca53"),
        Contract("flywheel", "dev/engine_room/flywheel/backend/engine/BaseInstancer.class", "84c67a168c2b978d0a97c00154b883714f0e01839ba2fce320c564db1609d32d"),
        Contract("flywheel", "dev/engine_room/flywheel/backend/engine/indirect/IndirectInstancer.class", "9429748abca22c00ba36f119ae29a0e9d4e2c811cac1bcaca21e4090b5317c52"),
        Contract("flywheel", "dev/engine_room/flywheel/backend/engine/indirect/IndirectInstancer\$InstancePage.class", "b0e14a20e1a0107834f2bed813303c191de83999a45a2f6e3d88027820dbce34"),
        Contract("flywheel", "dev/engine_room/flywheel/backend/engine/embed/EmbeddedEnvironment.class", "7891cc9af18a61cc2b38e7d0bdee7097c54bb2bb277161d8042d418961328d2a"),
        Contract("flywheel", "dev/engine_room/flywheel/backend/engine/embed/EnvironmentStorage.class", "bb089d1f7fc45ed1927873d02058d00edb7f24053c43f0aeab09cee6635ac960"),
        Contract("flywheel", "dev/engine_room/flywheel/backend/engine/LightStorage.class", "8e9ac58c401c8affbdc273eb5e165b33fea504070c4910512abfea33683b549a"),
        Contract("flywheel", "dev/engine_room/flywheel/backend/engine/LightDataCollector.class", "757f5455508c79191ce930c2774919ba5a8a82dbd2475c1211025467211f1185"),
        Contract("sable", "dev/ryanhcode/sable/neoforge/compatibility/flywheel/SableFlywheelLightStorage.class", "7c150d325532b3fd039f71a2b2e6c72181a26235b2776f5ef71c2bdae584c64a"),
        Contract("sable", "dev/ryanhcode/sable/neoforge/mixin/compatibility/flywheel/EmbeddedEnvironmentMixin.class", "8ff4319de786caef0f73f8991a78ba5a670758e77e9d23dafc2a7baaf10d7d46"),
        Contract("sable", "dev/ryanhcode/sable/neoforge/mixin/compatibility/flywheel/EnvironmentStorageMixin.class", "84a2d9feea73d782fd729ac50dd0dca56d1b0e046db9c1cac9b7116f511ab7c1"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/create_integration/IPFlywheelStraddleClip.class", "5c5a578892ae20f8027ca19b5feeaa754aecdd9b24a2ae6cb1826ed2d5bb84aa"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/create_integration/IPFlywheelStraddleClip\$ShipFarHalf.class", "912945c4f71cca28ddbbb581c25961a25a00ea96d2804f165480f050db78c02f"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/create_integration/IPFlywheelFarHalf.class", "c00ce95669268ca8e4f17d3d2fa09bf9588915ad27d7661a422369134a64c449"),
        Contract("sable", "dev/ryanhcode/sable/sublevel/render/vanilla/VanillaChunkedSubLevelRenderData.class", "8263072ae56ad75ceaf2757040c826a6395cd4d2a03d781c019d7bd2517362a7"),
        Contract("sable", "dev/ryanhcode/sable/sublevel/ClientSubLevel.class", "88d305b1f314cfdbbd395d9a1a3c8cd54b76316f38f7866bfca48eb2b6c29582"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/sable_straddle/client/IPStraddleClientSessions.class", "d21f54d7569cbca9c97e2d7c4d7a3a6d597ec1964fc2d6b9c60a1b88b471b955"),
        Contract("sable", "dev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer.class", "9084f5ec3a334322c3a05392c568af8778e952a6a43a1abcd5cecfd66b6deabb"),
        Contract("create_train_functionality", "createtrainfunctionality/AutoConvert.class", "6f22853a3992da74b4a167776802dc976dc621ec71f0897432f586bcc920e085"),
        Contract("create_train_functionality", "createtrainfunctionality/HeldBuilds.class", "058e368b9e01349f4d8b7b6481d641d91b4bdd20101bd22a0291f3344b9f60da"),
        Contract("create_train_functionality", "createtrainfunctionality/TrainLinks.class", "e7af5344edc53da47fff673fa5359da02ecf0e4bad4f424afaa41777ebd366cb"),
        Contract("create_train_functionality", "createtrainfunctionality/mixin/CarriagePortalRefusalMixin.class", "98fc2a68e9f63e24246d73c25bd11651f4e3e0d95489dcf00cc584759d3f37dd"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/create_integration/IPCreateCarriageCrossing.class", "df7cc7569a053a5b6720a518b51e8a15751f6670edfec5f4890f09964e7ffbe2"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/create_integration/IPCreateTrainSides.class", "0d94f298b4fad400078ba137fae725e6591fb75d63d14ba8fb449985a74c70a4"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/sable_straddle/IPStraddleSessions.class", "058a4a5b7b053fbc79af2a56d6542c3885052fba4ba626b8dc76086a133c0baa"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/sable_straddle/IPStraddleSync.class", "69fe0d7e68e3003ad1d0134c442eba3637089fd1bd758fd277ac863cd5600ce4"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/sable_straddle/IPStraddleSeats.class", "2f38e3760b1d094a864662433098d158b6b6827b25a81089ea54e8bfc798051a"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/sable_straddle/IPStraddleSeats\$Proxy.class", "6fc9d09326b40648c04a1f798b6dea40882ba39851d3477ded9956dc918c920b"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/sable_straddle/IPSeatProxies.class", "5620f846e503705d7fe96087d3308dc5715a8e32b5404315beba0e6ca80a0809"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/sable_integration/IPShipRiderCarry.class", "3d7565a3c6fb5d2ac9d54d949d3c2bec510c2e9c0fbd37ce45bfdabdf55416d4"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/sable_integration/IPShipThroughPortalSync.class", "c43759da48fe100955559a7a4fec946004e5c10ce38cc2ba0d7d29b509173094"),
        Contract("create", "com/simibubi/create/content/trains/entity/Carriage.class", "563fc8cfc61f08ff0de5fe6eacb746210ccbb2844a5e1ae8a98fdae4e7041e70"),
        Contract("create", "com/simibubi/create/content/trains/entity/CarriageCouplingRenderer.class", "401229c46b7122911c6081ab41431771fed508da25c1337c33bd6df4e7cab746"),
        Contract("create", "com/simibubi/create/content/trains/entity/CarriageBogey.class", "76c7af49ac8ebef3f159ad7d4e4ab4040049c4acc25f1dc4550cd0d2a326e028"),
        Contract("create_train_functionality", "createtrainfunctionality/client/CouplingAnchors.class", "2466a2abac5c020f835c96d3d0ef74f27e61bbccd76518dafdb73341b48be302"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/sable_straddle/client/IPStraddleRender.class", "dc4ef797865777b33cef7ead7efa419656982a01a36095994bf2a6cc59348704"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/mixin_sable/MixinSableChunkedRenderData_DispatcherLevel.class", "48dc79533c09e6211173b7ef5a79df3a3132dcc0e4ce044ae3886ac77cac6830"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/sable_straddle/client/IPPlaneClippingVertexConsumer.class", "eee17b402e214132db4c3883582325ff9e23508e171434b51f81b7231a39039b"),
        Contract("create_train_functionality", "createtrainfunctionality/PortalRefusal.class", "0a4c8d48040da6235d510e2f2bbb3676d3e95fa4d483979e5677fd85b14ab9ea"),
        Contract("create_train_functionality", "createtrainfunctionality/SubLevelLinks.class", "c06803e111abe238cf6a024163e9a66a5cf09bb21d253c39bc916986b2df20f3"),
        Contract("create_train_functionality", "createtrainfunctionality/LinkRegistry.class", "6663f6ac46f214589d66b7fcb52e491c9b8ba9a725f913f2148b47df050a15fb"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/sable_integration/IPSableBridge.class", "fdbdbf400193db0d94dd42a4b582a708467f56becbe04b9f680dc44159a9fbfe"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/sable_integration/IPShipPortalTraversalImpl.class", "2775aac3a6cc5087c02ff74c8e648db9bed1e6f984dc548e412762ae5401e970"),
        Contract("ip_aeronautics", "qouteall/imm_ptl/core/compat/sable_straddle/IPStraddleController.class", "1e582de2566cf7bcb8b1cd01688921727ce433fc4d6a3408471dd10dde4d34d0"),
    )

    fun supports(versions: Map<String, String>): Boolean = supported.all { (id, version) -> versions[id] == version }

    fun validate(read: (String, String) -> ByteArray?) {
        for (contract in contracts) {
            val actual = read(contract.mod, contract.path)?.let { bytes ->
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            }
            check(actual == contract.sha256) {
                "TeutonLib train portal guard: unexpected ${contract.mod} class ${contract.path} " +
                    "(sha256=$actual, expected ${contract.sha256}). Use the verified original release JARs."
            }
        }
    }

    private val enabled: Boolean by lazy {
        val mods = LoadingModList.get() ?: error("TeutonLib: mod list unavailable during train portal mixin selection")
        val versions = mods.mods.associate { it.modId to it.version.toString() }
        if (!supports(versions)) {
            if ("create_train_functionality" in versions && "ip_aeronautics" in versions) {
                LoggerFactory.getLogger("TeutonLib/TrainPortals").warn(
                    "Train portal guard disabled: requires {}; found {}", supported,
                    supported.keys.associateWith { versions[it] ?: "missing" })
            }
            false
        } else {
            validate { id, path ->
                val resource = mods.getModFileById(id)?.file?.findResource(path) ?: return@validate null
                if (Files.isRegularFile(resource)) Files.readAllBytes(resource) else null
            }
            LoggerFactory.getLogger("TeutonLib/TrainPortals").info(
                "Train portal guard enabled: coordinated CTF carriage/build transfer; original class fingerprints verified")
            true
        }
    }

    @JvmStatic fun active(): Boolean = enabled
}

class TrainPortalMixinCanceller : MixinCanceller {
    override fun shouldCancel(targetClassNames: List<String>, mixinClassName: String): Boolean {
        if (mixinClassName != "createtrainfunctionality.mixin.CarriagePortalRefusalMixin" || !TrainPortalCompatibility.active()) return false
        check(targetClassNames == listOf("com.simibubi.create.content.trains.entity.Carriage"))
        return true
    }
}
