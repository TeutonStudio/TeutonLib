package de.TeutonStudio.TeutonLib.integration

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.jar.JarFile

/** The distributed mod must carry the rendering bridge as well as transfer guards. */
class TrainPortalRenderingPackagingTest {
    @Test fun `release activates both native train views and the client replacement gate`() {
        JarFile(System.getProperty("teutonlib.architectureArtifact")).use { jar ->
            val config = jar.getInputStream(jar.getJarEntry("teutonlib.mixins.json")).bufferedReader().use {
                JsonParser.parseReader(it).asJsonObject
            }
            val common = config.getAsJsonArray("mixins").map { it.asString }.toSet()
            val client = config.getAsJsonArray("client").map { it.asString }.toSet()
            val nativeLifecycle = setOf("train.StraddlingTrainBuildMixin", "train.TrainPortalLoadingMixin",
                "train.TrainPortalViewerSyncMixin")
            val clientHooks = setOf("train.client.CtfPortalRenderingMixin", "train.client.TrainPortalStandingPlayerMixin",
                "train.client.CarriageCouplingPortalMixin", "train.client.TrainPortalChunkedLightingMixin",
                "train.client.TrainPortalSectionDispatcherAccess",
                "train.client.TrainPortalVertexBufferLightingMixin", "train.client.TrainPortalBlockEntityLightingMixin",
                "train.client.TrainPortalFlywheelLightingMixin", "train.client.TrainPortalFlywheelEnvironmentLightingMixin",
                "train.client.TrainPortalFlywheelStorageWorldMixin")
            assertTrue(common.containsAll(nativeLifecycle), "A transfer-only release cannot show the far half")
            assertTrue(client.containsAll(clientHooks), "Projected standing players also need client floor motion")
            assertTrue(clientHooks.none { it in common }, "ClientLinks must not load on a dedicated server")
            for (mixin in nativeLifecycle + clientHooks) {
                val path = config.get("package").asString.replace('.', '/') + "/" + mixin.replace('.', '/') + ".class"
                assertNotNull(jar.getJarEntry(path), "Configured portal hook missing from release: $path")
            }
            for (name in listOf("TrainPortalVisuals", "TrainPortalClip", "TrainPortalProjectedSupport", "client/TrainPortalClientRiders",
                "client/TrainPortalCouplings", "client/TrainPortalCouplingGeometry", "client/TrainPortalClientLighting",
                "client/TrainPortalFlywheelLighting")) {
                assertNotNull(jar.getJarEntry("de/TeutonStudio/TeutonLib/integration/train/$name.class"))
            }
            val names = jar.entries().asSequence().map { it.name }.toList()
            assertFalse(names.any { it.contains("traintest/") || it.startsWith("qouteall/") ||
                it.startsWith("createtrainfunctionality/") }, "Verification and foreign mod classes must stay outside the release")
        }
    }
}
