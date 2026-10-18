package de.TeutonStudio.TeutonLib.integration.peripheral;

import de.TeutonStudio.TeutonLib.TeutonLib;
import dan200.computercraft.api.ComputerCraftAPI;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

/** Loaded only after the optional dependencies have been checked. */
public final class PeripheralLinks {
    private static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, TeutonLib.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, TeutonLib.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TeutonLib.MOD_ID);
    public static final Supplier<PeripheralLinkBlock> BLOCK = BLOCKS.register("peripheral_link",
            () -> new PeripheralLinkBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(1.5f).noOcclusion()));
    public static final Supplier<BlockItem> ITEM = ITEMS.register("peripheral_link",
            () -> new PeripheralLinkItem(BLOCK.get(), new Item.Properties()));
    public static final Supplier<BlockEntityType<PeripheralLinkBlockEntity>> ENTITY = ENTITIES.register("peripheral_link",
            () -> BlockEntityType.Builder.of(PeripheralLinkBlockEntity::new, BLOCK.get()).build(null));

    public static boolean available() {
        return ModList.get().isLoaded("computercraft")
                && ModList.get().isLoaded("create");
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() ->
                ComputerCraftAPI.registerAPIFactory(LinkLuaApi::new)));
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.REDSTONE_BLOCKS) event.accept(ITEM.get());
        });
        NeoForge.EVENT_BUS.register(LinkRegistry.class);
    }
    private PeripheralLinks() {}
}
