package de.TeutonStudio.TeutonLib.client.computer;

import dan200.computercraft.shared.computer.core.ComputerFamily;
import de.TeutonStudio.TeutonLib.integration.computer.ComputerPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import java.util.function.Consumer;

/** A monitor workspace shared by ordinary computers and addon menu hosts. */
public final class ComputerPanels {
    private static final int BACKGROUND = 0xff111111, LINE = 0xff2b2b2b, TEXT = 0xffe0e0e0, MUTED = 0xff888888;
    private final int menu;
    private final UUID session = UUID.randomUUID();
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final Font font;
    private final Rows rows;
    private final EditBox name;
    private final FlatButton direction, side, visibility, primary, remove, minus, plus, newChannel;
    private final int editorX, editorWidth, nameY, step, actionY;
    private final Runnable focusTerminal;
    private ComputerFamily family = ComputerFamily.NORMAL;
    private int page, inputSide;
    private boolean input, received, focusNameRequested;
    private long lastRequest = Long.MIN_VALUE;
    private CompoundTag snapshot = new CompoundTag();
    private String selected = "", selectedSource = "", armed = "", pendingSelection = "";

    public ComputerPanels(int menu, int x, int y, int width, int height, Font font,
                          Consumer<AbstractWidget> add, Runnable focusTerminal) {
        this.menu = menu; this.font = font; this.focusTerminal = focusTerminal;
        editorWidth = Math.clamp(width / 2 - 12, 110, 150);
        editorX = x + width - editorWidth - 7;
        nameY = y + 38; step = Math.clamp((height - 58) / 5, 16, 21); actionY = y + height - 34;
        rows = new Rows(x, y, width, height); widgets.add(rows);
        name = new EditBox(font, editorX + 3, nameY, editorWidth - 6, 16, tr("name"));
        name.setMaxLength(32); name.setBordered(false); name.setTextColor(TEXT); name.setTextColorUneditable(MUTED);
        name.setHint(tr("name")); widgets.add(name);
        direction = button(editorX, nameY + step, editorWidth, "output", () -> { input = !input; refreshControls(); });
        side = button(editorX + editorWidth / 2 + 2, nameY + step, editorWidth / 2 - 2, "front",
            () -> { inputSide = (inputSide + 1) % 6; refreshControls(); });
        visibility = button(editorX, nameY + step * 2, editorWidth, "private", () -> {
            var entry = selectedEntry(); if (entry != null) send("public", selected, "", 0, !entry.getBoolean("public"));
        });
        minus = button(editorX, nameY + step * 3, 22, "minus", () -> adjust(-1)); minus.setMessage(Component.literal("−"));
        plus = button(editorX + editorWidth - 22, nameY + step * 3, 22, "plus", () -> adjust(1)); plus.setMessage(Component.literal("+"));
        primary = button(editorX, actionY, editorWidth, "create", () -> {
            if (selectedEntry() == null) send("create", name.getValue().trim(), input ? "input" : "output", inputSide, false);
            else send("rename", selected, name.getValue().trim(), 0, false);
        });
        remove = button(editorX + editorWidth - 20, actionY, 20, "remove", () -> {
            if (selected.equals(armed)) { send("remove", selected, "", 0, false); armed = ""; }
            else armed = selected;
            refreshControls();
        }); remove.setMessage(Component.literal("×"));
        newChannel = button(x + width - 21, y + 3, 17, "new_channel", this::beginNewChannel);
        newChannel.setMessage(Component.literal("+"));
        name.setResponder(value -> refreshControls());
        widgets.forEach(add); show(0);
    }

