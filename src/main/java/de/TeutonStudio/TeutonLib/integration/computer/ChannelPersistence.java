package de.TeutonStudio.TeutonLib.integration.computer;
import net.minecraft.nbt.*;
public final class ChannelPersistence {
    public static ListTag save(ChannelBank bank) {
        ListTag list = new ListTag();
        for (var e : bank.entries()) {
            CompoundTag t = new CompoundTag(); t.putString("name", e.name()); t.putString("direction", e.direction());
            t.putString("side", e.side()); t.putBoolean("public", e.published()); t.putInt("value", e.value()); list.add(t);
        }
        return list;
    }
    public static ChannelBank load(ListTag list) {
        ChannelBank bank = new ChannelBank();
        for (int i = 0; i < Math.min(list.size(), ChannelBank.LIMIT); i++) {
            CompoundTag t = list.getCompound(i);
            try { bank.restore(new ChannelBank.Entry(t.getString("name"), t.getString("direction"), t.getString("side"), t.getBoolean("public"), t.getInt("value"))); }
            catch (IllegalArgumentException ignored) { /* Ignore invalid definitions from copied/older NBT. */ }
        }
        return bank;
    }
    private ChannelPersistence() {}
}
