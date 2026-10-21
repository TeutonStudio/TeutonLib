package de.TeutonStudio.TeutonLib.dockingtest

import de.TeutonStudio.TeutonLib.ServerConfig
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle
import dev.ryanhcode.sable.companion.math.BoundingBox3i
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.integration.cso.docking.*
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorBlockEntity as Dock
import dev.simulated_team.simulated.content.blocks.docking_connector.DockingConnectorPair
import dev.simulated_team.simulated.index.SimBlocks
import dev.simulated_team.simulated.multiloader.tanks.CFluidType
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.properties.BlockStateProperties as Props
import net.minecraft.world.level.material.Fluids
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent

/** Runs real mixed-in block entities in a disposable server world, on its server thread.
 * Explicit ticks keep each test deterministic and do not advance the world's daylight sensors.
 * This checks stationary geometry and native constraint creation on assembled sublevels.
 * Moving-sublevel dynamics still need a gameplay acceptance run.
 */
@Mod("teutonlib_docking_test")
class AnalogDockingVerification {
    init { NeoForge.EVENT_BUS.register(this) }

    @SubscribeEvent fun commands(event: RegisterCommandsEvent) {
        event.dispatcher.register(Commands.literal("teutonlib_docking_test").requires { it.hasPermission(4) }.executes { ctx ->
            try {
                val checks = verify(ctx.source.level)
                ctx.source.sendSuccess({ Component.literal("Analog docking PASS: $checks assemblies plus transfer, geometry, persistence, signal changes and original mode") }, false)
                1
            } catch (error: Throwable) {
                TeutonLib.LOGGER.error("Analog docking verification failed", error)
                ctx.source.sendFailure(Component.literal("Analog docking FAIL: $error"))
                0
            }
        }.then(Commands.literal("prepareReload").executes { ctx ->
            prepareReload(ctx.source.level)
            ctx.source.sendSuccess({ Component.literal("Analog reload fixture prepared") }, false)
            1
        }).then(Commands.literal("probeReload").executes { ctx ->
            val a = ctx.source.level.getBlockEntity(reloadOrigin) as? Dock
            val b = ctx.source.level.getBlockEntity(reloadOrigin.east(2)) as? Dock
            val passed = a != null && b != null && a.isLocked && b.isLocked &&
                AnalogDocking.access(a).effectiveLength == DockingLength.HALF &&
                AnalogDocking.access(b).effectiveLength == DockingLength.HALF &&
                a.battery.energy == 42 && b.tank.amount == 123L &&
                ctx.source.level.getBlockState(reloadOrigin.east()).isAir
            ctx.source.sendSuccess({ Component.literal("Analog reload PASS=$passed signal=${ctx.source.level.getBestNeighborSignal(reloadOrigin)}/${ctx.source.level.getBestNeighborSignal(reloadOrigin.east(2))} A=${a?.saveWithFullMetadata(ctx.source.level.registryAccess())} B=${b?.saveWithFullMetadata(ctx.source.level.registryAccess())}") }, false)
            if (passed) 1 else 0
        }).then(Commands.literal("clearReload").executes { ctx ->
            for (x in 0..2) for (y in -1..0) for (z in 0..9) ctx.source.level.removeBlock(reloadOrigin.offset(x, y, z), false)
            1
        }))
    }

    private val reloadOrigin = BlockPos(16008, 240, 16008)
    private fun prepareReload(level: ServerLevel) {
        for (x in 0..2) for (y in -1..0) for (z in 0..9) level.removeBlock(reloadOrigin.offset(x, y, z), false)
        for (x in listOf(0, 2)) {
            val base = reloadOrigin.east(x)
            level.setBlock(base, SimBlocks.DOCKING_CONNECTOR.get().defaultBlockState().setValue(Props.FACING,
                if (x == 0) Direction.EAST else Direction.WEST).setValue(Props.POWERED, true), 3)
            for (z in 1..8) {
                level.setBlock(base.offset(0, -1, z), Blocks.STONE.defaultBlockState(), 3)
                level.setBlock(base.south(z), Blocks.REDSTONE_WIRE.defaultBlockState(), 3)
            }
            level.setBlock(base.south(9), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3)
        }
        (level.getBlockEntity(reloadOrigin) as Dock).battery.energy = 42
        (level.getBlockEntity(reloadOrigin.east(2)) as Dock).tank.readSnapshot(water, 123)
    }

