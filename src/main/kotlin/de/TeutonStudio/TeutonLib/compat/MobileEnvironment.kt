package de.TeutonStudio.TeutonLib.compat

import java.util.Locale

/** Early, JDK-only environment detection safe to use from the Mixin config plugin. */
object MobileEnvironment {
    const val MODE_PROPERTY = "teutonlib.mobile.mode"
    const val COLOR_MASK_PROPERTY = "teutonlib.mobile.render.colorMask"

    enum class Mode {
        AUTO,
        ON,
        OFF
    }

    @JvmStatic
    fun mode(): Mode = when (System.getProperty(MODE_PROPERTY, "auto").lowercase(Locale.ROOT)) {
        "on", "true", "force" -> Mode.ON
        "off", "false", "disable" -> Mode.OFF
        "auto" -> Mode.AUTO
        else -> {
            CompatibilityDiagnostics.warnOnce(
                "invalid-mobile-mode",
                "TeutonLib: invalid -D{} value '{}'; using auto",
                MODE_PROPERTY,
                System.getProperty(MODE_PROPERTY)
            )
            Mode.AUTO
        }
    }

    @JvmStatic
    fun isAndroidRuntime(): Boolean {
        val runtimeMarker = listOf(
            "java.runtime.name",
            "java.vm.name",
            "java.vendor",
            "java.specification.vendor",
            "os.version"
        ).asSequence()
            .mapNotNull(System::getProperty)
            .map { it.lowercase(Locale.ROOT) }
            .any { "android" in it || "dalvik" in it }
        if (runtimeMarker) return true

        val androidEnvironment = !System.getenv("ANDROID_ROOT").isNullOrBlank() &&
            !System.getenv("ANDROID_DATA").isNullOrBlank()
        if (androidEnvironment) return true

        return listOf("java.home", "user.home", "java.library.path")
            .asSequence()
            .mapNotNull(System::getProperty)
            .map { it.lowercase(Locale.ROOT).replace('\\', '/') }
            .any { "/data/user/" in it || "/data/data/" in it || "/apex/com.android" in it }
    }

    @JvmStatic
    fun isMobileEnabled(): Boolean = when (mode()) {
        Mode.ON -> true
        Mode.OFF -> false
        Mode.AUTO -> isAndroidRuntime()
    }

    @JvmStatic
    fun isColorMaskEnabled(): Boolean = java.lang.Boolean.getBoolean(COLOR_MASK_PROPERTY)

    @JvmStatic
    fun runtimeDescription(): String = buildString {
        append(System.getProperty("java.runtime.name", "unknown runtime"))
        append(" / ")
        append(System.getProperty("java.vm.name", "unknown VM"))
        append(" / ")
        append(System.getProperty("os.name", "unknown OS"))
        append(" ")
        append(System.getProperty("os.arch", "unknown architecture"))
    }
}
