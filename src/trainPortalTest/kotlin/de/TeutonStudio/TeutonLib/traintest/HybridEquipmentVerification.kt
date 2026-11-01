package de.TeutonStudio.TeutonLib.traintest

import com.simibubi.create.AllBlocks
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity
import com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity
import createtrainfunctionality.SubLevelLinks
import de.TeutonStudio.TeutonLib.integration.hose.*
import de.TeutonStudio.TeutonLib.integration.hybrid.HybridTrainRuntime
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer
import dev.ryanhcode.sable.sublevel.ServerSubLevel
import dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity
import dev.simulated_team.simulated.index.SimBlocks
import net.createmod.catnip.animation.LerpedFloat
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity
import rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity
import rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlock
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption
import rbasamoyai.createbigcannons.cannons.autocannon.IAutocannonBlockEntity
import rbasamoyai.createbigcannons.cannons.autocannon.breech.AbstractAutocannonBreechBlockEntity
import rbasamoyai.createbigcannons.cannons.big_cannons.IBigCannonBlockEntity
import rbasamoyai.createbigcannons.index.CBCBlocks
import rbasamoyai.createbigcannons.index.CBCItems
import rbasamoyai.createbigcannons.munitions.autocannon.AutocannonCartridgeItem
import java.util.UUID
import java.util.Properties

/** Actual controllers and projectile entities, not substitutes for the supported mods. */
internal object HybridEquipmentVerification {
    private val mechanical = BlockPos(-5, 6, 0)
    private val swivel = BlockPos(-8, 6, 0)
    private val cannon = BlockPos(9, 6, 0)
    private val autocannon = BlockPos(-2, 6, 2)
    private val hose = BlockPos(1, 6, 0)
    private var headId: UUID? = null
    private var shots = 0

