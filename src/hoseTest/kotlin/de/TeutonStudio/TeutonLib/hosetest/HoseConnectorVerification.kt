package de.TeutonStudio.TeutonLib.hosetest

import com.simibubi.create.AllBlocks
import com.simibubi.create.AllItems
import com.simibubi.create.content.fluids.FluidPropagator
import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity
import com.simibubi.create.content.fluids.pump.PumpBlock
import com.simibubi.create.content.fluids.pump.PumpBlockEntity
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity
import de.TeutonStudio.TeutonLib.integration.hose.*
import net.createmod.catnip.animation.LerpedFloat
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.material.Fluids
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction

/** Real native pump/tank networks; only the motor and fixture placement are test-controlled. */
class HoseConnectorVerification {
    private val motionBodies = mutableListOf<dev.ryanhcode.sable.sublevel.ServerSubLevel>()
    private var motionHead: java.util.UUID? = null
    private var motionDistance = 0.0
    private val motionTrace = mutableListOf<String>()
    @SubscribeEvent fun trace(event: net.neoforged.neoforge.event.tick.ServerTickEvent.Post) {
        val id = motionHead ?: return
        if (motionBodies.size < 2 || motionTrace.size >= 30) return
        val world = motionBodies[0].level
        val head = world.getEntity(id) as? HoseHeadEntity
        val connector = HoseDrainConnection.get(world.getBlockEntity(motionBodies[1].plot.centerBlock))
        motionTrace.add("${world.gameTime}: alive=${head?.isAlive} dock=${head?.dockId} owner=${head?.validOwner()?.blockPos} " +
            "distance=${if (head != null && connector != null) head.worldAnchor().distanceTo(connector.socketWorld()) else -1} tension=${connector?.tension} overload=${connector?.overloadSeconds}")
    }
    private fun origin(lane: Int) = BlockPos(4, 180, 64 + lane * 16)
    private fun socket(lane: Int) = origin(lane).east(4)
    private fun pulley(lane: Int) = origin(lane).offset(12, 4, 0)
    private fun endpoint(lane: Int) = if (lane == 0) origin(lane).east(8) else origin(lane).offset(9, 4, 0)

