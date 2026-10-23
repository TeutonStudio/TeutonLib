package de.TeutonStudio.TeutonLib.integration.peripheral

import dan200.computercraft.api.ComputerCraftAPI
import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.lua.ILuaAPIFactory
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.api.computer.peripheral.PeripheralEndpoint
import de.TeutonStudio.TeutonLib.api.computer.peripheral.PeripheralEndpointMetadata
import de.TeutonStudio.TeutonLib.api.computer.peripheral.PeripheralProviders
import java.util.function.Consumer
import java.util.function.Supplier
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.entity.BlockEntityType.BlockEntitySupplier
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModList
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.registries.DeferredRegister

/** Loaded only after the optional dependencies have been checked.  */
object PeripheralLinks {
    private val BLOCKS: DeferredRegister<Block> =
        DeferredRegister.create<Block>(Registries.BLOCK, TeutonLib.Companion.MOD_ID)
    private val ITEMS: DeferredRegister<Item> =
        DeferredRegister.create<Item>(Registries.ITEM, TeutonLib.Companion.MOD_ID)
    private val ENTITIES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create<BlockEntityType<*>>(Registries.BLOCK_ENTITY_TYPE, TeutonLib.Companion.MOD_ID)
    private val MENUS: DeferredRegister<MenuType<*>> =
        DeferredRegister.create<MenuType<*>>(Registries.MENU, TeutonLib.Companion.MOD_ID)
    private val CC_TAB = ResourceKey.create(
        Registries.CREATIVE_MODE_TAB,
        ResourceLocation.fromNamespaceAndPath("computercraft", "tab")
    )
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
    @JvmField
    val MENU = MENUS.register<MenuType<PeripheralLinkMenu>>(
        "peripheral_link",
        Supplier {
            IMenuTypeExtension.create { containerId, inventory, data ->
                PeripheralLinkMenu(containerId, inventory, data.readBlockPos())
            }
        }
    )

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
        MENUS.register(bus)
        bus.addListener<RegisterPayloadHandlersEvent>(Consumer(PeripheralLinkPayloads::register))
        bus.addListener<FMLCommonSetupEvent>(Consumer { event: FMLCommonSetupEvent? ->
            event!!.enqueueWork(Runnable {
                PeripheralProviders.registerEndpoints { context ->
                    val host = context.host() ?: return@registerEndpoints emptyList()
                    LinkRegistry.validate()
                    LinkRegistry.connections(host).map { connection ->
                        val peripheral = connection.peripheral
                        val types = linkedSetOf(peripheral.type).apply { addAll(peripheral.additionalTypes) }
                        PeripheralEndpoint(
                            address = connection.name(),
                            primaryType = peripheral.type,
                            types = types,
                            peripheral = peripheral,
                            ownerKey = host.blockPos.asLong().toString(),
                            descriptor = connection,
                            metadata = LinkLuaApi.describe(connection) + mapOf(
                                PeripheralEndpointMetadata.LINK to true,
                                PeripheralEndpointMetadata.ANCHOR_POSITION to connection.anchor.blockPos.immutable(),
                                PeripheralEndpointMetadata.DEVICE_POSITION to connection.link.devicePosition().immutable(),
                                PeripheralEndpointMetadata.SIDE to connection.link.blockState.getValue(DirectionalBlock.FACING).opposite
                            )
                        )
                    }
                }
                ComputerCraftAPI.registerAPIFactory(
                    ILuaAPIFactory { system: IComputerSystem -> LinkLuaApi(system) })
            })
        })
        bus.addListener<BuildCreativeModeTabContentsEvent>(Consumer { event: BuildCreativeModeTabContentsEvent? ->
            if (event!!.tabKey != CC_TAB) return@Consumer
            val modem = ItemStack(dan200.computercraft.shared.ModRegistry.Items.WIRELESS_MODEM_ADVANCED.get())
            val entry = ItemStack(ITEM.get())
            if (event.parentEntries.contains(modem) && event.searchEntries.contains(modem)) {
                event.insertAfter(modem, entry, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS)
            } else event.accept(entry)
        })
        NeoForge.EVENT_BUS.register(LinkRegistry::class.java)
    }

}