    public void family(ComputerFamily family) { this.family = family; }
    private int accent() { return family == ComputerFamily.ADVANCED ? 0xffffcc55 : 0xffd0d0d0; }
    private int indicator() { return family == ComputerFamily.ADVANCED ? 0xff79bd88 : 0xffcccccc; }
    private static Component tr(String key) {
        String translation = "teutonlib.computer." + key;
        return net.minecraft.client.resources.language.I18n.exists(translation) ? Component.translatable(translation) : Component.literal(key);
    }
    private String label(String key) { return tr(key).getString(); }
    private FlatButton button(int x, int y, int width, String key, Runnable action) {
        var button = new FlatButton(x, y, width, tr(key), action); widgets.add(button); return button;
    }
    private String sideName() { return new String[]{"front", "back", "left", "right", "top", "bottom"}[inputSide]; }
    private ListTag channels() { return snapshot.getList("channels", Tag.TAG_COMPOUND); }
    private CompoundTag selectedEntry() {
        var list = channels();
        for (int i = 0; i < list.size(); i++) if (list.getCompound(i).getString("name").equals(selected)) return list.getCompound(i);
        return null;
    }
    public void beginNewChannel() {
        focusNameRequested = true;
        selected = ""; armed = ""; name.setValue(""); refreshControls();
    }
    private void selectChannel(CompoundTag entry) {
        selected = entry.getString("name"); armed = ""; name.setValue(selected); refreshControls();
    }
    private void adjust(int delta) {
        var entry = selectedEntry();
        if (entry != null && entry.getString("direction").equals("output"))
            send("set", selected, "", Math.clamp(entry.getInt("value") + delta, 0, 15), false);
    }
    private void refreshControls() {
        if (newChannel == null) return; // The EditBox responder may run during construction.
        var entry = selectedEntry();
        boolean enabled = page == 2, editing = entry != null, writable = !editing || entry.getBoolean("editable");
        for (var widget : widgets) { widget.visible = enabled; widget.active = enabled; }
        rows.visible = page != 0; rows.active = page != 0;
        name.active = enabled && writable;
        String kind = editing ? entry.getString("direction") : input ? "input" : "output";
        boolean hasSide = kind.equals("input");
        direction.setMessage(tr(kind)); direction.active = enabled && !editing;
        direction.setWidth(hasSide ? editorWidth / 2 - 2 : editorWidth);
        side.setMessage(tr(editing ? entry.getString("side") : sideName())); side.visible = enabled && hasSide;
        side.active = enabled && hasSide && !editing;
        visibility.setMessage(tr(editing && entry.getBoolean("public") ? "public" : "private"));
        visibility.active = enabled && editing && writable;
        primary.setMessage(tr(editing ? "rename" : "create"));
        primary.visible = enabled && writable;
        primary.active = enabled && writable && !name.getValue().trim().isEmpty() && (!editing || !name.getValue().trim().equals(selected));
        primary.setWidth(editing && writable ? editorWidth - 24 : editorWidth);
        remove.visible = enabled && editing && writable; remove.active = remove.visible;
        remove.setMessage(Component.literal(armed.equals(selected) && !armed.isEmpty() ? "!" : "×"));
        remove.setTooltip(Tooltip.create(tr(armed.equals(selected) && !armed.isEmpty() ? "confirm_remove" : "remove")));
        boolean output = kind.equals("output");
        minus.visible = enabled && output; plus.visible = enabled && output;
        minus.active = enabled && editing && writable && output && entry.getInt("value") > 0;
        plus.active = enabled && editing && writable && output && entry.getInt("value") < 15;
    }
    /** Hosts apply this after dispatching clicks/keys, so a new-channel action focuses its name field. */
    public AbstractWidget takeRequestedFocus() {
        if (!focusNameRequested || page != 2) return null;
        focusNameRequested = false; return name;
    }
    public int page() { return page; }
    public UUID session() { return session; }
    public void show(int next) {
        boolean changed = next != page; page = next; rows.setMessage(tr(next == 1 ? "sources" : "channels"));
        if (changed) {
            selected = ""; selectedSource = ""; armed = ""; pendingSelection = ""; focusNameRequested = false; rows.offset = 0; received = false; snapshot = new CompoundTag(); name.setValue("");
            if (next != 0) request(); else focusTerminal.run();
        }
        refreshControls();
    }
    public void tick() {
        if (page == 0) return;
        long tick = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        if (lastRequest == Long.MIN_VALUE || tick - lastRequest >= 20) request();
        refreshControls();
    }
    private void request() { send("snapshot", "", "", 0, false); }
    private void send(String action, String channel, String text, int value, boolean published) {
        if (action.equals("create")) pendingSelection = channel;
        else if (action.equals("rename")) pendingSelection = text;
        if (Minecraft.getInstance().getConnection() != null)
            PacketDistributor.sendToServer(new ComputerPayloads.Request(menu, session, action, channel, text, value, published));
        lastRequest = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
    }
    public void receive(ComputerPayloads.Snapshot packet) {
        if (packet.menu() != menu || !packet.session().equals(session) || packet.data() == null) return;
        snapshot = packet.data(); received = true;
        if (!snapshot.getString("error").isEmpty() || snapshot.contains("errorKey")) pendingSelection = "";
        if (!pendingSelection.isEmpty()) {
            var entries = channels();
            for (int i = 0; i < entries.size(); i++) if (entries.getCompound(i).getString("name").equals(pendingSelection)) {
                selected = pendingSelection; pendingSelection = ""; armed = ""; name.setValue(selected); break;
            }
        }
        if (!selected.isEmpty() && selectedEntry() == null) { selected = ""; armed = ""; name.setValue(""); }
        refreshControls();
    }
    public interface Owner { ComputerPanels teutonlib$panels(); }
    public static void accept(ComputerPayloads.Snapshot packet) {
        if (Minecraft.getInstance().screen instanceof Owner owner && owner.teutonlib$panels() != null) owner.teutonlib$panels().receive(packet);
    }