    fun setup(level: ServerLevel, origin: BlockPos) {
        fun put(pos: BlockPos, state: BlockState) { check(level.getBlockState(origin.offset(pos)) == state || level.setBlockAndUpdate(origin.offset(pos), state)) }
        for (pos in listOf(mechanical, swivel, cannon, autocannon)) {
            for (y in 2 until pos.y) put(BlockPos(pos.x, y, pos.z), Blocks.STONE.defaultBlockState())
        }
        for (x in -2..1) put(BlockPos(x, 4, 0), Blocks.STONE.defaultBlockState())
        // Bridge the fixed mount's support column to the deck.
        put(BlockPos(-2, 2, 1), Blocks.STONE.defaultBlockState())
        put(mechanical.below(), AllBlocks.CREATIVE_MOTOR.defaultState.setValue(BlockStateProperties.FACING, Direction.UP))
        put(mechanical, AllBlocks.MECHANICAL_BEARING.defaultState.setValue(BlockStateProperties.FACING, Direction.UP))
        put(mechanical.above(), Blocks.GLASS.defaultBlockState())
        put(swivel, SimBlocks.SWIVEL_BEARING.defaultState.setValue(BlockStateProperties.FACING, Direction.UP))
        put(swivel.above(), Blocks.CHEST.defaultBlockState())
        (level.getBlockEntity(origin.offset(swivel).above()) as ChestBlockEntity).setItem(0, ItemStack(Items.EMERALD, 11))
        val cog = swivel.east()
        for (y in 3..4) put(BlockPos(cog.x, y, cog.z), Blocks.STONE.defaultBlockState())
        put(cog.below(), AllBlocks.CREATIVE_MOTOR.defaultState.setValue(BlockStateProperties.FACING, Direction.UP))
        put(cog, AllBlocks.COGWHEEL.defaultState.setValue(BlockStateProperties.AXIS, Direction.Axis.Y))

        put(cannon, CBCBlocks.CANNON_MOUNT.defaultState
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
            .setValue(BlockStateProperties.VERTICAL_DIRECTION, Direction.DOWN))
        put(cannon.above(2), CBCBlocks.CAST_IRON_SLIDING_BREECH.defaultState.setValue(BlockStateProperties.FACING, Direction.EAST))
        put(cannon.above(2).east(), CBCBlocks.CAST_IRON_CANNON_CHAMBER.defaultState.setValue(BlockStateProperties.FACING, Direction.EAST))
        put(cannon.above(2).east(2), CBCBlocks.CAST_IRON_CANNON_BARREL.defaultState.setValue(BlockStateProperties.FACING, Direction.EAST))
        for (i in 0..2) {
            val be = level.getBlockEntity(origin.offset(cannon.above(2).east(i))) as IBigCannonBlockEntity
            if (i > 0) be.cannonBehavior().setConnectedFace(Direction.WEST, true)
            if (i < 2) be.cannonBehavior().setConnectedFace(Direction.EAST, true)
        }
        put(autocannon, CBCBlocks.FIXED_CANNON_MOUNT.defaultState.setValue(BlockStateProperties.FACING, Direction.EAST))
        put(autocannon.east(), CBCBlocks.CAST_IRON_AUTOCANNON_BREECH.defaultState.setValue(BlockStateProperties.FACING, Direction.EAST))
        put(autocannon.east(2), CBCBlocks.CAST_IRON_AUTOCANNON_BARREL.defaultState.setValue(BlockStateProperties.FACING, Direction.EAST))
        (level.getBlockEntity(origin.offset(autocannon.east())) as IAutocannonBlockEntity).cannonBehavior().setConnectedFace(Direction.EAST, true)
        (level.getBlockEntity(origin.offset(autocannon.east(2))) as IAutocannonBlockEntity).cannonBehavior().setConnectedFace(Direction.WEST, true)
        put(cannon.west(), Blocks.REDSTONE_BLOCK.defaultBlockState())
        val fixedState = level.getBlockState(origin.offset(autocannon))
        put(autocannon.relative((fixedState.block as FixedCannonMountBlock).getAssemblyFace(fixedState)), Blocks.REDSTONE_BLOCK.defaultBlockState())
        // A side-mounted hose hangs clear of the deck and has a real, persistent world entity.
        put(BlockPos(1, 5, 0), Blocks.STONE.defaultBlockState())
        put(hose, AllBlocks.HOSE_PULLEY.defaultState)
        val pulley = level.getBlockEntity(origin.offset(hose)) as HosePulleyBlockEntity
        val offset = HosePulleyBlockEntity::class.java.getDeclaredField("offset").also { it.isAccessible = true }.get(pulley) as LerpedFloat
        offset.startWithValue(2.0)
        HosePulleyBridge.tick(pulley, 2f)
        headId = (pulley as HosePulleyHost).physicalHeadId
        check(headId != null)
        shots = 0
    }

