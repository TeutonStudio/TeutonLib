package de.TeutonStudio.TeutonLib.traintest

import com.bawnorton.mixinsquared.MixinSquaredBootstrap
import com.bawnorton.mixinsquared.adjuster.MixinAnnotationAdjusterRegistrar
import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode
import com.bawnorton.mixinsquared.adjuster.tools.AdjustableWrapOperationNode
import com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.loading.FMLLoader
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.slf4j.LoggerFactory
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.extensibility.IMixinInfo

/** Development verification only: NeoForm recompiles and renumbers Mojang's lambdas. */
class TrainPortalTestMixinPlugin : IMixinConfigPlugin {
    private var adjust = false

    override fun onLoad(mixinPackage: String?) {
        adjust = accessoriesDevAdjustmentEnabled()
        if (!adjust) return
        MixinSquaredBootstrap.init()
        MixinAnnotationAdjusterRegistrar.register(TrainPortalAccessoriesDevAdjuster())
    }

    override fun getRefMapperConfig(): String? = null
    override fun shouldApplyMixin(targetClassName: String, mixinClassName: String): Boolean = true
    override fun acceptTargets(myTargets: MutableSet<String>, otherTargets: MutableSet<String>) {}
    override fun getMixins(): MutableList<String>? {
        if (adjust) MixinSquaredBootstrap.reOrderExtensions()
        return null
    }
    override fun preApply(target: String, node: ClassNode, mixin: String, info: IMixinInfo?) {}
    override fun postApply(target: String, node: ClassNode, mixin: String, info: IMixinInfo?) {}
}

private fun accessoriesDevAdjustmentEnabled(): Boolean =
    java.lang.Boolean.getBoolean("teutonlib.trainPortalClientTests") &&
        !FMLLoader.isProduction() && FMLLoader.getDist() == Dist.CLIENT

/** Keeps Accessories' original handlers and injection points, changing only two selectors. */
class TrainPortalAccessoriesDevAdjuster : MixinAnnotationAdjuster {
    private data class Target(val handler: String, val originalName: String, val devName: String, val desc: String) {
        val originalSelector get() = originalName + desc
        val devSelector get() = devName + desc
    }

    private val verified by lazy { verifyDevelopmentTargets() }

    override fun adjust(targetClassNames: List<String>, mixinClassName: String, methodNode: MethodNode,
        annotation: AdjustableAnnotationNode): AdjustableAnnotationNode {
        // The server loads this test plugin too. Do not resolve any client target there.
        if (!accessoriesDevAdjustmentEnabled() || mixinClassName != ACCESSORIES_MIXIN ||
            annotation !is AdjustableWrapOperationNode) return annotation
        val target = TARGETS.firstOrNull { it.handler == methodNode.name } ?: return annotation
        check(targetClassNames == listOf(MINECRAFT_TARGET)) { "Unexpected Accessories renderer target: $targetClassNames" }
        check(methodNode.desc == HANDLER_DESC) { "Accessories renderer handler descriptor changed" }
        check(annotation.getMethod() == listOf(target.originalSelector)) { "Accessories renderer selector changed" }
        val at = annotation.getAt().singleOrNull()
        check(at != null && at.value == "INVOKE" && at.target == PROVIDER_TARGET) {
            "Accessories renderer operation changed"
        }
        check(verified)
        annotation.setMethod(listOf(target.devSelector))
        LoggerFactory.getLogger("TeutonLib/TrainPortalClientTest").info(
            "Development Accessories selector adjusted: {} -> {}", target.originalName, target.devName)
        return annotation
    }

    private fun verifyDevelopmentTargets(): Boolean {
        // ModLauncher rejects untransformed bytecode-provider queries. Read the
        // resource directly, without loading or recursively transforming this class.
        val resource = MINECRAFT_TARGET.replace('.', '/') + ".class"
        val node = ClassNode()
        checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream(resource)) {
            "Missing development renderer bytecode: $resource"
        }.use { ClassReader(it).accept(node, 0) }
        check(node.name == MINECRAFT_TARGET.replace('.', '/')) { "Unexpected renderer bytecode: ${node.name}" }
        for (target in TARGETS) {
            check(node.methods.none { it.name == target.originalName && it.desc == target.desc }) {
                "Original Accessories selector still exists; development retarget is unnecessary"
            }
            val method = node.methods.singleOrNull { it.name == target.devName && it.desc == target.desc }
                ?: error("Missing unique development renderer lambda: ${target.devSelector}")
            check(method.access and (Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC) ==
                (Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC)) { "Renderer target is not a static lambda" }
            val calls = method.instructions.asSequence().filterIsInstance<MethodInsnNode>()
                .filter { it.owner == PROVIDER && it.name == "create" }.toList()
            check(calls.size == 1 && calls.single().desc == CREATE_DESC &&
                calls.single().opcode == Opcodes.INVOKEINTERFACE && calls.single().itf) {
                "Development renderer lambda no longer contains exactly one expected Provider.create call"
            }
        }
        return true
    }

    private companion object {
        const val MINECRAFT_TARGET = "net.minecraft.client.renderer.entity.EntityRenderers"
        const val ACCESSORIES_MIXIN = "io.wispforest.accessories.mixin.client.model.EntityRenderersMixin"
        const val PROVIDER = "net/minecraft/client/renderer/entity/EntityRendererProvider"
        const val CONTEXT = "Lnet/minecraft/client/renderer/entity/EntityRendererProvider\$Context;"
        const val RENDERER = "Lnet/minecraft/client/renderer/entity/EntityRenderer;"
        const val CREATE_DESC = "($CONTEXT)$RENDERER"
        const val PROVIDER_TARGET = "L$PROVIDER;create$CREATE_DESC"
        const val HANDLER_DESC = "(L$PROVIDER;$CONTEXT" +
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)$RENDERER"
        const val BUILDER = "Lcom/google/common/collect/ImmutableMap\$Builder;"
        val TARGETS = listOf(
            Target("accessories\$attemptToSaveRoot1", "lambda\$createEntityRenderers\$26", "lambda\$createEntityRenderers\$2",
                "($BUILDER$CONTEXT" + "Lnet/minecraft/world/entity/EntityType;L$PROVIDER;)V"),
            Target("accessories\$attemptToSaveRoot2", "lambda\$createPlayerRenderers\$27", "lambda\$createPlayerRenderers\$3",
                "($BUILDER$CONTEXT" + "Lnet/minecraft/client/resources/PlayerSkin\$Model;L$PROVIDER;)V")
        )
    }
}
