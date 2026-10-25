package de.TeutonStudio.TeutonLib.compat

import java.util.concurrent.ConcurrentHashMap
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object CompatibilityDiagnostics {
    private val logger: Logger = LogManager.getLogger("TeutonLib/Compatibility")
    private val emitted = ConcurrentHashMap.newKeySet<String>()

    @JvmStatic
    fun infoOnce(key: String, message: String, vararg arguments: Any?) {
        if (emitted.add("info:$key")) logger.info(message, *arguments)
    }

    @JvmStatic
    fun warnOnce(key: String, message: String, vararg arguments: Any?) {
        if (emitted.add("warn:$key")) logger.warn(message, *arguments)
    }

    @JvmStatic
    fun errorOnce(key: String, message: String, throwable: Throwable? = null) {
        if (!emitted.add("error:$key")) return
        if (throwable == null) logger.error(message) else logger.error(message, throwable)
    }

    internal fun resetForTests() {
        emitted.clear()
    }
}