    @SubscribeEvent fun commands(event: RegisterCommandsEvent) {
        val command = Commands.literal("teutonlib_hose_connector_test").requires { it.hasPermission(4) }
        for (action in listOf("prepare", "dock-all", "sample", "status", "undock", "dock", "stop", "reverse", "validate", "physics", "visual", "identity", "motion-prepare", "motion-sample", "lifecycle", "reset")) {
            command.then(Commands.literal(action).executes { context ->
                try {
                    val world = context.source.level
                    val output = when (action) {
                        "prepare" -> prepare(world)
                        "dock-all" -> {
                            val owner = world.getBlockEntity(pulley(2)) as HosePulleyBlockEntity
                            val handler = world.getCapability(Capabilities.FluidHandler.BLOCK, owner.blockPos, null) as com.simibubi.create.content.fluids.hosePulley.HosePulleyFluidHandler
                            handler.internalTank.fill(FluidStack(Fluids.LAVA, 137), FluidAction.EXECUTE)
                            for (lane in 1..3) dock(world, lane)
                            for (lane in 0..3) motor(world, lane).generatedSpeed.setValue(64)
                            sample(world)
                        }
                        "sample" -> sample(world)
                        "status" -> status(world)
                        "undock" -> { (HoseDrainConnection.get(world.getBlockEntity(socket(1)))!!).release(); sample(world) }
                        "dock" -> { dock(world, 1); sample(world) }
                        "stop" -> { for (lane in 0..3) motor(world, lane).generatedSpeed.setValue(0); sample(world) }
                        "reverse" -> {
                            for (lane in 0..3) {
                                val pos = pumpPosition(lane)
                                val be = world.getBlockEntity(pos) as PumpBlockEntity
                                world.setBlockAndUpdate(pos, be.blockState.setValue(PumpBlock.FACING, be.blockState.getValue(PumpBlock.FACING).opposite))
                                be.updatePressureChange()
                            }
                            sample(world)
                        }
                        "lifecycle" -> lifecycle(world)
                        "motion-prepare" -> prepareMotion(world)
                        "motion-sample" -> sampleMotion(world)
                        "identity" -> {
                            val connector = HoseDrainConnection.get(world.getBlockEntity(socket(2)))!!
                            val head = checkNotNull(connector.head()) { "saved head not loaded" }
                            check(connector.active()) { "saved UUID relation not active" }
                            val count = world.getEntitiesOfClass(HoseHeadEntity::class.java, net.minecraft.world.phys.AABB(pulley(2)).inflate(100.0)) {
                                it.belongsTo(head.ownerPosition)
                            }.size
                            check(count == 1) { "duplicate head after load" }
                            "${connector.identity}/${head.uuid}/$count"
                        }
                        "visual" -> {
                            val owner = world.getBlockEntity(BlockPos(8, 220, 8)) as HosePulleyBlockEntity
                            val head = world.getEntity((owner as HosePulleyHost).physicalHeadId!!) as HoseHeadEntity
                            val pos = BlockPos(6, 216, 8)
                            world.setBlock(pos, AllBlocks.ITEM_DRAIN.defaultState, 3)
                            val socket = HoseDrainConnection.get(world.getBlockEntity(pos))!!
                            socket.drain.initialize()
                            val player = FakePlayerFactory.getMinecraft(world)
                            head.carry(player)
                            check(socket.dock(head, player))
                            "seated head=${head.uuid}"
                        }
                        "physics" -> physics(world)
                        "reset" -> {
                            for (lane in 0..3) {
                                tank(world, origin(lane)).tankInventory.setFluid(FluidStack(Fluids.WATER, 8000))
                                tank(world, endpoint(lane)).tankInventory.setFluid(FluidStack.EMPTY)
                                if (lane == 3) for (direction in listOf(Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.DOWN))
                                    tank(world, socket(lane).relative(direction, 2)).tankInventory.setFluid(FluidStack.EMPTY)
                            }
                            sample(world)
                        }
                        else -> validate(world)
                    }
                    context.source.sendSuccess({ Component.literal("PASS connector $action $output") }, false)
                    1
                } catch (error: Throwable) {
                    org.slf4j.LoggerFactory.getLogger("TeutonLibHoseConnectorTest").error("Connector verification failed", error)
                    context.source.sendFailure(Component.literal("FAIL connector $action $error"))
                    0
                }
            })
        }
        event.dispatcher.register(command)
    }

    private fun tank(world: ServerLevel, pos: BlockPos) = world.getBlockEntity(pos) as FluidTankBlockEntity
    private fun pipe(world: ServerLevel, pos: BlockPos, axis: Direction.Axis = Direction.Axis.X) {
        world.setBlock(pos, AllBlocks.FLUID_PIPE.get().getAxisState(axis), 3)
    }
    private fun pumpPosition(lane: Int) = if (lane >= 2) pulley(lane).west() else origin(lane).east(2)
    private fun motor(world: ServerLevel, lane: Int) = world.getBlockEntity(pumpPosition(lane).north().west()) as CreativeMotorBlockEntity
    private fun putTank(world: ServerLevel, pos: BlockPos, amount: Int = 0) {
        world.setBlock(pos, AllBlocks.FLUID_TANK.defaultState, 3)
        tank(world, pos).initialize()
        if (amount > 0) tank(world, pos).tankInventory.fill(FluidStack(Fluids.WATER, amount), FluidAction.EXECUTE)
    }