    fun start(level: ServerLevel, origin: BlockPos) {
        (level.getBlockEntity(origin.offset(mechanical)) as MechanicalBearingBlockEntity).assemble()
        (level.getBlockEntity(origin.offset(swivel)) as SwivelBearingBlockEntity).let {
            it.assemble()
            it.assembleNextTick = false
        }
        val mount = level.getBlockEntity(origin.offset(cannon)) as CannonMountBlockEntity
        mount.onRedstoneUpdate(true, false, false, false, 0)
        check(mount.contraption?.contraption is MountedBigCannonContraption) { "Big Cannon assembly failed: ${mount.lastAssemblyException?.message}" }
        mount.setYaw(92f); mount.setPitch(10f)
        val big = mount.contraption!!.contraption as MountedBigCannonContraption
        for ((pos, info) in big.blocks) {
            val ammo = when {
                info.state().`is`(CBCBlocks.CAST_IRON_CANNON_CHAMBER.get()) -> CBCBlocks.POWDER_CHARGE.defaultState.setValue(BlockStateProperties.AXIS, Direction.Axis.X)
                info.state().`is`(CBCBlocks.CAST_IRON_CANNON_BARREL.get()) -> CBCBlocks.SOLID_SHOT.defaultState.setValue(BlockStateProperties.FACING, Direction.EAST)
                else -> continue
            }
            check((big.presentBlockEntities[pos] as IBigCannonBlockEntity).cannonBehavior().tryLoadingBlock(StructureBlockInfo(pos, ammo, null)))
        }
        val fixed = level.getBlockEntity(origin.offset(autocannon)) as FixedCannonMountBlockEntity
        fixed.onRedstoneUpdate(true, false, false, false, 0)
        check(fixed.contraption?.contraption is MountedAutocannonContraption) { "Autocannon assembly failed: ${fixed.lastAssemblyException?.message}" }
        val auto = fixed.contraption!!.contraption as MountedAutocannonContraption
        val breech = auto.presentBlockEntities.values.filterIsInstance<AbstractAutocannonBreechBlockEntity>().single()
        repeat(3) {
            val cartridge = CBCItems.AUTOCANNON_CARTRIDGE.asStack()
            AutocannonCartridgeItem.writeProjectile(CBCItems.AP_AUTOCANNON_ROUND.asStack(), cartridge)
            breech.inputBuffer.add(cartridge)
        }
        breech.setFireRate(0)
        check((level.getBlockEntity(origin.offset(mechanical)) as MechanicalBearingBlockEntity).movedContraption?.isAlive == true)
        val bearing = level.getBlockEntity(origin.offset(swivel)) as SwivelBearingBlockEntity
        check(bearing.isAssembled && bearing.subLevelID != null) { "Swivel assembly failed: ${bearing.lastAssemblyException?.message}" }
    }

    fun save(values: Properties) {
        values["equipmentHead"] = headId.toString()
        values["equipmentShots"] = shots.toString()
    }

    fun load(values: Properties) {
        headId = UUID.fromString(values.getProperty("equipmentHead"))
        shots = values.getProperty("equipmentShots").toInt()
    }

