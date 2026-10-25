package de.TeutonStudio.TeutonLib.compat.resources

import com.google.gson.JsonParser
import java.io.File
import java.util.jar.JarFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResourceCompatibilityTest {
    @Test
    fun createRadarRulesMatchOnlyTheEightVerifiedEmptyResources() {
        val identity = ResourceCompatibility.ModIdentity("create_radar", "0.4.9.2-1.21.1", "fixture")
        val rules = ResourceCompatibility.rules(identity)
        assertEquals(8, rules.size)
        assertTrue(rules.all { it.action == ResourceCompatibility.Action.Filter })
        rules.forEach { rule ->
            val bytes = fixtureBytes("assets/${rule.namespace}/${rule.path}")
            assertEquals(rule.sha256, ResourceCompatibility.sha256(bytes), rule.resourceKey())
            assertEquals(0, bytes.size, rule.resourceKey())
        }
        assertTrue(ResourceCompatibility.rules(identity.copy(version = "0.4.9.4-1.21.1")).isEmpty())
    }

    @Test
    fun enderIoAndDesignDecorRulesMatchPublishedBytes() {
        val enderRules = ResourceCompatibility.rules(
            ResourceCompatibility.ModIdentity("enderio", "8.2.10-beta", "fixture")
        )
        assertEquals(3, enderRules.size)
        enderRules.forEach { rule ->
            assertEquals(rule.sha256, ResourceCompatibility.sha256(fixtureBytes("assets/${rule.namespace}/${rule.path}")))
        }

        val designRule = ResourceCompatibility.rules(
            ResourceCompatibility.ModIdentity("dndecor", "2.2b", "fixture")
        ).single()
        val original = fixtureBytes("assets/${designRule.namespace}/${designRule.path}")
        assertEquals(designRule.sha256, ResourceCompatibility.sha256(original))
        val replacement = (designRule.action as ResourceCompatibility.Action.Replace).transform(original)
        assertEquals(emptySet<String>(), JsonParser.parseString(replacement.toString(Charsets.UTF_8)).asJsonObject.keySet())
    }

    @Test
    fun ntglRepairsOnlyTheConfirmedKeyframes() {
        val rules = ResourceCompatibility.rules(ResourceCompatibility.ModIdentity("ntgl", "3.1.5", "fixture"))
        assertEquals(2, rules.size)
        val transformed = rules.associate { rule ->
            val original = fixtureBytes("assets/${rule.namespace}/${rule.path}")
            assertEquals(rule.sha256, ResourceCompatibility.sha256(original), rule.resourceKey())
            rule.path to (rule.action as ResourceCompatibility.Action.Replace).transform(original)
        }

        val chassis = JsonParser.parseString(
            transformed.getValue("animations/example_chassis.animation.json").toString(Charsets.UTF_8)
        ).asJsonObject
        val rotation = chassis.getAsJsonObject("animations").getAsJsonObject("power_punch")
            .getAsJsonObject("bones").getAsJsonObject("body_top").getAsJsonObject("rotation")
            .getAsJsonObject("0.5").getAsJsonArray("vector")
        assertEquals(-17.5, rotation[1].asDouble)

        val fatman = JsonParser.parseString(
            transformed.getValue("animations/weapons/fatman.animation.json").toString(Charsets.UTF_8)
        ).asJsonObject
        val scale = fatman.getAsJsonObject("animations").getAsJsonObject("shot")
            .getAsJsonObject("bones").getAsJsonObject("bullet").getAsJsonObject("scale")
            .getAsJsonObject("0.25").getAsJsonArray("vector")
        assertEquals(listOf(1, 1, 1), scale.map { it.asInt })
    }

    private fun fixtureBytes(path: String): ByteArray {
        val matches = compatibilityJars().mapNotNull { file ->
            JarFile(file).use { jar -> jar.getJarEntry(path)?.let { jar.getInputStream(it).readAllBytes() } }
        }
        assertEquals(1, matches.size, "Expected exactly one compatibility fixture containing $path")
        return matches.single()
    }

    private fun compatibilityJars(): List<File> = System.getProperty("teutonlib.compatibilityJars")
        .split(File.pathSeparator)
        .filter(String::isNotBlank)
        .map(::File)
}
