package de.TeutonStudio.TeutonLib.compat

import gg.essential.lib.kdiscordipc.core.socket.RawPacket
import gg.essential.lib.kdiscordipc.core.socket.Socket
import java.io.File
import java.io.IOException
import net.neoforged.fml.ModList

object EssentialSocketAdapter {
    @JvmStatic
    fun create(): Any = DisabledSocket

    @JvmStatic
    fun isVerifiedVersion(): Boolean {
        val version = runCatching {
            ModList.get().getModContainerById("essential").orElse(null)?.modInfo?.version?.toString()
        }.getOrNull()
        if (version == "1.3.10.9") return true
        CompatibilityDiagnostics.warnOnce(
            "essential-version-unverified",
            "TeutonLib: Essential Discord IPC patch was not applied because loaded version '{}' is not the verified 1.3.10.9 release",
            version ?: "unknown"
        )
        return false
    }

    private object DisabledSocket : Socket {
        override val connected: Boolean = false

        override fun connect(file: File) {
            throw IOException("Discord IPC is unavailable on this Android runtime")
        }

        override fun read(): RawPacket = throw IOException("Discord IPC is unavailable on this Android runtime")

        override fun write(bytes: ByteArray) {
            throw IOException("Discord IPC is unavailable on this Android runtime")
        }

        override fun close() = Unit
    }
}
