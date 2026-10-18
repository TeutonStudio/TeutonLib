package de.TeutonStudio.TeutonLib.integration.peripheral

import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.shared.platform.ComponentAccess
import dan200.computercraft.shared.platform.PlatformHelper
import java.util.UUID
import java.util.function.Consumer
import kotlin.concurrent.Volatile
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class PeripheralLinkBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(PeripheralLinks.ENTITY.get(), pos, state) {
    private var identity: UUID = UUID.randomUUID()
    private var target: UUID? = null
    private var access: ComponentAccess<IPeripheral>? = null
    private var cachedPeripheral: IPeripheral? = null
    private var peripheralDirty = true

    @Volatile
    var connection: LinkRegistry.Connection? = null

    @Volatile
    var status: String = "target_unavailable"

    fun identity(): UUID {
        return identity
    }

    fun target(): UUID? {
        return target
    }

    fun target(target: UUID?) {
        this.target = target
        setChanged()
    }

    override fun setLevel(level: Level) {
        super.setLevel(level)
        access = null
        invalidatePeripheral()
        if (!level.isClientSide) LinkRegistry.register(this)
    }

    override fun setBlockState(state: BlockState) {
        super.setBlockState(state)
        invalidatePeripheral()
    }

    fun invalidatePeripheral() {
        peripheralDirty = true
        cachedPeripheral = null
        connection = null
    }

    fun devicePosition(): BlockPos {
        return worldPosition.relative(getBlockState().getValue<Direction>(DirectionalBlock.FACING).opposite)
    }

    fun peripheral(): IPeripheral? {
        if (access == null) access =
            PlatformHelper.get().createPeripheralAccess(this, Consumer { side: Direction? -> invalidatePeripheral() })
        if (peripheralDirty) {
            cachedPeripheral =
                access!!.get(getBlockState().getValue<Direction>(DirectionalBlock.FACING).opposite)
            peripheralDirty = false
        }
        return cachedPeripheral
    }

    override fun onLoad() {
        super.onLoad()
        if (level != null && !level!!.isClientSide) LinkRegistry.register(this)
    }

    override fun setRemoved() {
        LinkRegistry.remove(this)
        super.setRemoved()
    }

    override fun saveAdditional(tag: CompoundTag, provider: HolderLookup.Provider) {
        super.saveAdditional(tag, provider)
        tag.putUUID("LinkId", identity)
        if (target != null) tag.putUUID("LinkTarget", target)
    }

    override fun loadAdditional(tag: CompoundTag, provider: HolderLookup.Provider) {
        super.loadAdditional(tag, provider)
        identity = if (tag.hasUUID("LinkId")) tag.getUUID("LinkId") else UUID.randomUUID()
        target = if (tag.hasUUID("LinkTarget")) tag.getUUID("LinkTarget") else null
    }
}