    private val origin = BlockPos(8, 240, 8)
    private val water = CFluidType(Fluids.WATER, DataComponentPatch.EMPTY)

    private fun clear(level: ServerLevel) {
        for (x in -5..5) for (y in -5..5) for (z in -5..5) {
            val pos = origin.offset(x, y, z)
            if (!level.getBlockState(pos).isAir) level.removeBlock(pos, false)
        }
        Dock.MAGNET_CONTROLLER.pairMap[level]?.clear()
    }

    private fun signal(level: ServerLevel, dock: Dock, strength: Int) {
        // A real vanilla analog signal source, sampled via Level.getBestNeighborSignal.
        level.setBlock(dock.blockPos.relative(if (dock.blockState.getValue(Props.FACING).axis == Direction.Axis.Y) Direction.WEST else Direction.DOWN),
            Blocks.DAYLIGHT_DETECTOR.defaultBlockState().setValue(Props.POWER, strength), 2)
        check(level.getBestNeighborSignal(dock.blockPos) == strength) { "input strength $strength" }
    }

    private fun place(level: ServerLevel, pos: BlockPos, facing: Direction, strength: Int): Dock {
        level.setBlock(pos, SimBlocks.DOCKING_CONNECTOR.get().defaultBlockState().setValue(Props.FACING, facing).setValue(Props.POWERED, strength > 0), 3)
        val dock = level.getBlockEntity(pos) as Dock
        signal(level, dock, strength)
        return dock
    }

    private fun settle(a: Dock, b: Dock?) {
        repeat(32) {
            a.tick(); b?.tick()
            if (b != null) (Dock.MAGNET_CONTROLLER.getPair(a.level, a.blockPos, b.blockPos) as? DockingConnectorPair)?.dock(false)
        }
    }

    private fun checkTransfers(a: Dock, b: Dock) {
        check(a.isLocked && b.isLocked) { "must lock before transfer" }
        val nodeA = (a.ccWiredElement as dan200.computercraft.api.network.wired.WiredElement).node
        val nodeB = (b.ccWiredElement as dan200.computercraft.api.network.wired.WiredElement).node
        check(!nodeA.connectTo(nodeB)) { "ComputerCraft wired edge must already exist" }
        check(a.battery.receiveEnergy(123, false) == 123 && b.battery.energy == 123) { "FE forward" }
        check(b.battery.receiveEnergy(57, false) == 57 && a.battery.energy == 57) { "FE reverse" }
        check(a.tank.insert(water, 250, false) == 250L && b.tank.amount == 250L) { "fluid forward" }
        check(b.tank.insert(water, 125, false) == 125L && a.tank.amount == 125L) { "fluid reverse" }
        val remainder = a.getInventory().insertSlot(ItemStack(Items.IRON_INGOT, 3), 0, false)
        check(remainder.isEmpty && b.inventory.getItem(0).count == 3) { "item forward" }
        val reverse = b.getInventory().insertSlot(ItemStack(Items.COPPER_INGOT, 2), 0, false)
        check(reverse.isEmpty && a.inventory.getItem(0).count == 2) { "item reverse" }
    }

    private fun geometry(a: Dock, length: DockingLength) {
        check(a.getExtensionDistance(1f) == length.blocks) { "renderer extension" }
        check(kotlin.math.abs(a.tipPosition.distanceTo(a.blockPos.center) - length.contactOffset) < 1e-6) { "constraint anchor" }
        check(kotlin.math.abs(a.magnetPosition.distanceTo(a.blockPos.center) - (length.contactOffset - .1)) < 1e-6) { "magnet anchor" }
        val axis = a.blockState.getValue(Props.FACING).axis
        val box = a.getBoundingBox(a.blockState)
        val size = when (axis) { Direction.Axis.X -> box.xsize; Direction.Axis.Y -> box.ysize; else -> box.zsize }
        check(kotlin.math.abs(size - (1.0 + length.blocks)) < 1e-6) { "collision box size=$size length=$length" }
        val front = a.level!!.getBlockState(a.blockPos.relative(a.blockState.getValue(Props.FACING)))
        check(if (length == DockingLength.FULL) front.`is`(SimBlocks.PAIRED_DOCKING_CONNECTOR.get()) else front.isAir) { "phantom arm at $length" }
    }

