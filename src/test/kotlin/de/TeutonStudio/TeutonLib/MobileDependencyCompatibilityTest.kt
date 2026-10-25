package de.TeutonStudio.TeutonLib

import de.TeutonStudio.TeutonLib.compat.EssentialSocketAdapter
import gg.essential.lib.kdiscordipc.core.socket.Socket
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

class MobileDependencyCompatibilityTest {
    @Test
    fun optionalMobileTargetsKeepTheirVerifiedSignatures() {
        assertStatic("org/patryk3211/powergrid/electricity/sim/solver/NativeMNA", "tryLoad()V")
        assertStatic("immersive_melodies/MidiListener", "launch()V")
        assertStatic("net/mcreator/visualrecipeeditor/procedures/WorkspaceBackupProcedure", "execute()V")
        assertStatic(
            "net/createmod/catnip/gui/UIRenderHelper\$CustomRenderTarget",
            "create(Lcom/mojang/blaze3d/platform/Window;)Lnet/createmod/catnip/gui/UIRenderHelper\$CustomRenderTarget;"
        )
        val stencil = classMethods("net/createmod/catnip/gui/element/StencilElement")
        assertTrue(stencil.containsKey("render(Lnet/minecraft/client/gui/GuiGraphics;)V"))
    }

    @Test
    fun essentialNestedSocketFactoryAndContractRemainCompatible() {
        val loader = nestedClassMethods("gg/essential/util/kdiscordipc/KDiscordIPCLoader")
        assertTrue(loader.containsKey(
            "getPlatformSocket()Lgg/essential/lib/kdiscordipc/core/socket/Socket;"
        ))
        assertEquals(0, loader.getValue("getPlatformSocket()Lgg/essential/lib/kdiscordipc/core/socket/Socket;") and Opcodes.ACC_STATIC)

        val socket = nestedClassMethods("gg/essential/lib/kdiscordipc/core/socket/Socket")
        assertTrue(socket.keys.containsAll(listOf(
            "getConnected()Z",
            "connect(Ljava/io/File;)V",
            "read()Lgg/essential/lib/kdiscordipc/core/socket/RawPacket;",
            "write([B)V",
            "close()V"
        )))

        val disabled = EssentialSocketAdapter.create() as Socket
        assertFalse(disabled.connected)
        assertThrows(IOException::class.java) { disabled.connect(File("discord-ipc-0")) }
        assertThrows(IOException::class.java) { disabled.read() }
        assertThrows(IOException::class.java) { disabled.write(byteArrayOf()) }
        disabled.close()
    }

    @Test
    fun c2meNativeMathFixtureRemainsJava22AndDesktopOnly() {
        val outer = compatibilityJars().single { it.name.startsWith("c2me-neoforge-") }
        JarFile(outer).use { jar ->
            val nested = jar.getJarEntry("META-INF/jars/c2me-opts-natives-math-mc1.21.1-0.3.0+alpha.0.93.jar")
            val nativeJar = jar.getInputStream(nested).readAllBytes()
            val entries = mutableSetOf<String>()
            var moduleClass: ByteArray? = null
            JarInputStream(ByteArrayInputStream(nativeJar)).use { child ->
                while (true) {
                    val entry = child.nextJarEntry ?: break
                    entries += entry.name
                    if (entry.name == "com/ishland/c2me/opts/natives_math/ModuleEntryPoint.class") {
                        moduleClass = child.readAllBytes()
                    }
                }
            }
            val classBytes = requireNotNull(moduleClass)
            val major = ((classBytes[6].toInt() and 0xff) shl 8) or (classBytes[7].toInt() and 0xff)
            assertEquals(66, major)
            assertTrue(entries.contains("linux-x86_64-libc2me-opts-natives-math.so"))
            assertTrue(entries.contains("windows-x86_64-c2me-opts-natives-math.dll"))
            assertFalse(entries.any { "aarch64" in it && (it.endsWith(".so") || it.endsWith(".dll")) })
        }
    }

    private fun assertStatic(className: String, signature: String) {
        val methods = classMethods(className)
        assertTrue(methods.containsKey(signature), "Missing $className.$signature")
        assertTrue(methods.getValue(signature) and Opcodes.ACC_STATIC != 0, "$signature must remain static")
    }

    private fun classMethods(className: String): Map<String, Int> {
        val matches = compatibilityJars().mapNotNull { file ->
            JarFile(file).use { jar -> jar.getJarEntry("$className.class")?.let { methods(jar.getInputStream(it).readAllBytes()) } }
        }
        assertEquals(1, matches.size, "Expected exactly one fixture containing $className")
        return matches.single()
    }

    private fun nestedClassMethods(className: String): Map<String, Int> {
        val matches = mutableListOf<Map<String, Int>>()
        compatibilityJars().forEach { file ->
            JarFile(file).use { outer ->
                outer.entries().asSequence()
                    .filter { it.name.startsWith("essential-") && it.name.endsWith(".jar") }
                    .forEach { nested ->
                        val nestedBytes = outer.getInputStream(nested).readAllBytes()
                        JarInputStream(ByteArrayInputStream(nestedBytes)).use { jar ->
                            while (true) {
                                val entry = jar.nextJarEntry ?: break
                                if (entry.name == "$className.class") {
                                    matches += methods(jar.readAllBytes())
                                    break
                                }
                            }
                        }
                    }
            }
        }
        assertEquals(1, matches.size, "Expected one nested Essential class $className")
        return matches.single()
    }

    private fun methods(bytes: ByteArray): Map<String, Int> {
        val result = linkedMapOf<String, Int>()
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?
            ) = null.also { result[name + descriptor] = access }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return result
    }

    private fun compatibilityJars(): List<File> = System.getProperty("teutonlib.compatibilityJars")
        .split(File.pathSeparator)
        .filter(String::isNotBlank)
        .map(::File)
}
