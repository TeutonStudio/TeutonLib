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
import net.minecraft.network.chat.Component
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class PeripheralLinkBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(PeripheralLinks.ENTITY.get(), pos, state), MenuProvider {
    private var identity: UUID = UUID.randomUUID()
    private var target: UUID? = null
    private var nickname = ""
    private var nicknameInitialized = false
    private var knownTargetPosition: BlockPos? = null
    private var knownTargetBlock = ""
    private var knownTargetDimension = ""
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
        if (this.target != target) clearKnownTarget()
        this.target = target
        setChanged()
    }

    fun rememberTarget(anchor: BlockEntity) {
        val anchorLevel = anchor.level ?: return
        val nextPosition = anchor.blockPos.immutable()
        val nextBlock = BuiltInRegistries.BLOCK.getKey(anchor.blockState.block).toString()
        val nextDimension = anchorLevel.dimension().location().toString()
        if (knownTargetPosition == nextPosition && knownTargetBlock == nextBlock && knownTargetDimension == nextDimension) return
        knownTargetPosition = nextPosition
        knownTargetBlock = nextBlock
        knownTargetDimension = nextDimension
        setChanged()
    }

    fun knownTargetPosition(): BlockPos? = knownTargetPosition

    fun knownTargetBlock(): String = knownTargetBlock

    fun knownTargetDimension(): String = knownTargetDimension

    private fun clearKnownTarget() {
        knownTargetPosition = null
        knownTargetBlock = ""
        knownTargetDimension = ""
    }

    fun nickname(): String = nickname

    fun nicknameInitialized(): Boolean = nicknameInitialized

    fun nickname(rawNickname: String) {
        val next = validateNickname(rawNickname)
        if (nickname == next && nicknameInitialized) return
        nickname = next
        nicknameInitialized = true
        setChanged()
    }

    /** Imports the old computer-local nickname once, before this link owns its name. */
    fun initializeNickname(rawNickname: String?): Boolean {
        if (nicknameInitialized) return false
        nickname = validateNickname(rawNickname.orEmpty())
        nicknameInitialized = true
        setChanged()
        return true
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
        if (nicknameInitialized) tag.putBoolean("NicknameInitialized", true)
        if (nickname.isNotEmpty()) tag.putString("Nickname", nickname)
        knownTargetPosition?.let { tag.putLong("KnownTargetPosition", it.asLong()) }
        if (knownTargetBlock.isNotEmpty()) tag.putString("KnownTargetBlock", knownTargetBlock)
        if (knownTargetDimension.isNotEmpty()) tag.putString("KnownTargetDimension", knownTargetDimension)
    }

    override fun loadAdditional(tag: CompoundTag, provider: HolderLookup.Provider) {
        super.loadAdditional(tag, provider)
        identity = if (tag.hasUUID("LinkId")) tag.getUUID("LinkId") else UUID.randomUUID()
        target = if (tag.hasUUID("LinkTarget")) tag.getUUID("LinkTarget") else null
        nicknameInitialized = tag.getBoolean("NicknameInitialized") || tag.contains("Nickname")
        nickname = try {
            validateNickname(tag.getString("Nickname"))
        } catch (_: IllegalArgumentException) {
            ""
        }
        knownTargetPosition = if (tag.contains("KnownTargetPosition")) BlockPos.of(tag.getLong("KnownTargetPosition")) else null
        knownTargetBlock = tag.getString("KnownTargetBlock")
        knownTargetDimension = tag.getString("KnownTargetDimension")
    }

    override fun getDisplayName(): Component = Component.translatable("block.teutonlib.peripheral_link")

    override fun createMenu(containerId: Int, inventory: Inventory, player: Player): AbstractContainerMenu =
        PeripheralLinkMenu(containerId, inventory, this)

    companion object {
        @JvmStatic
        fun validateNickname(rawNickname: String): String {
            val value = rawNickname.trim()
            require(value.length <= 32) { "Nickname is too long" }
            require(value.none(Char::isISOControl)) { "Nickname contains control characters" }
            return value
        }
    }
}
