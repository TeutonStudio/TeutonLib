package de.TeutonStudio.TeutonLib

import de.TeutonStudio.TeutonLib.mixin.computer.client.DbwClientMixin
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

class DbwClientMixinCompatibilityTest {
    @Test
    fun `drivebywire shadows and injector stay on the mixin class`() {
        val mixin = DbwClientMixin::class.java
        assertTrue(Modifier.isStatic(mixin.getDeclaredField("selectedSource").modifiers))
        assertTrue(Modifier.isStatic(mixin.getDeclaredField("currentChannel").modifiers))
        assertTrue(
            mixin.declaredMethods.any { it.name == "teutonlib\$cycle" && Modifier.isStatic(it.modifiers) },
            "The injector must be merged into the DriveByWire target instead of a Kotlin companion class"
        )
        assertFalse(
            runCatching { Class.forName("${mixin.name}\$Companion") }.isSuccess,
            "A companion class would access DriveByWire's private shadow fields from outside the target class"
        )
    }
}