    fun verify(level: ServerLevel, origin: BlockPos, fire: Boolean = false) {
        val bearing = level.getBlockEntity(origin.offset(mechanical)) as MechanicalBearingBlockEntity
        check(bearing.isRunning && bearing.movedContraption?.isAlive == true)
        check(kotlin.math.abs(bearing.getInterpolatedAngle(1f)) > .01f) { "Mechanical payload is not rotating" }
        check(bearing.movedContraption!!.contraption.blocks.values.any { it.state().`is`(Blocks.GLASS) })
        val sbe = level.getBlockEntity(origin.offset(swivel)) as SwivelBearingBlockEntity
        check(sbe.isAssembled && sbe.subLevelID != null) { "Swivel did not resume" }
        check(kotlin.math.abs(sbe.targetAngleDegrees) > .01) { "Swivel payload is not rotating: speed=${sbe.extraKinetics.speed}" }
        val child = SubLevelContainer.getContainer(level)!!.getSubLevel(sbe.subLevelID) as ServerSubLevel
        check(HybridTrainRuntime.owningRoot(child)?.uniqueId == HybridTrainRuntime.owningRoot(
            dev.ryanhcode.sable.Sable.HELPER.getContaining(level, origin) as ServerSubLevel)?.uniqueId)
        val chest = child.plot.loadedChunks.flatMap { it.chunk.blockEntities.values }.filterIsInstance<ChestBlockEntity>().single()
        check(chest.getItem(0).`is`(Items.EMERALD) && chest.getItem(0).count == 11) { "Swivel lost its payload inventory" }
        check(!level.destroyBlock(chest.blockPos, true)) { "Swivel payload bypassed the driving lock" }
        val mount = level.getBlockEntity(origin.offset(cannon)) as CannonMountBlockEntity
        val big = mount.contraption?.contraption as? MountedBigCannonContraption
        check(big != null && mount.contraption!!.isAlive && big.blocks.size == 3) { "Big Cannon did not resume with all blocks" }
        val loadedAmmo = big.presentBlockEntities.values.filterIsInstance<IBigCannonBlockEntity>().count { !it.cannonBehavior().block().state().isAir }
        check(loadedAmmo == if (shots < 2) 2 else 0) { "Big Cannon lost or duplicated its loaded charge/projectile: $loadedAmmo" }
        val fixed = level.getBlockEntity(origin.offset(autocannon)) as FixedCannonMountBlockEntity
        val auto = fixed.contraption?.contraption as? MountedAutocannonContraption
        check(auto != null && fixed.contraption!!.isAlive) { "Autocannon did not resume" }
        val breech = auto.presentBlockEntities.values.filterIsInstance<AbstractAutocannonBreechBlockEntity>().single()
        check(breech.inputBuffer.sumOf { it.count } == 3 - shots) { "Autocannon ammunition changed across transfer: ${breech.inputBuffer}" }
        if (fire) {
            if (shots == 1) {
                val beforeBig = level.allEntities.count { it is rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile }
                big.fireShot(level, mount.contraption!!)
                check(level.allEntities.count { it is rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile } == beforeBig + 1) { "Loaded Big Cannon did not fire after transfer" }
            }
            val oldItems = level.allEntities.filterIsInstance<net.minecraft.world.entity.item.ItemEntity>().mapTo(hashSetOf()) { it.uuid }
            breech.setFireRate(15)
            val before = level.allEntities.count { it.type.builtInRegistryHolder().key().location().namespace == "createbigcannons" }
            auto.fireShot(level, fixed.contraption!!)
            shots++
            breech.setFireRate(0)
            check(level.allEntities.count { it.type.builtInRegistryHolder().key().location().namespace == "createbigcannons" } > before) {
                "CBC did not produce a real projectile"
            }
            val ejected = level.allEntities.filterIsInstance<net.minecraft.world.entity.item.ItemEntity>().filter { it.uuid !in oldItems }
            check(ejected.size == 1 && ejected.single().item.`is`(CBCItems.EMPTY_AUTOCANNON_CARTRIDGE.get())) { "Unexpected firing drops: $ejected" }
            ejected.single().discard() // Keep the separate portal test's zero-block-drop assertion strict.
        }
        val pulley = level.getBlockEntity(origin.offset(hose)) as HosePulleyBlockEntity
        val host = pulley as HosePulleyHost
        check(host.physicalHeadId == headId) { "Hose head identity changed" }
        val head = level.getEntity(checkNotNull(headId)) as? HoseHeadEntity
        check(head != null && head.isAlive && head.belongsTo(pulley.blockPos) && head.validOwner() === pulley) { "Hose head did not follow its pulley" }
        val handler = checkNotNull(level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pulley.blockPos, null))
        val endpoint = head.blockPosition()
        val saved = BlockPos.betweenClosed(endpoint.offset(-1, -1, -1), endpoint.offset(1, 1, 1))
            .associate { it.immutable() to level.getBlockState(it) }
        try {
            for (pos in saved.keys) level.setBlock(pos, when (pos) {
                endpoint -> Blocks.LAVA.defaultBlockState()
                endpoint.above() -> Blocks.AIR.defaultBlockState()
                else -> Blocks.STONE.defaultBlockState()
            }, 2)
            var simulated = net.neoforged.neoforge.fluids.FluidStack.EMPTY
            repeat(800) {
                if (simulated.amount != 1000) {
                    simulated = handler.drain(1000, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE)
                    pulley.tick()
                }
            }
            check(simulated.amount == 1000 && level.getFluidState(endpoint).isSource) { "Mounted hose failed native fluid simulation after transfer" }
            val drained = handler.drain(1000, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)
            check(drained.amount == 1000 && level.getFluidState(endpoint).isEmpty) { "Mounted hose failed native fluid extraction at its real head" }
        } finally {
            saved.forEach { (pos, state) -> level.setBlock(pos, state, 2) }
        }
    }
}
