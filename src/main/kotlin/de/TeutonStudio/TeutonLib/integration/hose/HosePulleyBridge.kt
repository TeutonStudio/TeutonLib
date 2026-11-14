package de.TeutonStudio.TeutonLib.integration.hose

import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity
import net.minecraft.server.level.ServerLevel

object HosePulleyBridge {
    const val MAX_LENGTH = 256f

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
