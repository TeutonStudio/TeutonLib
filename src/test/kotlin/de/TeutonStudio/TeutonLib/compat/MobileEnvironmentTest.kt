package de.TeutonStudio.TeutonLib.compat

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MobileEnvironmentTest {
    private val keys = listOf(
        MobileEnvironment.MODE_PROPERTY,
        MobileEnvironment.COLOR_MASK_PROPERTY,
        "java.runtime.name",
        "java.vm.name",
        "java.vendor",
        "java.specification.vendor",
        "os.version",
        "java.home",
        "user.home",
        "java.library.path"
    )
    private lateinit var original: Map<String, String?>

    @BeforeEach
    fun rememberProperties() {
        original = keys.associateWith(System::getProperty)
    }

    @AfterEach
    fun restoreProperties() {
        original.forEach { (key, value) ->
            if (value == null) System.clearProperty(key) else System.setProperty(key, value)
        }
    }

    @Test
    fun autoModeRequiresStrongAndroidEvidence() {
        System.setProperty(MobileEnvironment.MODE_PROPERTY, "auto")
        System.setProperty("java.runtime.name", "OpenJDK Runtime Environment")
        System.setProperty("java.vm.name", "OpenJDK 64-Bit Server VM")
        System.setProperty("java.vendor", "Eclipse Adoptium")
        System.setProperty("java.specification.vendor", "Oracle Corporation")
        System.setProperty("os.version", "6.10.0-linux")
        System.setProperty("java.home", "/opt/jdk-21")
        System.setProperty("user.home", "/home/test")
        System.setProperty("java.library.path", "/usr/lib")
        assertFalse(MobileEnvironment.isMobileEnabled())

        System.setProperty("java.runtime.name", "Android Runtime")
        assertTrue(MobileEnvironment.isMobileEnabled())

        System.setProperty("java.runtime.name", "OpenJDK Runtime Environment")
        System.setProperty("java.home", "/data/user/0/com.movtery.zalithlauncher/runtimes/jre-21")
        assertTrue(MobileEnvironment.isMobileEnabled())
    }

    @Test
    fun explicitModeOverridesRuntimeDetection() {
        System.setProperty("java.runtime.name", "Android Runtime")
        System.setProperty(MobileEnvironment.MODE_PROPERTY, "off")
        assertFalse(MobileEnvironment.isMobileEnabled())

        System.setProperty("java.runtime.name", "OpenJDK Runtime Environment")
        System.setProperty(MobileEnvironment.MODE_PROPERTY, "on")
        assertTrue(MobileEnvironment.isMobileEnabled())
    }
}