    private fun prepare(world: ServerLevel): String {
        for (x in 0..1) for (z in 3..8) world.setChunkForced(x, z, true)
        for (lane in 0..3) {
            val o = origin(lane)
            for (x in -1..14) for (y in -1..6) for (z in -2..3) world.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3)
            putTank(world, o, if (lane == 3) 0 else 8000)
            putTank(world, endpoint(lane), if (lane == 3) 8000 else 0)
            if (lane == 0) for (x in 1..7) pipe(world, o.east(x))
            else {
                for (x in 1..3) pipe(world, o.east(x))
                world.setBlock(socket(lane), AllBlocks.ITEM_DRAIN.defaultState, 3)
                (HoseDrainConnection.get(world.getBlockEntity(socket(lane)))!!).drain.initialize()
                world.setBlock(pulley(lane), AllBlocks.HOSE_PULLEY.defaultState, 3)
                val pulley = world.getBlockEntity(pulley(lane)) as HosePulleyBlockEntity
                pulley.initialize()
                val field = HosePulleyBlockEntity::class.java.getDeclaredField("offset").apply { isAccessible = true }
                (field.get(pulley) as LerpedFloat).startWithValue(20.0)
                pulley.tick()
                for (x in 10..11) pipe(world, o.offset(x, 4, 0))
                // Lane 2 pumps from the connector-side tank toward the pulley-side destination as well.
                // Dock in a separate server command after forced chunks become entity-ticking.
            }
            val pumpPos = pumpPosition(lane)
            world.setBlock(pumpPos, AllBlocks.MECHANICAL_PUMP.defaultState.setValue(PumpBlock.FACING,
                if (lane == 2) Direction.WEST else Direction.EAST), 3)
            (world.getBlockEntity(pumpPos) as PumpBlockEntity).initialize()
            world.setBlock(pumpPos.north(), AllBlocks.COGWHEEL.defaultState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.X), 3)
            world.setBlock(pumpPos.north().west(), AllBlocks.CREATIVE_MOTOR.defaultState.setValue(PumpBlock.FACING, Direction.EAST), 3)
            motor(world, lane).also { it.initialize(); it.generatedSpeed.setValue(0) }
            if (lane == 3) {
                // One source pumps through the hose into all FIVE socket outlets.
                for (direction in listOf(Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.DOWN)) {
                    pipe(world, socket(lane).relative(direction), direction.axis)
                    putTank(world, socket(lane).relative(direction, 2))
                }
            }
            FluidPropagator.propagateChangedPipe(world, pumpPos, world.getBlockState(pumpPos))
        }
        return sample(world)
    }

    private fun dock(world: ServerLevel, lane: Int) {
        val owner = world.getBlockEntity(pulley(lane)) as HosePulleyBlockEntity
        val head = world.getEntity((owner as HosePulleyHost).physicalHeadId!!) as HoseHeadEntity
        val connector = HoseDrainConnection.get(world.getBlockEntity(socket(lane)))!!
        val player = FakePlayerFactory.getMinecraft(world)
        player.setPos(connector.socketWorld().add(0.0, 0.0, 1.0))
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(AllItems.WRENCH.get()))
        // This fixture teleports its fake carrier between lanes. Seed the new cable at the
        // carried endpoint; ordinary gameplay moves the strand continuously through physics.
        de.TeutonStudio.TeutonLib.integration.sable.HoseCablePhysics.remove(head)
        val pose = head.saveWithoutId(net.minecraft.nbt.CompoundTag()).also { it.remove("CablePoints") }
        head.load(pose)
        head.setPos(connector.socketWorld())
        head.carry(player)
        check(connector.dock(head, player)) { "dock rejected lane $lane" }
        check(connector.active()) { "mutual UUIDs not active lane $lane" }
    }

    private fun sample(world: ServerLevel): String = (0..3).joinToString(";") { lane ->
        val branch = if (lane == 3) listOf(Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.DOWN)
            .sumOf { tank(world, socket(lane).relative(it, 2)).tankInventory.fluidAmount } else 0
        val source = if (lane == 3) endpoint(lane) else origin(lane)
        val target = if (lane == 3) origin(lane) else endpoint(lane)
        "$lane=${tank(world, source).tankInventory.fluidAmount},${tank(world, target).tankInventory.fluidAmount},$branch"
    }

    private fun status(world: ServerLevel): String = (0..3).joinToString("; ") { lane ->
        val positions = if (lane == 0) listOf(origin(lane).east(3)) else listOf(socket(lane), pulley(lane), pulley(lane).west())
        "$lane speed=${(world.getBlockEntity(pumpPosition(lane)) as PumpBlockEntity).speed} " + positions.joinToString { pos ->
            "$pos=${FluidPropagator.getPipe(world, pos)?.interfaces?.mapValues { (_, c) -> c.pressure.toString() + ":" + c.hasFlow() }}"
        }
    }

    private fun lifecycle(world: ServerLevel): String {
        val connector = HoseDrainConnection.get(world.getBlockEntity(socket(2)))!!
        val head = connector.head()!!
        val player = FakePlayerFactory.getMinecraft(world)
        val wrench = ItemStack(AllItems.WRENCH.get())
        val hit = net.minecraft.world.phys.BlockHitResult(connector.socketWorld(), Direction.UP, connector.blockPos, false)
        player.setPos(connector.socketWorld().add(100.0, 0.0, 0.0))
        check(connector.blockState.useItemOn(wrench, world, player,
            InteractionHand.MAIN_HAND, hit) == net.minecraft.world.ItemInteractionResult.FAIL) { "out-of-reach player operated connector" }
        check(connector.headId == head.uuid)
        check(!connector.dock(head, player)) { "occupied socket accepted another insertion" }
        check(!com.simibubi.create.content.fluids.pipes.FluidPipeBlock.canConnectTo(world, connector.blockPos, connector.blockState, Direction.DOWN)) {
            "pipe attached to reserved socket top"
        }
        val pulley = head.validOwner()!!
        check(!com.simibubi.create.content.fluids.pipes.FluidPipeBlock.canConnectTo(world, pulley.blockPos, pulley.blockState, Direction.UP)) {
            "pipe attached to reserved pulley underside"
        }
        player.setPos(connector.socketWorld().add(0.0, 0.0, 1.0))
        check(connector.blockState.useItemOn(wrench, world, player,
            InteractionHand.MAIN_HAND, hit) == net.minecraft.world.ItemInteractionResult.SUCCESS)
        check(connector.headId == null && head.isCarriedBy(player)) { "wrench did not release head into carrying" }
        val pipe = connector
        check(Direction.entries.none { pipe.canHaveFlowToward(connector.blockState, it) }) { "empty socket retained an open port" }
        // Reproduce the real click path: the carried head lies between the eye and socket.
        val eye = player.eyePosition
        val target = connector.blockPos.center.add(0.0, -.375, 0.0)
        val towardSocket = target.subtract(eye)
        head.setPos(eye.add(towardSocket.normalize().scale(.7)).add(0.0, -head.bbHeight * .5, 0.0))
        check(head.boundingBox.clip(eye, target).isPresent) { "fixture did not put the carried head in the click ray" }
        val picked = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, target,
            player.boundingBox.expandTowards(towardSocket).inflate(1.0), { it.isPickable && !it.isSpectator }, towardSocket.lengthSqr())
        check(picked?.entity !== head) { "carried head intercepted the connector click" }
        val blockHit = world.clip(net.minecraft.world.level.ClipContext(eye, target,
            net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player))
        check(blockHit.blockPos == connector.blockPos) { "click ray missed connector: $blockHit" }
        check(player.gameMode.useItemOn(player, world, wrench, InteractionHand.MAIN_HAND, blockHit).consumesAction())
        check(connector.active() && !head.isCarriedBy(player)) { "actual wrench use did not dock the carried head" }

        connector.release()
        check(FluidPropagator.getPipe(world, connector.blockPos) == null) { "empty drain still advertised a through pipe" }
        check(connector.socketLocal().y == connector.blockPos.y + 7.0 / 16.0) { "head seat did not follow supplied model" }
        val nativeHandler = world.getCapability(Capabilities.FluidHandler.BLOCK, connector.blockPos, Direction.DOWN)!!
        check(nativeHandler.tanks == 1 && nativeHandler.getTankCapacity(0) == 1500) { "native drain tank was replaced" }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(net.minecraft.world.item.Items.WATER_BUCKET))
        check(connector.blockState.useItemOn(player.mainHandItem, world, player, InteractionHand.MAIN_HAND, hit).consumesAction())
        check(nativeHandler.getFluidInTank(0).fluid === Fluids.WATER && nativeHandler.getFluidInTank(0).amount == 1000) {
            "native bucket draining stopped working"
        }
        val nativeBefore = nativeHandler.getFluidInTank(0).copy()
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench)
        verifyCarryCancellation(world, head)
        player.setPos(connector.socketWorld().add(0.0, 0.0, 1.0))
        head.carry(player)
        check(connector.dock(head, player))
        check(FluidStack.matches(nativeBefore, nativeHandler.getFluidInTank(0))) { "docking consumed native drain contents" }
        check(connector.drain.saveWithFullMetadata(world.registryAccess()).getString("id") == "create:item_drain") { "drain block entity was replaced" }
        return "native Item Drain/bucket/tank preserved, supplied head seat, server reach, occupied socket, reserved faces, click through carried head, wrench release/reinsert, air throw, deselection/slot/offhand cancellation and all ports closed when empty"
    }

    private fun verifyCarryCancellation(world: ServerLevel, head: HoseHeadEntity) {
        val player = net.neoforged.neoforge.common.util.FakePlayer(world,
            com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "HoseCarryTest"))
        player.setPos(head.position())
        player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack())
        // Entity.tick resolves carriers through Level's player list, including this temporary player.
        world.players().add(player)
        try {
            head.carry(player)
            head.tick()
            check(head.isCarriedBy(player) && !head.isPickable) { "valid wrench carry did not survive a tick" }
            val airClick = net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND)
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(airClick)
            check(airClick.isCanceled && airClick.cancellationResult.consumesAction()) { "air click did not consume the wrench interaction" }
            check(!head.isCarriedBy(player) && head.isPickable && head.deltaMovement.dot(player.lookAngle) > .5) { "air click did not throw the head" }

            head.carry(player)
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(net.minecraft.world.item.Items.STICK))
            head.tick()
            check(!head.isCarriedBy(player) && head.isPickable) { "changing the selected item retained carry state" }
            player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack())
            head.tick()
            check(!head.isCarriedBy(player)) { "reselecting wrench resumed canceled carry" }

            head.carry(player)
            player.inventory.selected = 1
            player.setItemInHand(InteractionHand.MAIN_HAND, AllItems.WRENCH.asStack())
            head.tick()
            check(!head.isCarriedBy(player)) { "switching to another wrench slot retained carry state" }

            player.setItemInHand(InteractionHand.OFF_HAND, AllItems.WRENCH.asStack())
            head.carry(player, InteractionHand.OFF_HAND)
            head.tick()
            check(head.isCarriedBy(player)) { "offhand wrench could not carry" }
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
            head.tick()
            check(!head.isCarriedBy(player) && head.isPickable) { "removing offhand wrench retained carry state" }
        } finally {
            world.players().remove(player)
        }
    }

    private fun prepareMotion(world: ServerLevel): String {
        val old = HoseDrainConnection.get(world.getBlockEntity(socket(3)))!!
        val head = old.head()!!
        for (pos in listOf(pulley(3), socket(3))) {
            motionBodies.add(dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(world, pos, listOf(pos),
                dev.ryanhcode.sable.companion.math.BoundingBox3i(pos, pos)))
        }
        val owner = world.getBlockEntity(motionBodies[0].plot.centerBlock) as HosePulleyBlockEntity
        val connector = HoseDrainConnection.get(world.getBlockEntity(motionBodies[1].plot.centerBlock))!!
        owner.initialize(); connector.drain.initialize(); owner.tick(); head.tick()
        check(connector.active())
        motionDistance = head.worldAnchor().distanceTo(connector.socketWorld())
        val field = HosePulleyBlockEntity::class.java.getDeclaredField("offset").apply { isAccessible = true }
        (field.get(owner) as LerpedFloat).startWithValue(motionDistance - HoseHeadEntity.END_ALLOWANCE - 1.0)
        owner.tick()
        motionHead = head.uuid
        return "distance=$motionDistance length=${head.hoseLength}"
    }

    private fun sampleMotion(world: ServerLevel): String {
        val head = world.getEntity(motionHead!!) as HoseHeadEntity
        val connector = checkNotNull(head.connector()) { "moderate tension unexpectedly released connector: $motionTrace" }
        val distance = head.worldAnchor().distanceTo(connector.socketWorld())
        check(distance < motionDistance - .05) { "native Sable solver did not pull ships together: $motionDistance -> $distance" }
        val output = "native Sable solver moved ships together: $motionDistance -> $distance"
        connector.release()
        head.discard()
        val container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(world)!!
        for (body in motionBodies) container.removeSubLevel(body, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED)
        container.processSubLevelRemovals()
        motionBodies.clear()
        return output
    }

    private fun physics(world: ServerLevel): String {
        val system = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.require(world)
        val container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(world)!!
        val bodies = mutableListOf<dev.ryanhcode.sable.sublevel.ServerSubLevel>()
        var owner = world.getBlockEntity(pulley(1)) as HosePulleyBlockEntity
        var connector = HoseDrainConnection.get(world.getBlockEntity(socket(1)))!!
        val head = connector.head()!!
        val id = head.uuid
        val connectorId = connector.identity
        fun assemble(pos: BlockPos): dev.ryanhcode.sable.sublevel.ServerSubLevel {
            return dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(world, pos, listOf(pos),
                dev.ryanhcode.sable.companion.math.BoundingBox3i(pos, pos)).also { bodies.add(it) }
        }
        fun step() {
            for (body in bodies) body.prePhysicsTickBegin()
            de.TeutonStudio.TeutonLib.integration.sable.HoseCablePhysics.ensure(head)
            de.TeutonStudio.TeutonLib.integration.sable.HoseCablePhysics.before(
                dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent(system, .01))
            de.TeutonStudio.TeutonLib.integration.sable.HoseCablePhysics.after(
                dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent(system, .01))
        }
        fun impulse(body: dev.ryanhcode.sable.sublevel.ServerSubLevel): org.joml.Vector3d =
            body.logicalPose().orientation().transform(org.joml.Vector3d(body.getOrCreateQueuedForceGroup(HoseConnectors.TENSION.get()).forceTotal.localForce))
        try {
            val a = assemble(owner.blockPos)
            owner = world.getBlockEntity(a.plot.centerBlock) as HosePulleyBlockEntity
            owner.initialize()
            owner.tick()
            check(head.uuid == id && head.ownerPosition == owner.blockPos && connector.active()) { "pulley assembly broke UUID connection" }
            val distance = head.worldAnchor().distanceTo(connector.socketWorld())
            head.setHoseLength((distance + 2).toFloat())
            system.pipeline.resetVelocity(a)
            step()
            check(connector.tension == 0.0) { "slack hose exerted force" }
            head.setHoseLength((distance - HoseHeadEntity.END_ALLOWANCE - .1).toFloat())
            step()
            val normal = connector.socketWorld().subtract(head.worldAnchor()).normalize()
            val va = impulse(a)
            check(va.x() * normal.x + va.y() * normal.y + va.z() * normal.z > 0) { "world-to-ship spring did not pull toward socket: $va" }
            val b = assemble(connector.blockPos)
            connector = HoseDrainConnection.get(world.getBlockEntity(b.plot.centerBlock))!!
            connector.drain.initialize()
            head.tick()
            check(connector.identity == connectorId && connector.headId == id && head.dockPosition == connector.blockPos && connector.active()) {
                "socket assembly broke mutual UUIDs"
            }
            system.pipeline.resetVelocity(a)
            system.pipeline.resetVelocity(b)
            step()
            val handleA = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(a)!!
            val handleB = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(b)!!
            val velocityA = impulse(a)
            val velocityB = impulse(b)
            check(org.joml.Vector3d(velocityA).add(velocityB).length() < 1e-6) { "spring impulses violate momentum conservation" }
            check(velocityA.dot(velocityB) < 0) { "ship-to-ship forces must oppose: $velocityA / $velocityB" }
            val target = dev.ryanhcode.sable.companion.math.Pose3d(b.logicalPose())
            target.position().add(0.0, 1.0, 0.0)
            target.orientation().rotateY(.5).rotateZ(.2)
            b.logicalPose().set(target)
            handleB.teleport(target.position(), target.orientation())
            head.tick()
            check(head.position().distanceTo(connector.socketWorld()) < 1e-6) { "head did not follow moved/rotated socket" }
            head.setHoseLength(0f)
            system.pipeline.resetVelocity(a)
            system.pipeline.resetVelocity(b)
            repeat(24) { step() }
            check(connector.headId == id && connector.overloadSeconds < .25) { "overload released too early" }
            step()
            check(connector.headId == null && head.dockId == null) { "overload did not release at 0.25 seconds: force=${connector.tension} seconds=${connector.overloadSeconds} ready=${de.TeutonStudio.TeutonLib.integration.sable.HoseCablePhysics.ready(head)} points=${de.TeutonStudio.TeutonLib.integration.sable.HoseCablePhysics.points(head).size} length=${head.cableLength()}" }
            check(HosePipeGraph.remote(world, owner.blockPos, Direction.DOWN) == null) { "overload left graph edge active" }
            return "world/ship and ship/ship pull, slack, assembly UUIDs, moved/rotated socket, overload release"
        } finally {
            connector.release()
            head.discard()
            for (body in bodies) container.removeSubLevel(body, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED)
            container.processSubLevelRemovals()
        }
    }

    private fun validate(world: ServerLevel): String {
        for (lane in 1..3) {
            val connector = HoseDrainConnection.get(world.getBlockEntity(socket(lane)))!!
            check(connector.active())
            val head = connector.head()!!
            check(HosePipeGraph.remote(world, connector.blockPos, Direction.UP) == head.ownerPosition)
            check(HosePipeGraph.remote(world, head.ownerPosition, Direction.DOWN) == connector.blockPos)
            val before = head.saveWithoutId(net.minecraft.nbt.CompoundTag())
            val restored = HoseEntities.HOSE_HEAD.get().create(world)!!
            restored.load(before)
            check(restored.dockId == connector.identity && restored.dockPosition == connector.blockPos)
            val ownerHandler = world.getCapability(Capabilities.FluidHandler.BLOCK, head.ownerPosition, null)!!
            val tankBefore = ownerHandler.getFluidInTank(0).copy()
            if (lane == 2) check(tankBefore.fluid === Fluids.LAVA && tankBefore.amount == 137) { "docking/pipe transfer consumed the existing pulley tank" }
            ownerHandler.fill(FluidStack(Fluids.LAVA, 1000), FluidAction.SIMULATE)
            check(FluidStack.matches(tankBefore, ownerHandler.getFluidInTank(0)))
        }
        return "UUIDs, graph edges, dock NBT and simulation"
    }
}
