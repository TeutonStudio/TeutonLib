package de.TeutonStudio.TeutonLib.integration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import java.io.File
import java.util.jar.JarFile

class TrainPortalCompatibilityTest {
    private fun bytes(mod: String, path: String): ByteArray? {
        val file = System.getProperty("teutonlib.compatibilityJars").split(File.pathSeparator)
            .map(::File).single { it.name == when (mod) {
                "create" -> "create-1.21.1-6.0.10.jar"
                "sable" -> "sable-neoforge-1.21.1-2.0.6.jar"
                "create_train_functionality" -> "create_train_functionality-0.1.47.jar"
                "flywheel" -> "flywheel-neoforge-1.21.1-1.0.6.jar"
                else -> "ip_aeronautics-2.6.4.jar"
            } }
        return JarFile(file).use { jar -> jar.getJarEntry(path)?.let { jar.getInputStream(it).readBytes() } }
    }

    @Test fun `adapter requires every verified version and is optional without either mod`() {
        val versions = TrainPortalCompatibility.supported
        assertTrue(TrainPortalCompatibility.supports(versions))
        assertFalse(TrainPortalCompatibility.supports(versions + ("sable" to "2.0.5")))
        assertFalse(TrainPortalCompatibility.supports(versions + ("sable" to "2.0.7")))
        for (mod in versions.keys) {
            assertFalse(TrainPortalCompatibility.supports(versions - mod), mod)
            assertFalse(TrainPortalCompatibility.supports(versions + (mod to "unknown")), mod)
        }
        assertFalse(TrainPortalCompatibility.supports(emptyMap()))
    }

    @Test fun `changed private entry points fail verification`() {
        TrainPortalCompatibility.validate(::bytes)
        for (contract in TrainPortalCompatibility.contracts) {
            for (replacement in listOf(null, byteArrayOf(1, 2, 3))) {
                assertThrows(IllegalStateException::class.java) {
                    TrainPortalCompatibility.validate { mod, path ->
                        if (mod == contract.mod && path == contract.path) replacement else bytes(mod, path)
                    }
                }
            }
        }
    }

    @Test fun `release keeps Sable optional and requires the native straddling release when installed`() {
        JarFile(System.getProperty("teutonlib.architectureArtifact")).use { jar ->
            val metadata = jar.getInputStream(jar.getJarEntry("META-INF/neoforge.mods.toml"))
                .bufferedReader().use { it.readText() }
            val sable = metadata.split("[[dependencies.teutonlib]]").single { it.contains("modId=\"sable\"") }
            assertTrue(sable.contains("type=\"optional\""))
            assertTrue(sable.contains("versionRange=\"[2.0.6,)\""))
        }
    }

    @Test fun `each required injection selects an exact original method before its side effects`() {
        val prefix = "qouteall/imm_ptl/core/compat/"
        val targets = mapOf(
            "ShipPortalTransferMixin" to prefix + "sable_integration/IPSableBridge.class",
            "StraddlingTrainBuildMixin" to prefix + "sable_straddle/IPStraddleController.class",
            "FallbackTrainBuildMixin" to prefix + "sable_integration/IPShipPortalTraversalImpl.class"
        )
        for ((mixin, path) in targets) {
            val target = ClassNode().also { ClassReader(bytes("ip_aeronautics", path)!!).accept(it, 0) }
            val node = ClassNode()
            javaClass.classLoader.getResourceAsStream("de/TeutonStudio/TeutonLib/mixin/train/$mixin.class")!!.use {
                ClassReader(it).accept(node, 0)
            }
            val injects = node.methods.flatMap { method -> method.visibleAnnotations.orEmpty() }
                .filter { it.desc == "Lorg/spongepowered/asm/mixin/injection/Inject;" }
            assertEquals(if (mixin == "FallbackTrainBuildMixin") 1 else 2, injects.size, mixin)
            for (inject in injects) {
                val attributes = inject.values.chunked(2).associate { it[0] to it[1] }
                val selector = (attributes.getValue("method") as List<*>).single() as String
                assertTrue(target.methods.any { it.name + it.desc == selector }, selector)
                assertEquals(!selector.startsWith("reloadInDestination") && selector != "reset()V", attributes["cancellable"] ?: false)
                assertEquals(1, attributes["require"])
                val locations = (attributes.getValue("at") as List<*>).map {
                    (it as org.objectweb.asm.tree.AnnotationNode).values.chunked(2).associate { it[0] to it[1] }["value"]
                }
                assertTrue("HEAD" in locations)
                if (selector.startsWith("reloadInDestination")) assertTrue("RETURN" in locations)
            }
        }
    }

