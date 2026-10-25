package de.TeutonStudio.TeutonLib.compat.resources

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import de.TeutonStudio.TeutonLib.compat.CompatibilityDiagnostics
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.MetadataSectionSerializer
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.resources.IoSupplier
import net.neoforged.fml.ModList
import net.neoforged.neoforgespi.language.IModFileInfo

object ResourceCompatibility {
    private const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    private val gson = GsonBuilder().setPrettyPrinting().create()

    @JvmStatic
    fun wrap(modFile: IModFileInfo, delegate: Pack.ResourcesSupplier): Pack.ResourcesSupplier {
        val identity = identity(modFile) ?: return delegate
        val rules = rules(identity)
        return if (rules.isEmpty()) DiagnosticSupplier(identity, delegate)
        else CompatibilitySupplier(identity, delegate, rules.associateBy(Rule::resourceKey))
    }

    internal data class ModIdentity(val modId: String, val version: String, val fileName: String)

    internal sealed interface Action {
        data object Filter : Action
        data class Replace(val transform: (ByteArray) -> ByteArray) : Action
    }

    private sealed interface Resolution {
        data object KeepOriginal : Resolution
        data object Filter : Resolution
        data class Content(val bytes: ByteArray) : Resolution
    }

    internal data class Rule(
        val namespace: String,
        val path: String,
        val sha256: String,
        val action: Action
    ) {
        fun resourceKey(): String = "$namespace:$path"
    }

    private fun identity(file: IModFileInfo): ModIdentity? {
        val primary = file.mods.firstOrNull() ?: return null
        return ModIdentity(primary.modId, primary.version.toString(), file.file.fileName)
    }

    internal fun rules(identity: ModIdentity): List<Rule> = when (identity.modId to identity.version) {
        "create_radar" to "0.4.9.2-1.21.1" -> radarRules()
        "enderio" to "8.2.10-beta" -> if (athenaLoaded()) emptyList() else enderIoRules()
        "ntgl" to "3.1.5" -> ntglRules()
        "dndecor" to "2.2b" -> listOf(
            Rule(
                "dndecor",
                "sounds.json",
                "c460bdd148770d45983bfbf441d15b6b631904991118c850e10772903c5e1f12",
                Action.Replace { "{}\n".toByteArray(Charsets.UTF_8) }
            )
        )
        else -> emptyList()
    }

    private fun radarRules(): List<Rule> = listOf(
        "blockstates/radome.json",
        "blockstates/shield_jammer.json",
        "blockstates/siren.json",
        "blockstates/sky_radar.json",
        "blockstates/smart_mount.json",
        "blockstates/sonar_bearing.json",
        "blockstates/sonar_panel.json",
        "models/block/cannon_mount_radar.json"
    ).map { Rule("create_radar", it, EMPTY_SHA256, Action.Filter) }

    private fun enderIoRules(): List<Rule> = listOf(
        "models/block/athena/basic_capacitor_bank_part.json" to
            "9051f87851a71c910709501b75efd3c7701d3c62faaa6fa825664719e227537a",
        "models/block/athena/advanced_capacitor_bank_part.json" to
            "116e148e89ea20478267f2c2677a68d1591c7d12151a49889e00b3c63fc74e6e",
        "models/block/athena/vibrant_capacitor_bank_part.json" to
            "a977ae366cc23356715f2c553f170b7c77468f244a8ce9c7c6679fd336199242"
    ).map { (path, hash) -> Rule("enderio", path, hash, Action.Filter) }

    private fun ntglRules(): List<Rule> = listOf(
        Rule(
            "ntgl",
            "animations/example_chassis.animation.json",
            "7b6d860b1cf94cf0efcca468843856f3255f21eed5d75eb3610354090abec357",
            Action.Replace(::repairExampleChassis)
        ),
        Rule(
            "ntgl",
            "animations/weapons/fatman.animation.json",
            "362b467e660a43d37688b296e23c8d5db40181821261ff50b30278bedc5802e3",
            Action.Replace(::repairFatman)
        )
    )

    private fun athenaLoaded(): Boolean = runCatching { ModList.get().isLoaded("athena") }.getOrDefault(false)

    private fun repairExampleChassis(bytes: ByteArray): ByteArray {
        val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        val vector = root.getAsJsonObject("animations")
            .getAsJsonObject("power_punch")
            .getAsJsonObject("bones")
            .getAsJsonObject("body_top")
            .getAsJsonObject("rotation")
            .getAsJsonObject("0.5")
            .getAsJsonArray("vector")
        check(vector.size() == 3 && vector[1].asString == "-17.5++NaN")
        vector.set(1, com.google.gson.JsonPrimitive(-17.5))
        return (gson.toJson(root) + "\n").toByteArray(Charsets.UTF_8)
    }

    private fun repairFatman(bytes: ByteArray): ByteArray {
        val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        val keyframe = root.getAsJsonObject("animations")
            .getAsJsonObject("shot")
            .getAsJsonObject("bones")
            .getAsJsonObject("bullet")
            .getAsJsonObject("scale")
            .getAsJsonObject("0.25")
        check(keyframe.getAsJsonArray("vector").all { it.isJsonPrimitive && it.asString == "0+" })
        keyframe.add("vector", JsonArray().apply { repeat(3) { add(1) } })
        return (gson.toJson(root) + "\n").toByteArray(Charsets.UTF_8)
    }

    private open class DiagnosticSupplier(
        protected val identity: ModIdentity,
        protected val delegate: Pack.ResourcesSupplier
    ) : Pack.ResourcesSupplier {
        override fun openPrimary(location: PackLocationInfo): PackResources =
            DiagnosticResources(identity, delegate.openPrimary(location))

        override fun openFull(location: PackLocationInfo, metadata: Pack.Metadata): PackResources =
            DiagnosticResources(identity, delegate.openFull(location, metadata))
    }