    private fun verifyConstraints(level: ServerLevel): Int {
        val handleField = Dock::class.java.getDeclaredField("constraintHandle").apply { isAccessible = true }
        val container = checkNotNull(SubLevelContainer.getContainer(level))
        var count = 0
        for (bothMoving in listOf(false, true)) for ((left, right, distance) in listOf(Triple(6, 6, 2), Triple(6, 15, 2), Triple(15, 15, 3))) {
            clear(level)
            var a = place(level, origin, Direction.EAST, left)
            var b = place(level, origin.east(distance), Direction.WEST, right)
            val sublevels = mutableListOf<ServerSubLevel>()
            fun assemble(dock: Dock): Dock {
                val base = dock.blockPos
                val blocks = listOf(base, base.below())
                val sub = SubLevelAssemblyHelper.assembleBlocks(level, base, blocks, BoundingBox3i.from(blocks))
                sublevels.add(sub)
                val local = BlockPos.containing(sub.logicalPose().transformPositionInverse(base.center))
                return level.getBlockEntity(local) as Dock
            }
            try {
                b = assemble(b)
                if (bothMoving) a = assemble(a)
                signal(level, a, left); signal(level, b, right)
                settle(a, b)
                check(a.isLocked && b.isLocked) { "sublevel lock bothMoving=$bothMoving $left/$right select=${AnalogDocking.select(a)?.blockPos}/${AnalogDocking.select(b)?.blockPos} A=${a.saveWithFullMetadata(level.registryAccess())} B=${b.saveWithFullMetadata(level.registryAccess())} relative=${DockingConnectorPair.getRelativeTipPosition(a,b,org.joml.Vector3d())}" }
                val handles = listOfNotNull(handleField.get(a) as? FixedConstraintHandle, handleField.get(b) as? FixedConstraintHandle)
                check(handles.size == 1 && handles.single().isValid) { "exactly one valid fixed constraint" }
                checkTransfers(a, b)
                signal(level, a, 0)
                a.tick()
                check(handleField.get(a) == null && handleField.get(b) == null && !handles.single().isValid) { "constraint removed on OFF" }
                check(!a.battery.canReceive() && !b.battery.canReceive()) { "sublevel FE cleanup" }
                val nodeA = (a.ccWiredElement as dan200.computercraft.api.network.wired.WiredElement).node
                val nodeB = (b.ccWiredElement as dan200.computercraft.api.network.wired.WiredElement).node
                check(!nodeA.disconnectFrom(nodeB)) { "ComputerCraft wired edge must already be removed" }
                count++
            } finally {
                AnalogDocking.detach(a); AnalogDocking.detach(b)
                for (sub in sublevels) container.removeSubLevel(sub, SubLevelRemovalReason.REMOVED)
                container.processSubLevelRemovals()
            }
        }
        return count
    }

