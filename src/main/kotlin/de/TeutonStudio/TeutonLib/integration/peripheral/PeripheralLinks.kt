package de.TeutonStudio.TeutonLib.integration.peripheral

import dan200.computercraft.api.ComputerCraftAPI
import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.lua.ILuaAPIFactory
import de.TeutonStudio.TeutonLib.TeutonLib
import java.util.function.Consumer
import java.util.function.Supplier
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModList
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.registries.DeferredRegister

/** Loaded only after the optional dependencies have been checked.  */
object PeripheralLinks {
    private val BLOCKS: DeferredRegister<Block> =
        DeferredRegister.create<Block>(Registries.BLOCK, TeutonLib.Companion.MOD_ID)
    private val ITEMS: DeferredRegister<Item> =
        DeferredRegister.create<Item>(Registries.ITEM, TeutonLib.Companion.MOD_ID)
    private val ENTITIES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create<BlockEntityType<*>>(Registries.BLOCK_ENTITY_TYPE, TeutonLib.Companion.MOD_ID)
    @JvmField
    val BLOCK: Supplier<PeripheralLinkBlock> = BLOCKS.register<PeripheralLinkBlock>(
        "peripheral_link",
        Supplier {
            PeripheralLinkBlock(
                BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(1.5f).noOcclusion()
            )
        })
    @JvmField
    val ITEM: Supplier<BlockItem> = ITEMS.register<BlockItem>(
        "peripheral_link",
        Supplier { PeripheralLinkItem(BLOCK.get(), Item.Properties()) })
    @JvmField
    val ENTITY: Supplier<BlockEntityType<PeripheralLinkBlockEntity>> =
        ENTITIES.register<BlockEntityType<PeripheralLinkBlockEntity>>(
            "peripheral_link",
            Supplier {
                BlockEntityType.Builder.of<PeripheralLinkBlockEntity>(BlockEntitySupplier { pos: BlockPos, state: BlockState ->
                    PeripheralLinkBlockEntity(
                        pos,
                        state
                    )
                }, BLOCK.get()).build(null)
            })

    @JvmStatic
    fun available(): Boolean {
        return ModList.get().isLoaded("computercraft")
                && ModList.get().isLoaded("create")
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        BLOCKS.register(bus)
        ITEMS.register(bus)
        ENTITIES.register(bus)
        bus.addListener<FMLCommonSetupEvent>(Consumer { event: FMLCommonSetupEvent? ->
            event!!.enqueueWork(Runnable {
                ComputerCraftAPI.registerAPIFactory(
                    ILuaAPIFactory { system: IComputerSystem -> LinkLuaApi(system) })
            })
        })
        bus.addListener<BuildCreativeModeTabContentsEvent>(Consumer { event: BuildCreativeModeTabContentsEvent? ->
            if (event!!.tabKey === CreativeModeTabs.REDSTONE_BLOCKS) event.accept(ITEM.get())
        })
        NeoForge.EVENT_BUS.register(LinkRegistry::class.java)
    }
}
