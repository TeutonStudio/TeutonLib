package de.TeutonStudio.TeutonLib.integration.hose

import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity
import de.TeutonStudio.TeutonLib.api.sable.SubLevelGeometry
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.floor

object HosePulleyBridge {
    /** Scan only the deployed column; never load terrain just to find an obstacle. */
    fun maximumLength(pulley: HosePulleyBlockEntity, length: Float): Float {
        val level = pulley.level ?: return length
        val pos = pulley.blockPos
        val anchor = SubLevelGeometry.projectToWorld(level, Vec3(pos.x + .5, pos.y.toDouble(), pos.z + .5))
        val bottom = (floor(anchor.y) - level.minBuildHeight - 1).coerceAtLeast(0.0).toFloat()
        val cursor = BlockPos.containing(anchor).mutable().move(0, -1, 0)
        val end = minOf(ceil(length.toDouble()).toInt(), bottom.toInt())
        for (step in 0 until end) {
            if (!level.hasChunkAt(cursor) || !level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty) {
                return minOf(bottom, step.toFloat())
            }
            cursor.move(0, -1, 0)
        }
        return bottom
    }

    fun tick(pulley: HosePulleyBlockEntity, length: Float) {
        val level = pulley.level as? ServerLevel ?: return
        if (pulley.isRemoved || level.getBlockEntity(pulley.blockPos) !== pulley) return
        val host = pulley as HosePulleyHost
        val id = host.physicalHeadId
        var head = id?.let(level::getEntity) as? HoseHeadEntity
        val enabled = HoseHeadPolicy.enabled(pulley)
        var changed = host.physicalHeadEnabled != enabled
        host.physicalHeadEnabled = enabled
        if (!enabled) {
            if (head != null) {
                head.discard()
                host.physicalHeadId = null
                host.physicalHeadPosition = null
                changed = true
            }
            // Reserve an unknown/unloaded UUID. Enabling must wait for that head rather than
            // create a duplicate; a loaded entity also checks the policy on its own tick.
            host.physicalHeadMissingTicks = 0
            if (changed) {
                pulley.setChanged()
                pulley.sendData()
            }
            return
        }
        if (changed) {
            pulley.setChanged()
            pulley.sendData()
        }
        if (head != null && !head.isAlive) head = null
        // Assembly/disassembly carries the saved UUID to a new block position.
        if (head != null && !head.belongsTo(pulley.blockPos)) head.configure(pulley.blockPos, length)
        if (head == null) {
            // An entity in an unloaded chunk is still owned by this pulley. Wait for it.
            val lastPosition = host.physicalHeadPosition
            if (id != null && (lastPosition == null || !level.hasChunkAt(lastPosition))) return
            if (id != null && ++host.physicalHeadMissingTicks < 40) return
            head = HoseEntities.HOSE_HEAD.get().create(level) ?: return
            head.configure(pulley.blockPos, length)
            val anchor = head.worldAnchor()
            head.setPos(anchor.x, anchor.y - .65, anchor.z)
            if (!level.addFreshEntity(head)) return
            host.physicalHeadId = head.uuid
            pulley.setChanged()
            pulley.sendData()
        }
        host.physicalHeadMissingTicks = 0
        head.setHoseLength(length)
        val position = head.blockPosition()
        if (host.physicalHeadPosition != position) {
            host.physicalHeadPosition = position
            pulley.setChanged()
        }
    }
}