    private fun verify(level: ServerLevel): Int {
        val configured = ServerConfig.ENABLE_ANALOG_DOCKING.asBoolean
        ServerConfig.ENABLE_ANALOG_DOCKING.set(true)
        var checks = 0
        try {
            // All nine combinations, all six orientations, both relevant separations.
            for (direction in Direction.entries) for (left in listOf(0, 6, 11)) for (right in listOf(0, 6, 11)) for (distance in 2..3) {
                clear(level)
                val a = place(level, origin, direction, left)
                val b = place(level, origin.relative(direction, distance), direction.opposite, right)
                settle(a, b)
                val negotiated = DockingLength.fromSignal(left).negotiate(DockingLength.fromSignal(right))
                val expected = negotiated.acceptsGridDistance(distance)
                check(a.isLocked == expected && b.isLocked == expected) { "$left/$right $direction distance=$distance expected=$expected" }
                if (expected) {
                    check(AnalogDocking.access(a).effectiveLength == negotiated && AnalogDocking.access(b).effectiveLength == negotiated)
                    geometry(a, negotiated); geometry(b, negotiated)
                    checkTransfers(a, b)
                }
                checks++
            }
            for (strength in listOf(5, 6, 10, 11)) {
                clear(level)
                val a = place(level, origin, Direction.EAST, strength)
                settle(a, null)
                check(AnalogDocking.access(a).requestedLength == DockingLength.fromSignal(strength))
                check(a.otherConnector == null && !a.isLocked && !a.battery.canReceive()) { "missing partner" }
                if (strength > 5) geometry(a, DockingLength.fromSignal(strength))
            }
            // The FULL endpoint may extend before the HALF peer is even placed/loaded.
            clear(level)
            val early = place(level, origin.east(2), Direction.WEST, 15)
            settle(early, null)
            val late = place(level, origin, Direction.EAST, 6)
            settle(late, early)
            check(late.isLocked && early.isLocked && level.getBlockState(origin.east()).isAir) { "staggered half/full discovery" }

            // Changing length on a locked dock detaches both sides before any new pairing.
            clear(level)
            var a = place(level, origin, Direction.EAST, 15)
            var b = place(level, origin.east(3), Direction.WEST, 15)
            settle(a, b); checkTransfers(a, b)
            signal(level, a, 10)
            a.tick()
            check(!a.hasOtherConnector() && !b.hasOtherConnector() && !a.battery.canReceive() && !b.battery.canReceive()) { "transition cleanup" }
            check(a.tank.insert(water, 1, true) == 0L && b.tank.insert(water, 1, true) == 0L) { "fluid cleanup" }
            settle(a, b)
            check(!a.isLocked && !b.isLocked) { "half cannot lock at distance 3" }
            level.removeBlock(b.blockPos, false)
            b = place(level, origin.east(2), Direction.WEST, 15)
            settle(a, b)
            check(a.isLocked && b.isLocked) { "half/full re-approach" }
            signal(level, a, 5); a.tick()
            check(!a.hasOtherConnector() && !b.hasOtherConnector()) { "OFF cleanup" }
            settle(a, b)
            check(a.extension.value == 0f) { "OFF retracts completely" }

            // Real NBT restoration recreates neither transfer nor forced constraints prematurely.
            signal(level, a, 6); settle(a, b)
            val savedA = a.saveWithFullMetadata(level.registryAccess())
            val savedB = b.saveWithFullMetadata(level.registryAccess())
            val positionA = a.blockPos; val positionB = b.blockPos
            val blockA = a.blockState; val blockB = b.blockState
            level.removeBlockEntity(positionA); level.removeBlockEntity(positionB)
            a = BlockEntity.loadStatic(positionA, blockA, savedA, level.registryAccess()) as Dock
            b = BlockEntity.loadStatic(positionB, blockB, savedB, level.registryAccess()) as Dock
            level.setBlockEntity(a); level.setBlockEntity(b)
            check(AnalogDocking.access(a).requestedLength == DockingLength.HALF && AnalogDocking.access(a).effectiveLength == DockingLength.HALF) { "NBT length round trip" }
            settle(a, b)
            check(a.isLocked && b.isLocked) { "NBT reconnect: A=${a.saveWithFullMetadata(level.registryAccess())} B=${b.saveWithFullMetadata(level.registryAccess())} removed=${a.isRemoved}/${b.isRemoved} select=${AnalogDocking.select(a)?.blockPos}/${AnalogDocking.select(b)?.blockPos} signal=${level.getBestNeighborSignal(a.blockPos)}/${level.getBestNeighborSignal(b.blockPos)}" }

            // Additional geometrically invalid candidates do not steal the chosen partner.
            val wrong = place(level, origin.north(3), Direction.NORTH, 15)
            settle(a, b); wrong.tick()
            check(a.otherConnector === b && !wrong.hasOtherConnector()) { "multiple candidates" }
            level.removeBlock(b.blockPos, false); a.tick()
            check(!a.hasOtherConnector() && !a.battery.canReceive()) { "removed peer" }

            checks += verifyConstraints(level)

            // Config false: signal 1 once again produces the original full extension.
            ServerConfig.ENABLE_ANALOG_DOCKING.set(false)
            clear(level)
            a = place(level, origin, Direction.EAST, 1)
            b = place(level, origin.east(3), Direction.WEST, 1)
            settle(a, b)
            check(a.isLocked && b.isLocked && a.getExtensionDistance(1f) == 1f) { "original mode" }
            checks++
            return checks
        } finally {
            clear(level)
            ServerConfig.ENABLE_ANALOG_DOCKING.set(configured)
        }
    }
}
