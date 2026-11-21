package de.TeutonStudio.TeutonLib.hosetest

import com.simibubi.create.AllBlocks
import com.simibubi.create.AllItems
import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity
import de.TeutonStudio.TeutonLib.integration.hose.*
import de.TeutonStudio.TeutonLib.integration.sable.HoseCablePhysics
import net.createmod.catnip.animation.LerpedFloat
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.RegisterCommandsEvent

/** Real physics steps occur between commands, unlike direct entity-tick regression fixtures. */
class HoseCableVerification {
    @SubscribeEvent fun commands(event: RegisterCommandsEvent) {
        val command = Commands.literal("teutonlib_cable_test").requires { it.hasPermission(4) }
        for (action in listOf("prepare","status","sample","retract","released","saved","cleanup","flux","flux-matrix","long","long-status","obstacle-move")) command.then(Commands.literal(action).executes { context ->
            try {
                val world = context.source.level
                val result = when (action) {
                    "prepare" -> prepare(world)
                    "sample" -> HoseCablePhysics.points(head(world)).joinToString(prefix="[",postfix="]") { "[${it.x},${it.y},${it.z}]" }
                    "obstacle-move" -> moveObstacle(world)
                    "flux" -> flux(world)
                    "flux-matrix" -> fluxMatrix(world)
                    "long" -> longCable(world)
                    "long-status" -> {
                        val pos=BlockPos(40,220,80)
                        val owner=world.getBlockEntity(pos) as HosePulleyBlockEntity
                        val head=world.getEntity((owner as HosePulleyHost).physicalHeadId!!) as HoseHeadEntity
                        val ready=HoseCablePhysics.ready(head)
                        val active=head.connector()?.active() == true
                        if (!ready) check(HosePipeGraph.remote(world,pos,net.minecraft.core.Direction.DOWN) == null)
                        val count=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.require(world).arbitraryObjects.count()
                        "head=${head.uuid} ready=$ready active=$active objects=$count"
                    }
                    "retract" -> { length(owner(world), 3.0); owner(world).tick(); "retracting snagged cable" }
                    "released" -> { check(socket(world).headId == null) { "blocked retraction did not release overloaded socket" }; "overload released socket" }
                    "saved" -> saved(world)
                    "cleanup" -> { removeObstacle(world); socket(world).release(); head(world).discard(); world.removeBlock(OWNER,false); world.removeBlock(SOCKET,false); "removed cable" }
                    else -> status(world)
                }
                context.source.sendSuccess({ Component.literal("PASS cable $action $result") },false); 1
            } catch (failure: Throwable) {
                org.slf4j.LoggerFactory.getLogger("TeutonLibCableTest").error("Cable verification failed",failure)
                context.source.sendFailure(Component.literal("FAIL cable $action $failure")); 0
            }
        })
        event.dispatcher.register(command)
    }
    companion object {
        val OWNER = BlockPos(40,220,40)
        val SOCKET = BlockPos(50,216,40)
        private const val OBSTACLE_NAME="TeutonLib cable verification obstacle"
        private var obstacle: dev.ryanhcode.sable.sublevel.ServerSubLevel? = null
        private fun removeObstacle(world: ServerLevel) {
            val container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(world) ?: return
            container.allSubLevels.toList().filter { it.name == OBSTACLE_NAME }.forEach {
                container.removeSubLevel(it,dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED)
            }
            container.processSubLevelRemovals(); obstacle=null
        }
        private fun moveObstacle(world: ServerLevel): String {
            val blocks=(213..215).flatMap { y -> (39..41).map { z -> BlockPos(45,y,z) } }
            val body=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(world,BlockPos(45,214,40),blocks,
                dev.ryanhcode.sable.companion.math.BoundingBox3i(BlockPos(45,213,39),BlockPos(45,215,41)))
            body.name=OBSTACLE_NAME
            val pose=dev.ryanhcode.sable.companion.math.Pose3d(body.logicalPose())
            pose.position().add(.25,.15,0.0); pose.orientation().rotateY(.35).rotateZ(.15)
            body.logicalPose().set(pose)
            dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(body)!!.teleport(pose.position(),pose.orientation())
            obstacle=body
            return "assembled, translated and rotated obstacle ${body.uniqueId}"
        }
        private fun owner(world: ServerLevel) = world.getBlockEntity(OWNER) as HosePulleyBlockEntity
        private fun socket(world: ServerLevel) = checkNotNull(HoseDrainConnection.get(world.getBlockEntity(SOCKET)))
        private fun head(world: ServerLevel) = world.getEntity((owner(world) as HosePulleyHost).physicalHeadId!!) as HoseHeadEntity
        private fun length(pulley: HosePulleyBlockEntity, length: Double) {
            val field = HosePulleyBlockEntity::class.java.getDeclaredField("offset").apply { isAccessible = true }
            (field.get(pulley) as LerpedFloat).startWithValue(length)
        }
        private fun prepare(world: ServerLevel): String {
            removeObstacle(world)
            for (x in 2..3) for (z in 2..3) { world.setChunkForced(x,z,true); world.getChunk(x,z) }
            (world.getBlockEntity(OWNER) as? HosePulleyBlockEntity)?.let { old ->
                (old as HosePulleyHost).physicalHeadId?.let { world.getEntity(it)?.discard() }
            }
            for (x in 38..53) for (y in 212..222) for (z in 38..43) world.setBlock(BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3)
            for (x in 38..53) for (z in 38..43) world.setBlock(BlockPos(x,212,z),Blocks.SMOOTH_STONE.defaultBlockState(),3)
            // A raised obstacle catches a sagging strand. Endpoints are above its top.
            for (y in 213..215) for (z in 39..41) world.setBlock(BlockPos(45,y,z),Blocks.STONE.defaultBlockState(),3)
            world.setBlock(OWNER,AllBlocks.HOSE_PULLEY.defaultState,3)
            world.setBlock(SOCKET,AllBlocks.ITEM_DRAIN.defaultState,3)
            val owner = owner(world); owner.initialize(); socket(world).drain.initialize()
            length(owner,16.0); owner.tick()
            val head = head(world)
            val player = FakePlayerFactory.getMinecraft(world)
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(AllItems.WRENCH.get()))
            head.carry(player)
            check(socket(world).dock(head,player))
            head.tick()
            return "head=${head.uuid} length=${head.hoseLength}"
        }
        private fun flux(world: ServerLevel): String {
            val facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING
            fun state(id: String) = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(id)).defaultBlockState()
            world.setBlock(BlockPos(42,213,43),state("create_new_age:reinforced_motor").setValue(facing,net.minecraft.core.Direction.WEST),3)
            world.setBlock(BlockPos(43,213,43),state("create_new_age:advanced_motor_extension").setValue(facing,net.minecraft.core.Direction.WEST),3)
            world.setBlock(BlockPos(44,213,43),state("fluxnetworks:flux_point"),3)
            return "rear Flux Point at 44 213 43"
        }
        private fun fluxMatrix(world: ServerLevel): String {
            val facing=net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING
            val registry=net.minecraft.core.registries.BuiltInRegistries.BLOCK
            fun state(id: String)=registry.get(net.minecraft.resources.ResourceLocation.parse(id)).defaultBlockState()
            for ((row,type) in listOf("basic","advanced").withIndex()) for (direction in net.minecraft.core.Direction.entries) {
                val pos=BlockPos(40+direction.ordinal*6,220,40+row*7)
                for (x in -2..2) for (y in -2..2) for (z in -2..2) world.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3)
                for (x in -2..2) for (z in -2..2) world.setBlock(pos.offset(x,-2,z),Blocks.SMOOTH_STONE.defaultBlockState(),3)
                world.setBlock(pos.relative(direction),state("create_new_age:reinforced_motor").setValue(facing,direction),3)
                world.setBlock(pos,state("create_new_age:${type}_motor_extension").setValue(facing,direction),3)
                for (side in net.minecraft.core.Direction.entries.filter { it != direction })
                    world.setBlock(pos.relative(side),state("fluxnetworks:flux_point"),3)
            }
            return "12 extensions with five simultaneous Flux inputs each"
        }
        private fun longCable(world: ServerLevel): String {
            val pos = BlockPos(40,220,80); val dock = BlockPos(290,216,80)
            for (x in 2..18) for (z in 4..5) { world.setChunkForced(x,z,true); world.getChunk(x,z) }
            world.setBlock(pos,AllBlocks.HOSE_PULLEY.defaultState,3)
            world.setBlock(dock,AllBlocks.ITEM_DRAIN.defaultState,3)
            val owner = world.getBlockEntity(pos) as HosePulleyBlockEntity
            val socket = checkNotNull(HoseDrainConnection.get(world.getBlockEntity(dock)))
            owner.initialize(); socket.drain.initialize(); length(owner,256.0); owner.tick()
            val head = world.getEntity((owner as HosePulleyHost).physicalHeadId!!) as HoseHeadEntity
            val player = FakePlayerFactory.getMinecraft(world)
            head.carry(player); check(socket.dock(head,player)); head.tick()
            return "head=${head.uuid} length=256 trackingDistance=250"
        }
        private fun status(world: ServerLevel): String {
            val head = head(world)
            check(socket(world).active()) { "slack dock is inactive" }
            val points = HoseCablePhysics.points(head)
            check(points.size in 2..HoseCableMath.MAX_POINTS && HoseCableMath.valid(points)) { "native topology/pose invalid: ${points.size}" }
            val direct = points.first().distanceTo(points.last())
            val routed = HoseCableMath.length(points)
            check(routed <= head.cableLength()+.5) { "native cable failed to settle to its paid-out length: $routed/${head.cableLength()}" }
            check(routed > direct+1.0) { "slack cable stayed straight: $direct/$routed" }
            check(points.drop(1).dropLast(1).minOf { it.y } < 216) { "cable has no gravity sag" }
            var samples = 0
            for ((a,b) in points.zipWithNext()) for (index in 1..9) {
                val point = a.lerp(b,index/10.0)
                val pos = BlockPos.containing(point)
                val state = world.getBlockState(pos)
                val penetrates = state.getCollisionShape(world,pos).toAabbs().any { it.move(pos).deflate(.03).contains(point) }
                check(!penetrates) { "native cable penetrated $pos at $point: $points" }
                obstacle?.takeIf { !it.isRemoved }?.let { body ->
                    val local=body.logicalPose().transformPositionInverse(point)
                    val localPos=BlockPos.containing(local)
                    val shape=world.getBlockState(localPos).getCollisionShape(world,localPos)
                    check(shape.toAabbs().none { it.move(localPos).deflate(.03).contains(local) }) { "cable penetrated moving/rotated Sable obstacle at $point" }
                }
                samples++
            }
            return "points=${points.size} routed=$routed direct=$direct collisionSamples=$samples movingObstacle=${obstacle != null} tension=${socket(world).tension}"
        }
        private fun saved(world: ServerLevel): String {
            val head = head(world)
            val tag = head.saveWithoutId(CompoundTag())
            val restored = checkNotNull(HoseEntities.HOSE_HEAD.get().create(world))
            restored.load(tag)
            check(HoseCablePhysics.points(restored) == HoseCablePhysics.points(head)) { "saved cable path changed" }
            check(restored.dockId == head.dockId && restored.ownerPosition == head.ownerPosition)
            return "segment NBT roundtrip and endpoint identities"
        }
    }
}