    @Test fun `coordinated lifecycle hooks select exact pinned CTF methods`() {
        val targets = mapOf("CtfPortalLifecycleMixin" to "SubLevelLinks", "CtfBuildRemovalMixin" to "HeldBuilds",
            "CtfAutoConvertMixin" to "AutoConvert", "CtfTrainLinksMixin" to "TrainLinks",
            "client/CtfPortalRenderingMixin" to "TrainLinks")
        for ((mixin, name) in targets) {
            val target = ClassNode().also { ClassReader(bytes("create_train_functionality", "createtrainfunctionality/$name.class")!!).accept(it, 0) }
            val node = ClassNode()
            javaClass.classLoader.getResourceAsStream("de/TeutonStudio/TeutonLib/mixin/train/$mixin.class")!!.use {
                ClassReader(it).accept(node, 0)
            }
            for (inject in node.methods.flatMap { it.visibleAnnotations.orEmpty() }.filter {
                it.desc == "Lorg/spongepowered/asm/mixin/injection/Inject;"
            }) {
                val args = inject.values.chunked(2).associate { it[0] to it[1] }
                val selector = (args.getValue("method") as List<*>).single() as String
                assertTrue(target.methods.any { it.name + it.desc == selector }, "$mixin: $selector")
                assertEquals(1, args["require"])
            }
        }
    }

    @Test fun `null world is permitted at both Kotlin ABI boundaries`() {
        val paths = mapOf(
            "de/TeutonStudio/TeutonLib/mixin/train/CarriagePortalStopMixin.class" to "teutonlib\$guardTrackPortal",
            "de/TeutonStudio/TeutonLib/integration/train/TrainPortalGuard.class" to "portalListener"
        )
        for ((path, name) in paths) {
            val node = ClassNode()
            javaClass.classLoader.getResourceAsStream(path)!!.use { ClassReader(it).accept(node, 0) }
            val method = node.methods.single { it.name == name }
            val checks = method.instructions.filterIsInstance<MethodInsnNode>().filter {
                it.owner == "kotlin/jvm/internal/Intrinsics" && it.name == "checkNotNullParameter"
            }
            for (check in checks) {
                val parameter = (check.previous as org.objectweb.asm.tree.LdcInsnNode).cst
                assertNotEquals("level", parameter, "$path must accept Railways' null-world shunting")
            }
        }
    }

    @Test fun `transfer preflight runs before source removal and all assembly or rider mutations`() {
        val bridge = ClassNode().also { ClassReader(bytes("ip_aeronautics",
            "qouteall/imm_ptl/core/compat/sable_integration/IPSableBridge.class")!!).accept(it, 0) }
        val transfer = bridge.methods.single { it.name == "moveThroughPortal" && it.desc.contains("Ljava/util/Set;") }
        val calls = transfer.instructions.filterIsInstance<MethodInsnNode>()
        val preflight = calls.indexOfFirst { it.name == "whyNotMovable" }
        assertTrue(preflight >= 0)
        for (name in listOf("breakAttachedRopes", "disassembleAssemblies", "capture", "removeSubLevel", "reloadInDestination")) {
            val index = calls.indexOfFirst { it.name == name }
            assertTrue(index > preflight, "$name must follow the guarded preflight")
        }
    }

    @Test fun `rail veto wraps the actual Create portal listener and the release embeds no target mods`() {
        val carriage = ClassNode().also { ClassReader(bytes("create",
            "com/simibubi/create/content/trains/entity/Carriage.class")!!).accept(it, 0) }
        val mixin = ClassNode()
        javaClass.classLoader.getResourceAsStream("de/TeutonStudio/TeutonLib/mixin/train/CarriagePortalStopMixin.class")!!.use {
            ClassReader(it).accept(mixin, 0)
        }
        val modify = mixin.methods.flatMap { it.visibleAnnotations.orEmpty() }.single {
            it.desc == "Lorg/spongepowered/asm/mixin/injection/ModifyArg;"
        }
        val attributes = modify.values.chunked(2).associate { it[0] to it[1] }
        val selector = (attributes.getValue("method") as List<*>).single() as String
        val travel = carriage.methods.single { it.name + it.desc == selector }
        val at = attributes.getValue("at") as org.objectweb.asm.tree.AnnotationNode
        val target = at.values.chunked(2).associate { it[0] to it[1] }.getValue("target") as String
        assertTrue(travel.instructions.filterIsInstance<MethodInsnNode>().any {
            "L${it.owner};${it.name}${it.desc}" == target
        })
        assertEquals(5, attributes["index"])
        assertEquals(1, attributes["require"])
        JarFile(System.getProperty("teutonlib.architectureArtifact")).use { jar ->
            val entries = jar.entries().asSequence().map { it.name }.toList()
            assertTrue(entries.none { it.startsWith("createtrainfunctionality/") || it.startsWith("qouteall/") })
            assertTrue(entries.none { it.contains("traintest/") || it.contains("teutonlib_train_portal_test") })
        }
    }
}