    /** Controls inside the monitor are flat; the physical sidebar retains CC's casing. */
    private final class FlatButton extends Button {
        FlatButton(int x, int y, int width, Component message, Runnable action) {
            super(x, y, width, 16, message, b -> action.run(), DEFAULT_NARRATION);
            setTooltip(Tooltip.create(message));
        }
        @Override public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            int color = !active ? 0xff555555 : isHoveredOrFocused() ? accent() : TEXT;
            g.fill(getX(), getY(), getX() + width, getY() + height, active && isHoveredOrFocused() ? 0xff303030 : 0xff1c1c1c);
            g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, active && isHoveredOrFocused() ? accent() : LINE);
            String text = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, width - 6));
            g.drawString(font, text, getX() + (width - font.width(text)) / 2, getY() + 4, color, false);
        }
    }

    private record Row(String section, String title, CompoundTag entry, int height) {}
    private final class Rows extends AbstractWidget {
        private int offset;
        private final Set<String> collapsed = new HashSet<>();
        Rows(int x, int y, int width, int height) { super(x, y, width, height, tr("sources")); }
        private int listWidth() { return page == 2 ? editorX - getX() - 15 : width - 12; }
        private int viewportHeight() { return height - 47; }
        private List<Row> entries() {
            var result = new ArrayList<Row>();
            var list = page == 1 ? snapshot.getList("sources", Tag.TAG_COMPOUND) : channels();
            List<String> groups = page == 1 ? List.of("local", "wired", "wireless") : List.of("output", "input", "extensions");
            var extra = new LinkedHashSet<String>();
            for (int i = 0; i < list.size(); i++) {
                var entry = list.getCompound(i); String group = group(entry);
                if (!groups.contains(group)) extra.add(group);
            }
            var all = new ArrayList<>(groups); all.addAll(extra);
            for (String group : all) {
                var members = new ArrayList<CompoundTag>();
                for (int i = 0; i < list.size(); i++) if (group(list.getCompound(i)).equals(group)) members.add(list.getCompound(i));
                if (members.isEmpty()) continue;
                result.add(new Row(group, label(page == 2 && !group.equals("extensions") ? group + "s" : group), null, 15));
                if (!collapsed.contains(page + ":" + group))
                    for (var entry : members) result.add(new Row(group, "", entry, page == 1 ? entry.getString("name").equals(selectedSource) ? 56 : 25 : 23));
            }
            return result;
        }
        private String group(CompoundTag entry) {
            if (page == 1) return entry.getString("connection");
            return entry.getBoolean("editable") ? entry.getString("direction") : "extensions";
        }
        private String displayName(CompoundTag entry) {
            String address = entry.getString("name");
            if (!address.startsWith("cc_aeroworks:link_")) return address;
            String type = entry.getString("types").split(",", 2)[0].replace('_', ' ');
            String id = address.substring("cc_aeroworks:link_".length());
            return type + " · " + label("link") + " " + id.substring(0, Math.min(8, id.length()));
        }
        private void text(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
            if (maxWidth > 0) {
                String visible = font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
                g.drawString(font, visible, x, y, color, false);
            }
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
            g.fill(getX(), getY(), getX() + width, getY() + height, BACKGROUND);
            String title = label(page == 1 ? "sources" : "channels").toUpperCase(Locale.ROOT);
            var icon = ResourceLocation.fromNamespaceAndPath("teutonlib", "buttons/computer_" + (page == 1 ? "sources" : "channels") + (family == ComputerFamily.ADVANCED ? "_hover" : ""));
            g.blitSprite(icon, getX() + 6, getY() + 5, 12, 12);
            text(g, title, getX() + 24, getY() + 7, width - 100, TEXT);
            if (page == 2) {
                String backend = snapshot.getBoolean("dbw") ? "DbW" : label("local");
                g.drawString(font, backend, getX() + width - 28 - font.width(backend), getY() + 7, MUTED, false);
            }
            g.fill(getX() + 5, getY() + 23, getX() + width - 5, getY() + 24, LINE);
            if (page == 2) renderEditor(g);
            var entries = entries();
            int total = entries.stream().mapToInt(Row::height).sum();
            int maxOffset = 0, remaining = total;
            while (remaining > viewportHeight() && maxOffset < entries.size() - 1) remaining -= entries.get(maxOffset++).height();
            offset = Math.clamp(offset, 0, maxOffset);
            int scrolled = entries.stream().limit(offset).mapToInt(Row::height).sum();
            int left = getX() + 6, top = getY() + 28, right = left + listWidth();
            String hovered = null;
            g.enableScissor(left, top, right, top + viewportHeight());
            int y = top;
            for (var row : entries.subList(offset, entries.size())) {
                if (y + row.height() > top + viewportHeight()) break;
                boolean over = mx >= left && mx < right && my >= Math.max(top, y) && my < Math.min(top + viewportHeight(), y + row.height());
                if (row.entry() == null) {
                    g.fill(left, y + 1, right - 3, y + row.height() - 2, over ? 0xff262626 : 0xff1b1b1b);
                    String arrow = collapsed.contains(page + ":" + row.section()) ? "> " : "v ";
                    text(g, arrow + row.title().toUpperCase(Locale.ROOT), left + 4, y + 4, listWidth() - 10, MUTED);
                } else {
                    var entry = row.entry(); String id = entry.getString("name");
                    boolean chosen = page == 2 ? id.equals(selected) : id.equals(selectedSource);
                    if (chosen || over) g.fill(left, y, right - 3, y + row.height() - 1, chosen ? 0xff2a2a2a : 0xff1e1e1e);
                    if (chosen) g.fill(left, y + 3, left + 2, y + row.height() - 4, accent());
                    if (page == 1) {
                        g.fill(left + 5, y + 6, left + 8, y + 9, indicator());
                        text(g, displayName(entry), left + 13, y + 3, listWidth() - 18, TEXT);
                        String detail = entry.getString("types") + (entry.getString("position").isEmpty() ? "" : " · " + entry.getString("position"));
                        text(g, detail, left + 13, y + 15, listWidth() - 18, MUTED);
                        if (chosen) {
                            text(g, id, left + 13, y + 29, listWidth() - 18, TEXT);
                            text(g, entry.getString("link").isEmpty() ? label("device") + " " + entry.getString("position") : label("link") + " " + entry.getString("link"), left + 13, y + 41, listWidth() - 18, MUTED);
                        }
                        if (over) hovered = id + "\n" + label(entry.getString("connection")) + " · " + detail
                            + (entry.getString("link").isEmpty() ? "" : "\n" + label("link") + " " + entry.getString("link"));
                    } else {
                        int value = entry.getInt("value"); String signal = value + "/15";
                        text(g, id, left + 6, y + 3, listWidth() - 53, TEXT);
                        g.drawString(font, signal, right - font.width(signal) - 7, y + 3, value > 0 ? accent() : MUTED, false);
                        for (int i = 0; i < 15; i++) g.fill(left + 6 + i * 4, y + 16, left + 9 + i * 4, y + 19, i < value ? accent() : LINE);
                        String visibility = label(entry.getBoolean("public") ? "public" : "private");
                        text(g, visibility, left + 70, y + 13, listWidth() - 78, MUTED);
                        if (over) hovered = id + "\n" + label(entry.getString("direction")) + " · " + visibility
                            + "\n" + entry.getInt("connections") + " " + label("connections");
                    }
                }
                y += row.height();
            }
            if (entries.isEmpty()) text(g, label(received ? "empty" : "scanning_short"), left + 4, top + 8, listWidth() - 8, MUTED);
            g.disableScissor();
            if (total > viewportHeight()) {
                int rail = top + viewportHeight(); g.fill(right - 2, top, right - 1, rail, LINE);
                int thumbHeight = Math.max(10, viewportHeight() * viewportHeight() / total);
                int thumb = top + scrolled * (viewportHeight() - thumbHeight) / Math.max(1, total - viewportHeight());
                g.fill(right - 2, thumb, right - 1, thumb + thumbHeight, MUTED);
            }
            String error = snapshot.contains("errorKey") ? label(snapshot.getString("errorKey")) : snapshot.getString("error");
            String status = !received ? label("scanning") : error.isEmpty()
                ? (page == 1 ? snapshot.getList("sources", Tag.TAG_COMPOUND).size() : channels().size()) + " " + label(page == 1 ? "sources" : "channels") : error;
            g.fill(getX() + 5, getY() + height - 16, getX() + width - 5, getY() + height - 15, LINE);
            text(g, status, getX() + 7, getY() + height - 11, width - 30, error.isEmpty() ? MUTED : family == ComputerFamily.ADVANCED ? 0xffcc7777 : TEXT);
            g.fill(getX() + width - 13, getY() + height - 9, getX() + width - 10, getY() + height - 6, received && error.isEmpty() ? indicator() : MUTED);
            if (my >= getY() + height - 16 && isMouseOver(mx, my)) hovered = error.isEmpty() ? label(page == 2 && !snapshot.getBoolean("dbw") ? "dbw_unavailable" : "live") : error;
            setTooltip(hovered == null ? null : Tooltip.create(Component.literal(hovered)));
        }
        private void renderEditor(GuiGraphics g) {
            var entry = selectedEntry();
            g.fill(editorX - 7, getY() + 28, editorX - 6, getY() + height - 21, LINE);
            text(g, label(entry == null ? "new_channel" : "channel").toUpperCase(Locale.ROOT), editorX, getY() + 28, editorWidth, MUTED);
            g.fill(editorX, nameY - 2, editorX + editorWidth, nameY + 12, 0xff181818);
            g.fill(editorX, nameY + 13, editorX + editorWidth, nameY + 14, name.isFocused() ? accent() : LINE);
            int value = entry == null ? 0 : entry.getInt("value"); String signal = value + "/15";
            g.drawString(font, signal, editorX + (editorWidth - font.width(signal)) / 2, nameY + step * 3 + 4, entry == null ? MUTED : accent(), false);
            if (entry != null && !entry.getBoolean("editable")) text(g, label("read_only"), editorX, actionY + 4, editorWidth, MUTED);
            if (!armed.isEmpty() && armed.equals(selected)) {
                text(g, label("confirm_short"), editorX, actionY - 12, editorWidth, accent());
            } else if (entry != null) {
                text(g, entry.getInt("connections") + " " + label("connections"), editorX, actionY - 12, editorWidth, MUTED);
            }
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (!visible || !active || button != 0 || x < getX() + 6 || x >= getX() + 6 + listWidth() || y < getY() + 28 || y >= getY() + 28 + viewportHeight()) return false;
            var entries = entries(); offset = Math.min(offset, Math.max(0, entries.size() - 1));
            int cursor = getY() + 28;
            for (var row : entries.subList(offset, entries.size())) {
                if (cursor + row.height() > getY() + 28 + viewportHeight()) break;
                if (y >= cursor && y < cursor + row.height()) {
                    if (row.entry() == null) { String key = page + ":" + row.section(); if (!collapsed.add(key)) collapsed.remove(key); }
                    else if (page == 2) selectChannel(row.entry());
                    else {
                        selectedSource = selectedSource.equals(row.entry().getString("name")) ? "" : row.entry().getString("name");
                        if (!selectedSource.isEmpty()) ensureSourceVisible();
                    }
                    playDownSound(Minecraft.getInstance().getSoundManager()); return true;
                }
                cursor += row.height();
            }
            return true;
        }
        private void ensureSourceVisible() {
            var entries = entries(); int height = 0;
            for (int i = offset; i < entries.size(); i++) {
                height += entries.get(i).height();
                if (entries.get(i).entry() != null && entries.get(i).entry().getString("name").equals(selectedSource)) {
                    while (height > viewportHeight() && offset < i) height -= entries.get(offset++).height();
                    break;
                }
            }
        }
        @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
            if (!visible || !active || x < getX() || x >= getX() + 6 + listWidth() || y < getY() + 24 || y >= getY() + height - 16) return false;
            offset = Math.max(0, offset - (int)Math.signum(vertical)); return true;
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}
