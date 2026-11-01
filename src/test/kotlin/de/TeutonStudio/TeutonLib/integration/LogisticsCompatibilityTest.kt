package de.TeutonStudio.TeutonLib.integration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.jar.JarFile

class LogisticsCompatibilityTest {
    private fun bytes(mod: String, path: String): ByteArray? {
        val jar = System.getProperty("teutonlib.compatibilityJars").split(File.pathSeparator)
            .map(::File).single { file ->
                when (mod) {
                    "create" -> file.name == "create-1.21.1-6.0.10.jar"
                    "fluidlogistics" -> file.name == "fluidlogistics-1.3.1-mc1.21.1.jar"
                    else -> file.name == "$mod-1.21.1-1.4.9.jar"
                }
            }
        return JarFile(jar).use { zip -> zip.getJarEntry(path)?.let { zip.getInputStream(it).readBytes() } }
    }

    @Test fun `every required mod must have its exact supported version`() {
        assertTrue(LogisticsCompatibility.supports(LogisticsCompatibility.supported))
        for ((mod, _) in LogisticsCompatibility.supported) {
            assertFalse(LogisticsCompatibility.supports(LogisticsCompatibility.supported - mod), mod)
            assertFalse(LogisticsCompatibility.supports(LogisticsCompatibility.supported + (mod to "unsupported")), mod)
        }
        assertFalse(LogisticsCompatibility.supports(emptyMap()))
    }

    @Test fun `original release bytecode matches early activation contract`() {
        LogisticsCompatibility.validate(::bytes)
    }

    @Test fun `missing or modified bytecode fails before either replacement is enabled`() {
        for (contract in LogisticsCompatibility.contracts) {
            assertThrows(IllegalStateException::class.java) {
                LogisticsCompatibility.validate { mod, path ->
                    if (mod == contract.mod && path == contract.path) null else bytes(mod, path)
                }
            }
            assertThrows(IllegalStateException::class.java) {
                LogisticsCompatibility.validate { mod, path ->
                    if (mod == contract.mod && path == contract.path) byteArrayOf(1, 2, 3) else bytes(mod, path)
                }
            }
        }
    }

    @Test fun `replacement injectors select exact generic overloads added by Factory Logistics`() {
        val pairs = mapOf(
            "ResourcePromiseQueueMixin" to "ru/zznty/create_factory_logistics/mixin/logistics/packager/GenericPromiseQueueMixin.class",
            "ResourceInventorySummaryMixin" to "ru/zznty/create_factory_logistics/mixin/logistics/stock/InventorySummaryMixin.class"
        )
        for ((mixin, target) in pairs) {
            val factory = ClassNode()
            ClassReader(bytes("create_factory_logistics", target)!!).accept(factory, 0)
            val create = ClassNode()
            val original = if (mixin == "ResourcePromiseQueueMixin")
                "com/simibubi/create/content/logistics/packagerLink/RequestPromiseQueue.class" else
                "com/simibubi/create/content/logistics/packager/InventorySummary.class"
            ClassReader(bytes("create", original)!!).accept(create, 0)
            val signatures = (factory.methods + create.methods).map { it.name + it.desc }.toSet()
            val replacement = ClassNode()
            javaClass.classLoader.getResourceAsStream("de/TeutonStudio/TeutonLib/mixin/logistics/$mixin.class")!!.use {
                ClassReader(it).accept(replacement, 0)
            }
            var checked = 0
            for (method in replacement.methods) {
                val inject = method.visibleAnnotations.orEmpty().singleOrNull {
                    it.desc == "Lorg/spongepowered/asm/mixin/injection/Inject;"
                } ?: continue
                val attributes = inject.values.chunked(2).associate { it[0] to it[1] }
                val selectors = attributes.getValue("method") as List<*>
                assertEquals(1, selectors.size)
                val selector = selectors.single() as String
                assertTrue(selector.contains('('), selector)
                assertTrue(selector in signatures, selector)
                assertEquals(1, attributes["require"])
                checked++
            }
            assertEquals(if (mixin == "ResourcePromiseQueueMixin") 3 else 6, checked, mixin)
        }
    }
}
