package de.TeutonStudio.TeutonLib.mixin.hybrid

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.sugar.Local
import de.TeutonStudio.TeutonLib.integration.hybrid.StationBuildRuntime
import dev.ryanhcode.sable.api.physics.PhysicsPipeline
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody
import dev.ryanhcode.sable.api.physics.mass.MassTracker
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer
import net.minecraft.nbt.CompoundTag
import org.joml.Quaterniondc
import org.joml.Vector3d
import org.joml.Vector3dc
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

/** The fallback is a serialization pivot, never a physical centre of mass.
 * Keep Sable's serializer for chunks, attachments, dependencies and user data. */
@Mixin(SubLevelSerializer::class, remap = false)
abstract class HybridBuildSerializationMixin {
    private companion object {
        @JvmStatic
        @WrapOperation(method = ["serialize"], at = [At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/physics/mass/MassTracker;getCenterOfMass()Lorg/joml/Vector3dc;")], require = 1)
        private fun `teutonlib$serializeEmptyPivot`(mass: MassTracker, original: Operation<Vector3dc?>,
            @Local(argsOnly = true) sub: ServerSubLevel): Vector3dc? {
            val center = original.call(mass)
            if (center != null || !StationBuildRuntime.construction(sub)) return center
            val anchor = sub.plot.centerBlock
            return Vector3d(anchor.x + .5, anchor.y + .5, anchor.z + .5)
        }

        @JvmStatic
        @WrapOperation(method = ["serialize"], at = [At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;of(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;")], require = 1)
        private fun `teutonlib$noAbsentBodyVelocity`(sub: ServerSubLevel, original: Operation<RigidBodyHandle?>): RigidBodyHandle? =
            if (StationBuildRuntime.construction(sub) && (StationBuildRuntime.dormant(sub) || sub.isRemoved)) null
            else original.call(sub)

        @JvmStatic
        @WrapOperation(method = ["fullyLoad"], at = [At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/plot/ServerLevelPlot;load(Lnet/minecraft/nbt/CompoundTag;)V")], require = 1)
        private fun `teutonlib$loadConstruction`(plot: ServerLevelPlot, tag: CompoundTag, original: Operation<Void>,
            @Local(argsOnly = true) saved: SubLevelData) {
            val sub = plot.subLevel
            val user = saved.fullTag().getCompound("user_data")
            if (!user.contains(StationBuildRuntime.KEY)) { original.call(plot, tag); return }
            // Must precede mass reconstruction and bounds validation, not just
            // the return from fullyLoad (Sable normally sets user_data there).
            sub.userDataTag = user.copy()
            StationBuildRuntime.park(sub)
            original.call(plot, tag)
            StationBuildRuntime.preserveBounds(sub)
            StationBuildRuntime.updatePose(sub)
            StationBuildRuntime.hold(sub)
            sub.updateLastPose()
        }

        @JvmStatic
        @WrapOperation(method = ["fullyLoad"], at = [At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;teleport(Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Lorg/joml/Vector3dc;Lorg/joml/Quaterniondc;)V")], require = 1)
        private fun `teutonlib$loadedPose`(pipeline: PhysicsPipeline, body: PhysicsPipelineBody, position: Vector3dc,
            orientation: Quaterniondc, original: Operation<Void>) {
            if (body !is ServerSubLevel || !StationBuildRuntime.construction(body)) original.call(pipeline, body, position, orientation)
        }

        @JvmStatic
        @WrapOperation(method = ["fullyLoad"], at = [At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;addLinearAndAngularVelocity(Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;)V")], require = 1)
        private fun `teutonlib$loadedVelocity`(pipeline: PhysicsPipeline, body: PhysicsPipelineBody, linear: Vector3dc,
            angular: Vector3dc, original: Operation<Void>) {
            if (body !is ServerSubLevel || !StationBuildRuntime.construction(body)) original.call(pipeline, body, linear, angular)
        }
    }
}
