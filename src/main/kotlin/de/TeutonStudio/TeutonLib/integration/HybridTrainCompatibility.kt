package de.TeutonStudio.TeutonLib.integration

import net.neoforged.fml.loading.LoadingModList
import java.nio.file.Files
import java.security.MessageDigest

/** The simulation backend is optional and does not require Immersive Portals. */
object HybridTrainCompatibility {
    val supported = mapOf("create" to "6.0.10", "sable" to "2.0.6", "create_train_functionality" to "0.1.47")
    val contracts = mapOf(
        "dev/ryanhcode/sable/sublevel/storage/serialization/SubLevelSerializer.class" to "8566d2675c2f5abe99dd7805ace85d20c73f60d6cfd26d66bee4aba4b9ac94b2",
        "createtrainfunctionality/AutoConvert.class" to "6f22853a3992da74b4a167776802dc976dc621ec71f0897432f586bcc920e085",
        "createtrainfunctionality/SubLevelLinks.class" to "c06803e111abe238cf6a024163e9a66a5cf09bb21d253c39bc916986b2df20f3",
        "com/simibubi/create/content/contraptions/Contraption.class" to "2f4906644fcf6fbbb99110ae0aafcb39aa427347e6742ea8768a0a24cccf164a",
        "com/simibubi/create/content/trains/station/StationBlockEntity.class" to "5dba7eecfc4e58e38d0e84483934e8742ef9f6ef81f58a15ed2754242bd0435f",
        "dev/ryanhcode/sable/sublevel/ServerSubLevel.class" to "033eff21106f748c94b4dc51426a450398392a2e896a9ba7095a7ce4270b9119",
        "dev/ryanhcode/sable/api/sublevel/SubLevelContainer.class" to "b80934d4a48fe234dfc59d5fec78d511628e6c0ab9152e8359440cf454684ad5",
        "dev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem.class" to "02829b632495306538d5331a3369dc36d55b4d2d4fbec9f4949490d8ec9d48ac",
        "dev/ryanhcode/sable/api/physics/mass/MergedMassTracker.class" to "b1dc905689fb320921cbe6e38c74eaa6302d4084d3737d1e61739d5066f94fca",
        "dev/ryanhcode/sable/api/physics/mass/MassTracker.class" to "b5cc099743fcaa6a6a1634d54a4f525e03299a0f51b48500b96647c3e3d206b0",
        "dev/ryanhcode/sable/sublevel/plot/ServerLevelPlot.class" to "974465a85e34c975abb64a2aef842d759283b7dc027407a41edf12f33b3b6643",
        "dev/ryanhcode/sable/sublevel/ClientSubLevel.class" to "88d305b1f314cfdbbd395d9a1a3c8cd54b76316f38f7866bfca48eb2b6c29582",
        "dev/ryanhcode/sable/neoforge/compatibility/flywheel/FlywheelCompatNeoForge.class" to "576ee6275bbfed69f94d7b8423d8b242e996847e8c51d2d8a0fdef3d01fd765c",
        "dev/ryanhcode/sable/sublevel/render/dispatcher/VanillaSubLevelRenderDispatcher.class" to "dca3d43d3e10b5eae0ddd52e75422c2a873856b022cb5b79d27247d8b024b9e8",
        "dev/ryanhcode/sable/sublevel/render/dispatcher/ReachAroundSubLevelRenderDispatcher.class" to "72d6078d75301df48cd3e09c4e132fe0035c1368b9953454d4b9adbacd9767f8"
    )
    fun supports(versions: Map<String, String>) = supported.all { (id, version) -> versions[id] == version }
    fun validate(read: (String, String) -> ByteArray?) {
        contracts.forEach { (path, expected) ->
            val mod = when {
                path.startsWith("createtrainfunctionality/") -> "create_train_functionality"
                path.startsWith("dev/ryanhcode/sable/") -> "sable"
                else -> "create"
            }
            val actual = read(mod, path)?.let { bytes ->
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            }
            check(actual == expected) { "TeutonLib hybrid trains: unexpected $mod class $path ($actual)" }
        }
    }
    private val enabled by lazy {
        val mods = checkNotNull(LoadingModList.get())
        if (!supports(mods.mods.associate { it.modId to it.version.toString() })) false
        else {
            validate { mod, path -> mods.getModFileById(mod)?.file?.findResource(path)?.let {
                if (Files.isRegularFile(it)) Files.readAllBytes(it) else null
            } }
            true
        }
    }
    @JvmStatic fun active() = enabled
    private fun optional(mod: String, version: String, hashes: Map<String, String>): Boolean {
        if (!active()) return false
        val file = LoadingModList.get()?.getModFileById(mod) ?: return false
        if (file.mods.none { it.modId == mod && it.version.toString() == version }) return false
        hashes.forEach { (name, expected) ->
            val bytes = Files.readAllBytes(file.file.findResource(name))
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            check(actual == expected) { "TeutonLib hybrid trains: unexpected $mod class $name ($actual)" }
        }
        return true
    }
    private val cannons by lazy { optional("createbigcannons", "5.11.7", mapOf(
        "rbasamoyai/createbigcannons/cannon_control/cannon_mount/CannonMountBlockEntity.class" to "1f65c1f883e35ba64300f0f848ce803bb59f561d26126f0c22692a967f9a0dfa",
        "rbasamoyai/createbigcannons/cannon_control/fixed_cannon_mount/FixedCannonMountBlockEntity.class" to "59fae826ef19b1fdaaf846a778523f8562d2e93209fb2f534744c99136ffa5a7")) }
    private val swivel by lazy { optional("simulated", "1.3.2", mapOf(
        "dev/simulated_team/simulated/content/blocks/swivel_bearing/SwivelBearingBlockEntity.class" to "5144d1826f7875e148a6ff80ecf8a90623d018ffb8f17c21642d1eeccb8859e6",
        "dev/simulated_team/simulated/content/blocks/docking_connector/DockingConnectorBlockEntity.class" to "25fd30d3e64ba3b6e2d11284a05ecd18df4d6a9afe792edd83924bbc59c40ff1",
        "dev/simulated_team/simulated/content/blocks/docking_connector/DockingConnectorPair.class" to "3c512b4d71963fe75225ca65f6af2fdc949f9314044420d77a6efbe80d9c1175")) }
    @JvmStatic fun cannonsActive() = cannons
    @JvmStatic fun swivelActive() = swivel
    @JvmStatic fun radarActive(): Boolean {
        if (!active()) return false
        val file = LoadingModList.get()?.getModFileById("create_radar") ?: return false
        if (file.mods.none { it.modId == "create_radar" && it.version.toString() == "0.4.9.2-1.21.1" }) return false
        val path = file.file.findResource("com/happysg/radar/block/radar/bearing/RadarContraption.class")
        val actual = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
        check(actual == "976af60b060feae2be41f4bf649c0251626c3ba75ca65a4cf1bbca1713279188") {
            "TeutonLib hybrid radar: unexpected RadarContraption class ($actual)"
        }
        return true
    }
}