    private class CompatibilitySupplier(
        identity: ModIdentity,
        delegate: Pack.ResourcesSupplier,
        private val rules: Map<String, Rule>
    ) : DiagnosticSupplier(identity, delegate) {
        override fun openPrimary(location: PackLocationInfo): PackResources =
            CompatibilityResources(identity, delegate.openPrimary(location), rules)

        override fun openFull(location: PackLocationInfo, metadata: Pack.Metadata): PackResources =
            CompatibilityResources(identity, delegate.openFull(location, metadata), rules)
    }

    private open class DiagnosticResources(
        protected val identity: ModIdentity,
        protected val delegate: PackResources
    ) : PackResources {
        override fun getRootResource(vararg elements: String): IoSupplier<InputStream>? =
            delegate.getRootResource(*elements)

        override fun getResource(type: PackType, location: ResourceLocation): IoSupplier<InputStream>? =
            delegate.getResource(type, location)

        override fun listResources(
            type: PackType,
            namespace: String,
            path: String,
            output: PackResources.ResourceOutput
        ) = delegate.listResources(type, namespace, path, output)

        override fun getNamespaces(type: PackType): Set<String> = delegate.getNamespaces(type)

        override fun <T : Any?> getMetadataSection(serializer: MetadataSectionSerializer<T>): T? {
            return try {
                delegate.getMetadataSection(serializer)
            } catch (failure: Exception) {
                logMetadataFailure(failure)
                throw failure
            }
        }

        override fun location(): PackLocationInfo = delegate.location()

        override fun isHidden(): Boolean = delegate.isHidden

        override fun close() = delegate.close()

        private fun logMetadataFailure(failure: Throwable) {
            val supplier = delegate.getRootResource("pack.mcmeta")
            val hash = try {
                supplier?.get()?.use { sha256(it.readAllBytes()) } ?: "missing"
            } catch (_: IOException) {
                "unreadable"
            }
            CompatibilityDiagnostics.errorOnce(
                "pack-metadata-${identity.modId}-$hash",
                "TeutonLib: invalid pack.mcmeta in ${identity.modId} ${identity.version} " +
                    "(${identity.fileName}, sha256=$hash)",
                failure
            )
        }
    }

    private class CompatibilityResources(
        identity: ModIdentity,
        delegate: PackResources,
        private val rules: Map<String, Rule>
    ) : DiagnosticResources(identity, delegate) {
        override fun getResource(type: PackType, location: ResourceLocation): IoSupplier<InputStream>? {
            val original = delegate.getResource(type, location) ?: return null
            if (type != PackType.CLIENT_RESOURCES) return original
            val rule = rules["${location.namespace}:${location.path}"] ?: return original
            return when (val result = resolve(rule, original)) {
                Resolution.KeepOriginal -> original
                Resolution.Filter -> null
                is Resolution.Content -> byteSupplier(result.bytes)
            }
        }

        override fun listResources(
            type: PackType,
            namespace: String,
            path: String,
            output: PackResources.ResourceOutput
        ) {
            if (type != PackType.CLIENT_RESOURCES) {
                delegate.listResources(type, namespace, path, output)
                return
            }
            delegate.listResources(type, namespace, path) { location, original ->
                val rule = rules["${location.namespace}:${location.path}"]
                if (rule == null) output.accept(location, original)
                else when (val result = resolve(rule, original)) {
                    Resolution.KeepOriginal -> output.accept(location, original)
                    Resolution.Filter -> Unit
                    is Resolution.Content -> output.accept(location, byteSupplier(result.bytes))
                }
            }
        }

        private fun resolve(rule: Rule, original: IoSupplier<InputStream>): Resolution {
            val bytes = try {
                original.get().use(InputStream::readAllBytes)
            } catch (failure: Exception) {
                CompatibilityDiagnostics.errorOnce(
                    "resource-rule-failed-${identity.modId}-${rule.resourceKey()}",
                    "TeutonLib: failed to read ${rule.resourceKey()} while evaluating its resource rule",
                    failure
                )
                return Resolution.KeepOriginal
            }
            val actual = sha256(bytes)
            if (actual != rule.sha256) {
                CompatibilityDiagnostics.warnOnce(
                    "resource-fingerprint-${identity.modId}-${rule.resourceKey()}",
                    "TeutonLib: left {} from {} {} unchanged because its SHA-256 is not the verified release value",
                    rule.resourceKey(),
                    identity.modId,
                    identity.version
                )
                return Resolution.Content(bytes)
            }
            return try {
                CompatibilityDiagnostics.infoOnce(
                    "resource-rule-${identity.modId}-${rule.resourceKey()}",
                    "TeutonLib: applied verified resource repair for {} {} / {}",
                    identity.modId,
                    identity.version,
                    rule.resourceKey()
                )
                when (val action = rule.action) {
                    Action.Filter -> Resolution.Filter
                    is Action.Replace -> Resolution.Content(action.transform(bytes))
                }
            } catch (failure: Exception) {
                CompatibilityDiagnostics.errorOnce(
                    "resource-transform-failed-${identity.modId}-${rule.resourceKey()}",
                    "TeutonLib: failed to transform ${rule.resourceKey()}; using its original bytes",
                    failure
                )
                Resolution.Content(bytes)
            }
        }
    }

    private fun byteSupplier(bytes: ByteArray): IoSupplier<InputStream> = IoSupplier { ByteArrayInputStream(bytes) }

    internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
