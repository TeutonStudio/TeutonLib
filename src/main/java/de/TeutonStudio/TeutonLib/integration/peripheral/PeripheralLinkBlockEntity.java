package de.TeutonStudio.TeutonLib.integration.peripheral;

import java.util.UUID;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.platform.ComponentAccess;
import dan200.computercraft.shared.platform.PlatformHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PeripheralLinkBlockEntity extends BlockEntity {
    private UUID identity = UUID.randomUUID();
    private UUID target;
    private ComponentAccess<IPeripheral> access;
    private IPeripheral cachedPeripheral;
    private boolean peripheralDirty = true;
    public volatile LinkRegistry.Connection connection;
    public volatile String status = "target_unavailable";

    public PeripheralLinkBlockEntity(BlockPos pos, BlockState state) { super(PeripheralLinks.ENTITY.get(), pos, state); }
    public UUID identity() { return identity; }
    public UUID target() { return target; }
    public void target(UUID target) { this.target = target; setChanged(); }
    @Override public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        access = null; invalidatePeripheral();
        if (!level.isClientSide) LinkRegistry.register(this);
    }
    @Override public void setBlockState(BlockState state) { super.setBlockState(state); invalidatePeripheral(); }
    public void invalidatePeripheral() { peripheralDirty = true; cachedPeripheral = null; connection = null; }
    public BlockPos devicePosition() { return worldPosition.relative(getBlockState().getValue(DirectionalBlock.FACING).getOpposite()); }
    public IPeripheral peripheral() {
        if (access == null) access = PlatformHelper.get().createPeripheralAccess(this, side -> invalidatePeripheral());
        if (peripheralDirty) {
            cachedPeripheral = access.get(getBlockState().getValue(DirectionalBlock.FACING).getOpposite());
            peripheralDirty = false;
        }
        return cachedPeripheral;
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) LinkRegistry.register(this);
    }
    @Override public void setRemoved() { LinkRegistry.remove(this); super.setRemoved(); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putUUID("LinkId", identity);
        if (target != null) tag.putUUID("LinkTarget", target);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        identity = tag.hasUUID("LinkId") ? tag.getUUID("LinkId") : UUID.randomUUID();
        target = tag.hasUUID("LinkTarget") ? tag.getUUID("LinkTarget") : null;
    }
}
