package de.TeutonStudio.TeutonLib

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/** Compare JVM descriptors without loading Minecraft or optional mod classes. */
class PublicApiCompatibilityTest {
    @Test
    fun `published 1_0 API remains binary compatible after Kotlin migration`() {
        val loader = javaClass.classLoader
        val expected = requireNotNull(loader.getResourceAsStream("public-api-1.0.0.tsv"))
            .bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }
        val actual = mutableSetOf<String>()
        expected.map { it.substringBefore('\t') }.distinct().forEach { name ->
            val bytes = requireNotNull(loader.getResourceAsStream(name.replace('.', '/') + ".class")) {
                "Published class missing: $name"
            }.use { it.readBytes() }
            ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(access: Int, method: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                    if (access and Opcodes.ACC_PUBLIC != 0)
                        actual += listOf(name, "method", method, descriptor, (access and Opcodes.ACC_STATIC != 0).toString().replaceFirstChar { it.uppercase() }).joinToString("\t")
                    return null
                }
                override fun visitField(access: Int, field: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
                    if (access and Opcodes.ACC_PUBLIC != 0)
                        actual += listOf(name, "field", field, descriptor, (access and Opcodes.ACC_STATIC != 0).toString().replaceFirstChar { it.uppercase() }).joinToString("\t")
                    return null
                }
            }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        }
        val missing = expected.filterNot { it in actual }
        assertTrue(missing.isEmpty(), "Missing published JVM API members:\n" + missing.joinToString("\n"))
    }
}
