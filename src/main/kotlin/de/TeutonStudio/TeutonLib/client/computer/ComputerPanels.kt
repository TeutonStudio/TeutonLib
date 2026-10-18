package de.TeutonStudio.TeutonLib.client.computer

import kotlin.math.*
import dan200.computercraft.shared.computer.core.ComputerFamily
import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace
import de.TeutonStudio.TeutonLib.client.computer.ComputerPanels.FlatButton
import de.TeutonStudio.TeutonLib.integration.computer.ComputerPayloads
import java.util.ArrayList
import java.util.HashSet
import java.util.LinkedHashSet
import java.util.UUID
import java.util.function.BiConsumer
import java.util.function.Consumer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.*
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Button.OnPress
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.resources.language.I18n
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor

/** A monitor workspace shared by ordinary computers and addon menu hosts.  */
class ComputerPanels(
    private val menu: Int, x: Int, y: Int, width: Int, height: Int, private val font: Font,
    add: Consumer<AbstractWidget>, private val focusTerminal: Runnable
) {
    private val session: UUID = UUID.randomUUID()
    private val widgets: MutableList<AbstractWidget> = ArrayList<AbstractWidget>()
    private val rows: Rows
    private val name: EditBox
    private val direction: FlatButton?
    private val side: FlatButton?
    private val visibility: FlatButton?
    private val primary: FlatButton?
    private val remove: FlatButton?
    private val minus: FlatButton?
    private val plus: FlatButton?
    private val newChannel: FlatButton?
    private val editorX: Int
    private val editorWidth: Int
    private val nameY: Int
    private val step: Int
    private val actionY: Int
    private var family: ComputerFamily? = ComputerFamily.NORMAL
    private var page = 0
    private var inputSide = 0
    private var input = false
    private var received = false
    private var focusNameRequested = false
    private var lastRequest = Long.MIN_VALUE
    private var snapshot = CompoundTag()
    private var selected = ""
    private var selectedSource = ""
    private var armed = ""
    private var pendingSelection = ""

    init {
        editorWidth = Math.clamp((width / 2 - 12).toLong(), 110, 150)
        editorX = x + width - editorWidth - 7
        nameY = y + 38
        step = Math.clamp(((height - 58) / 5).toLong(), 16, 21)
        actionY = y + height - 34
        rows = Rows(x, y, width, height)
        widgets.add(rows)
        name = EditBox(font, editorX + 3, nameY, editorWidth - 6, 16, tr("name"))
        name.setMaxLength(32)
        name.setBordered(false)
        name.setTextColor(TEXT)
        name.setTextColorUneditable(MUTED)
        name.setHint(tr("name"))
        widgets.add(name)
        direction = button(editorX, nameY + step, editorWidth, "output", Runnable {
            input = !input
            refreshControls()
        })
        side = button(
            editorX + editorWidth / 2 + 2, nameY + step, editorWidth / 2 - 2, "front",
            Runnable {
                inputSide = (inputSide + 1) % 6
                refreshControls()
            })
        visibility = button(editorX, nameY + step * 2, editorWidth, "private", Runnable {
            val entry = selectedEntry()
            if (entry != null) send("public", selected, "", 0, !entry.getBoolean("public"))
        })
        minus = button(editorX, nameY + step * 3, 22, "minus", Runnable { adjust(-1) })
        minus.setMessage(Component.literal("−"))
        plus = button(editorX + editorWidth - 22, nameY + step * 3, 22, "plus", Runnable { adjust(1) })
        plus.setMessage(Component.literal("+"))
        primary = button(editorX, actionY, editorWidth, "create", Runnable {
            if (selectedEntry() == null) send(
                "create",
                name.value.trim { it <= ' ' },
                if (input) "input" else "output",
                inputSide,
                false
            )
            else send("rename", selected, name.value.trim { it <= ' ' }, 0, false)
        })
        remove = button(editorX + editorWidth - 20, actionY, 20, "remove", Runnable {
            if (selected == armed) {
                send("remove", selected, "", 0, false)
                armed = ""
            } else armed = selected
            refreshControls()
        })
        remove.setMessage(Component.literal("×"))
        newChannel = button(x + width - 21, y + 3, 17, "new_channel", Runnable { this.beginNewChannel() })
        newChannel.setMessage(Component.literal("+"))
        name.setResponder(Consumer { value: String? -> refreshControls() })
        widgets.forEach(add)
        show(0)
    }

    fun family(family: ComputerFamily?) {
        this.family = family
    }

    private fun accent(): Int {
        return if (family == ComputerFamily.ADVANCED) -0x33ab else -0x2f2f30
    }

    private fun indicator(): Int {
        return if (family == ComputerFamily.ADVANCED) -0x864278 else -0x333334
    }

    private fun label(key: String): String {
        return tr(key).string
    }

    private fun button(x: Int, y: Int, width: Int, key: String, action: Runnable): FlatButton {
        val button = FlatButton(x, y, width, tr(key), action)
        widgets.add(button)
        return button
    }

    private fun sideName(): String? {
        return arrayOf<String>("front", "back", "left", "right", "top", "bottom")[inputSide]
    }

    private fun channels(): ListTag {
        return snapshot.getList("channels", Tag.TAG_COMPOUND.toInt())
    }

    private fun selectedEntry(): CompoundTag? {
        val list = channels()
        for (i in list.indices) if (list.getCompound(i).getString("name") == selected) return list.getCompound(i)
        return null
    }

    fun beginNewChannel() {
        focusNameRequested = true
        selected = ""
        armed = ""
        name.setValue("")
        refreshControls()
    }

    private fun selectChannel(entry: CompoundTag) {
        selected = entry.getString("name")
        armed = ""
        name.setValue(selected)
        refreshControls()
    }

    private fun adjust(delta: Int) {
        val entry = selectedEntry()
        if (entry != null && entry.getString("direction") == "output") send(
            "set",
            selected,
            "",
            Math.clamp((entry.getInt("value") + delta).toLong(), 0, 15),
            false
        )
    }

    private fun refreshControls() {
        if (newChannel == null) return  // The EditBox responder may run during construction.

        val entry = selectedEntry()
        val enabled = page == 2
        val editing = entry != null
        val writable = !editing || entry.getBoolean("editable")
        for (widget in widgets) {
            widget.visible = enabled
            widget.active = enabled
        }
        rows.visible = page != 0
        rows.active = page != 0
        name.active = enabled && writable
        val kind = if (editing) entry.getString("direction") else if (input) "input" else "output"
        val hasSide = kind == "input"
        direction!!.setMessage(tr(kind))
        direction.active = enabled && !editing
        direction.setWidth(if (hasSide) editorWidth / 2 - 2 else editorWidth)
        side!!.setMessage(Companion.tr((if (editing) entry.getString("side") else sideName())!!))
        side.visible = enabled && hasSide
        side.active = enabled && hasSide && !editing
        visibility!!.setMessage(tr(if (editing && entry.getBoolean("public")) "public" else "private"))
        visibility.active = enabled && editing && writable
        primary!!.setMessage(tr(if (editing) "rename" else "create"))
        primary.visible = enabled && writable
        primary.active =
            enabled && writable && !name.value.trim { it <= ' ' }.isEmpty() && (!editing || name.value
                .trim { it <= ' ' } != selected)
        primary.setWidth(if (editing && writable) editorWidth - 24 else editorWidth)
        remove!!.visible = enabled && editing && writable
        remove.active = remove.visible
        remove.setMessage(Component.literal(if (armed == selected && !armed.isEmpty()) "!" else "×"))
        remove.setTooltip(Tooltip.create(tr(if (armed == selected && !armed.isEmpty()) "confirm_remove" else "remove")))
        val output = kind == "output"
        minus!!.visible = enabled && output
        plus!!.visible = enabled && output
        minus.active = enabled && editing && writable && output && entry.getInt("value") > 0
        plus.active = enabled && editing && writable && output && entry.getInt("value") < 15
    }

    /** Hosts apply this after dispatching clicks/keys, so a new-channel action focuses its name field.  */
    fun takeRequestedFocus(): AbstractWidget? {
        if (!focusNameRequested || page != 2) return null
        focusNameRequested = false
        return name
    }

    fun page(): Int {
        return page
    }

    fun session(): UUID {
        return session
    }

    fun show(next: Int) {
        val changed = next != page
        page = next
        rows.setMessage(tr(if (next == 1) "sources" else "channels"))
        if (changed) {
            selected = ""
            selectedSource = ""
            armed = ""
            pendingSelection = ""
            focusNameRequested = false
            rows.offset = 0
            received = false
            snapshot = CompoundTag()
            name.setValue("")
            if (next != 0) request() else focusTerminal.run()
        }
        refreshControls()
    }

    fun tick() {
        if (page == 0) return
        val tick = if (Minecraft.getInstance().level == null) 0 else Minecraft.getInstance().level!!.gameTime
        if (lastRequest == Long.MIN_VALUE || tick - lastRequest >= 20) request()
        refreshControls()
    }

    private fun request() {
        send("snapshot", "", "", 0, false)
    }

    private fun send(action: String, channel: String, text: String, value: Int, published: Boolean) {
        if (action == "create") pendingSelection = channel
        else if (action == "rename") pendingSelection = text
        if (Minecraft.getInstance().connection != null) PacketDistributor.sendToServer(
            ComputerPayloads.Request(
                menu,
                session,
                action,
                channel,
                text,
                value,
                published
            )
        )
        lastRequest = if (Minecraft.getInstance().level == null) 0 else Minecraft.getInstance().level!!.gameTime
    }

    fun receive(packet: ComputerPayloads.Snapshot) {
        if (packet.menu != menu || (packet.session != session) || packet.data == null) return
        snapshot = packet.data
        received = true
        if (!snapshot.getString("error").isEmpty() || snapshot.contains("errorKey")) pendingSelection = ""
        if (!pendingSelection.isEmpty()) {
            val entries = channels()
            for (i in entries.indices) if (entries.getCompound(i).getString("name") == pendingSelection) {
                selected = pendingSelection
                pendingSelection = ""
                armed = ""
                name.setValue(selected)
                break
            }
        }
        if (!selected.isEmpty() && selectedEntry() == null) {
            selected = ""
            armed = ""
            name.setValue("")
        }
        refreshControls()
    }

    interface Owner {
        fun `teutonlib$panels`(): ComputerPanels?
    }

    /** Controls inside the monitor are flat; the physical sidebar retains CC's casing.  */
    private inner class FlatButton(x: Int, y: Int, width: Int, message: Component, action: Runnable) :
        Button(x, y, width, 16, message, OnPress { b: Button? -> action.run() }, DEFAULT_NARRATION) {
        init {
            setTooltip(Tooltip.create(message))
        }

        public override fun renderWidget(g: GuiGraphics, mx: Int, my: Int, delta: Float) {
            val color = if (!active) -0xaaaaab else if (isHoveredOrFocused()) accent() else TEXT
            g.fill(
                getX(),
                getY(),
                getX() + width,
                getY() + height,
                if (active && isHoveredOrFocused()) -0xcfcfd0 else -0xe3e3e4
            )
            g.fill(
                getX(),
                getY() + height - 1,
                getX() + width,
                getY() + height,
                if (active && isHoveredOrFocused()) accent() else LINE
            )
            val text = font.plainSubstrByWidth(getMessage().string, max(1, width - 6))
            g.drawString(font, text, getX() + (width - font.width(text)) / 2, getY() + 4, color, false)
        }
    }

    @JvmRecord
    private data class Row(val section: String, val title: String, val entry: CompoundTag?, val height: Int)
    private inner class Rows(x: Int, y: Int, width: Int, height: Int) :
        AbstractWidget(x, y, width, height, tr("sources")) {
        var offset = 0
        private val collapsed: MutableSet<String> = HashSet<String>()
        fun listWidth(): Int {
            return if (page == 2) editorX - getX() - 15 else width - 12
        }

        fun viewportHeight(): Int {
            return height - 47
        }

        fun entries(): MutableList<Row> {
            val result = ArrayList<Row>()
            val list = if (page == 1) snapshot.getList("sources", Tag.TAG_COMPOUND.toInt()) else channels()
            val groups = if (page == 1) mutableListOf<String>(
                "local",
                "wired",
                "wireless"
            ) else mutableListOf<String>("output", "input", "extensions")
            val extra = LinkedHashSet<String>()
            for (i in list.indices) {
                val entry = list.getCompound(i)
                val group = group(entry)
                if (!groups.contains(group)) extra.add(group)
            }
            val all = ArrayList<String>(groups)
            all.addAll(extra)
            for (group in all) {
                val members = ArrayList<CompoundTag>()
                for (i in list.indices) if (group(list.getCompound(i)) == group) members.add(list.getCompound(i))
                if (members.isEmpty()) continue
                result.add(
                    ComputerPanels.Row(
                        group,
                        label((if (page == 2 && group != "extensions") group + "s" else group)!!),
                        null,
                        15
                    )
                )
                if (!collapsed.contains(page.toString() + ":" + group)) for (entry in members) result.add(
                    Row(
                        group,
                        "",
                        entry,
                        if (page == 1) if (entry.getString("name") == selectedSource) 56 else 25 else 23
                    )
                )
            }
            return result
        }

        fun group(entry: CompoundTag): String {
            if (page == 1) return entry.getString("connection")
            return if (entry.getBoolean("editable")) entry.getString("direction") else "extensions"
        }

        fun displayName(entry: CompoundTag): String {
            val address = entry.getString("name")
            if (!address.startsWith("cc_aeroworks:link_")) return address
            val type: String? =
                entry.getString("types").split(",".toRegex(), limit = 2).toTypedArray()[0].replace('_', ' ')
            val id = address.substring("cc_aeroworks:link_".length)
            return type + " · " + label("link") + " " + id.substring(0, min(8, id.length))
        }

        fun text(g: GuiGraphics, text: String, x: Int, y: Int, maxWidth: Int, color: Int) {
            if (maxWidth > 0) {
                val visible = if (font.width(text) <= maxWidth) text else font.plainSubstrByWidth(
                    text,
                    max(0, maxWidth - font.width("…"))
                ) + "…"
                g.drawString(font, visible, x, y, color, false)
            }
        }

        override fun renderWidget(g: GuiGraphics, mx: Int, my: Int, delta: Float) {
            g.fill(getX(), getY(), getX() + width, getY() + height, BACKGROUND)
            val title = label(if (page == 1) "sources" else "channels").uppercase()
            val icon = ResourceLocation.fromNamespaceAndPath(
                "teutonlib",
                "buttons/computer_" + (if (page == 1) "sources" else "channels") + (if (family == ComputerFamily.ADVANCED) "_hover" else "")
            )
            g.blitSprite(icon, getX() + 6, getY() + 5, 12, 12)
            text(g, title, getX() + 24, getY() + 7, width - 100, TEXT)
            if (page == 2) {
                val backend = if (snapshot.getBoolean("dbw")) "DbW" else label("local")
                g.drawString(font, backend, getX() + width - 28 - font.width(backend), getY() + 7, MUTED, false)
            }
            g.fill(getX() + 5, getY() + 23, getX() + width - 5, getY() + 24, LINE)
            if (page == 2) renderEditor(g)
            val entries = entries()
            val total = entries.stream().mapToInt(Row::height).sum()
            var maxOffset = 0
            var remaining = total
            while (remaining > viewportHeight() && maxOffset < entries.size - 1) remaining -= entries.get(maxOffset++).height
            offset = Math.clamp(offset.toLong(), 0, maxOffset)
            val scrolled = entries.stream().limit(offset.toLong()).mapToInt(Row::height).sum()
            val left = getX() + 6
            val top = getY() + 28
            val right = left + listWidth()
            var hovered: String? = null
            g.enableScissor(left, top, right, top + viewportHeight())
            var y = top
            for (row in entries.subList(offset, entries.size)) {
                if (y + row.height > top + viewportHeight()) break
                val over =
                    mx >= left && mx < right && my >= max(top, y) && my < min(top + viewportHeight(), y + row.height)
                if (row.entry == null) {
                    g.fill(left, y + 1, right - 3, y + row.height - 2, if (over) -0xd9d9da else -0xe4e4e5)
                    val arrow = if (collapsed.contains(page.toString() + ":" + row.section)) "> " else "v "
                    text(g, arrow + row.title!!.uppercase(), left + 4, y + 4, listWidth() - 10, MUTED)
                } else {
                    val entry = row.entry
                    val id = entry.getString("name")
                    val chosen = if (page == 2) (id == selected) else (id == selectedSource)
                    if (chosen || over) g.fill(
                        left,
                        y,
                        right - 3,
                        y + row.height - 1,
                        if (chosen) -0xd5d5d6 else -0xe1e1e2
                    )
                    if (chosen) g.fill(left, y + 3, left + 2, y + row.height - 4, accent())
                    if (page == 1) {
                        g.fill(left + 5, y + 6, left + 8, y + 9, indicator())
                        text(g, displayName(entry), left + 13, y + 3, listWidth() - 18, TEXT)
                        val detail = entry.getString("types") + (if (entry.getString("position")
                                .isEmpty()
                        ) "" else " · " + entry.getString("position"))
                        text(g, detail, left + 13, y + 15, listWidth() - 18, MUTED)
                        if (chosen) {
                            text(g, id, left + 13, y + 29, listWidth() - 18, TEXT)
                            text(
                                g,
                                if (entry.getString("link")
                                        .isEmpty()
                                ) label("device") + " " + entry.getString("position") else label("link") + " " + entry.getString(
                                    "link"
                                ),
                                left + 13,
                                y + 41,
                                listWidth() - 18,
                                MUTED
                            )
                        }
                        if (over) hovered = (id + "\n" + label(entry.getString("connection")) + " · " + detail
                                + (if (entry.getString("link")
                                .isEmpty()
                        ) "" else "\n" + label("link") + " " + entry.getString("link")))
                    } else {
                        val value = entry.getInt("value")
                        val signal = value.toString() + "/15"
                        text(g, id, left + 6, y + 3, listWidth() - 53, TEXT)
                        g.drawString(
                            font,
                            signal,
                            right - font.width(signal) - 7,
                            y + 3,
                            if (value > 0) accent() else MUTED,
                            false
                        )
                        for (i in 0..14) g.fill(
                            left + 6 + i * 4,
                            y + 16,
                            left + 9 + i * 4,
                            y + 19,
                            if (i < value) accent() else LINE
                        )
                        val visibility = label(if (entry.getBoolean("public")) "public" else "private")
                        text(g, visibility, left + 70, y + 13, listWidth() - 78, MUTED)
                        if (over) hovered = (id + "\n" + label(entry.getString("direction")) + " · " + visibility
                                + "\n" + entry.getInt("connections") + " " + label("connections"))
                    }
                }
                y += row.height
            }
            if (entries.isEmpty()) text(
                g,
                label(if (received) "empty" else "scanning_short"),
                left + 4,
                top + 8,
                listWidth() - 8,
                MUTED
            )
            g.disableScissor()
            if (total > viewportHeight()) {
                val rail = top + viewportHeight()
                g.fill(right - 2, top, right - 1, rail, LINE)
                val thumbHeight = max(10, viewportHeight() * viewportHeight() / total)
                val thumb = top + scrolled * (viewportHeight() - thumbHeight) / max(1, total - viewportHeight())
                g.fill(right - 2, thumb, right - 1, thumb + thumbHeight, MUTED)
            }
            val error =
                if (snapshot.contains("errorKey")) label(snapshot.getString("errorKey")) else snapshot.getString("error")
            val status = if (!received) label("scanning") else if (error.isEmpty())
                (if (page == 1) snapshot.getList(
                    "sources",
                    Tag.TAG_COMPOUND.toInt()
                ).size else channels().size).toString() + " " + label(if (page == 1) "sources" else "channels")
            else
                error
            g.fill(getX() + 5, getY() + height - 16, getX() + width - 5, getY() + height - 15, LINE)
            text(
                g,
                status,
                getX() + 7,
                getY() + height - 11,
                width - 30,
                if (error.isEmpty()) MUTED else if (family == ComputerFamily.ADVANCED) -0x338889 else TEXT
            )
            g.fill(
                getX() + width - 13,
                getY() + height - 9,
                getX() + width - 10,
                getY() + height - 6,
                if (received && error.isEmpty()) indicator() else MUTED
            )
            if (my >= getY() + height - 16 && isMouseOver(mx.toDouble(), my.toDouble())) hovered =
                if (error.isEmpty()) label(if (page == 2 && !snapshot.getBoolean("dbw")) "dbw_unavailable" else "live") else error
            setTooltip(if (hovered == null) null else Tooltip.create(Component.literal(hovered)))
        }

        fun renderEditor(g: GuiGraphics) {
            val entry = selectedEntry()
            g.fill(editorX - 7, getY() + 28, editorX - 6, getY() + height - 21, LINE)
            text(
                g,
                label(if (entry == null) "new_channel" else "channel").uppercase(),
                editorX,
                getY() + 28,
                editorWidth,
                MUTED
            )
            g.fill(editorX, nameY - 2, editorX + editorWidth, nameY + 12, -0xe7e7e8)
            g.fill(editorX, nameY + 13, editorX + editorWidth, nameY + 14, if (name.isFocused()) accent() else LINE)
            val value = if (entry == null) 0 else entry.getInt("value")
            val signal = value.toString() + "/15"
            g.drawString(
                font,
                signal,
                editorX + (editorWidth - font.width(signal)) / 2,
                nameY + step * 3 + 4,
                if (entry == null) MUTED else accent(),
                false
            )
            if (entry != null && !entry.getBoolean("editable")) text(
                g,
                label("read_only"),
                editorX,
                actionY + 4,
                editorWidth,
                MUTED
            )
            if (!armed.isEmpty() && armed == selected) {
                text(g, label("confirm_short"), editorX, actionY - 12, editorWidth, accent())
            } else if (entry != null) {
                text(
                    g,
                    entry.getInt("connections").toString() + " " + label("connections"),
                    editorX,
                    actionY - 12,
                    editorWidth,
                    MUTED
                )
            }
        }

        override fun mouseClicked(x: Double, y: Double, button: Int): Boolean {
            if (!visible || !active || button != 0 || x < getX() + 6 || x >= getX() + 6 + listWidth() || y < getY() + 28 || y >= getY() + 28 + viewportHeight()) return false
            val entries = entries()
            offset = min(offset, max(0, entries.size - 1))
            var cursor = getY() + 28
            for (row in entries.subList(offset, entries.size)) {
                if (cursor + row.height > getY() + 28 + viewportHeight()) break
                if (y >= cursor && y < cursor + row.height) {
                    if (row.entry == null) {
                        val key = page.toString() + ":" + row.section
                        if (!collapsed.add(key)) collapsed.remove(key)
                    } else if (page == 2) selectChannel(row.entry)
                    else {
                        selectedSource =
                            if (selectedSource == row.entry.getString("name")) "" else row.entry.getString("name")
                        if (!selectedSource.isEmpty()) ensureSourceVisible()
                    }
                    playDownSound(Minecraft.getInstance().soundManager)
                    return true
                }
                cursor += row.height
            }
            return true
        }

        fun ensureSourceVisible() {
            val entries = entries()
            var height = 0
            for (i in offset..<entries.size) {
                height += entries.get(i).height
                if (entries.get(i).entry != null && entries.get(i).entry!!.getString("name") == selectedSource) {
                    while (height > viewportHeight() && offset < i) height -= entries.get(offset++).height
                    break
                }
            }
        }

        override fun mouseScrolled(x: Double, y: Double, horizontal: Double, vertical: Double): Boolean {
            if (!visible || !active || x < getX() || x >= getX() + 6 + listWidth() || y < getY() + 24 || y >= getY() + height - 16) return false
            offset = max(0, offset - sign(vertical).toInt())
            return true
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) {
            defaultButtonNarrationText(output)
        }
    }

    companion object {
        private const val BACKGROUND = -0xeeeeef
        private const val LINE = -0xd4d4d5
        private const val TEXT = -0x1f1f20
        private const val MUTED = -0x777778

        /** Alternate authoritative backend using the same native computer workspace and sidebar.  */
        @JvmStatic
        fun workspace(
            menu: Int,
            backend: ResourceLocation,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            font: Font,
            add: Consumer<AbstractWidget>,
            focusTerminal: Runnable,
            clientAction: BiConsumer<ComputerWorkspace.Action, ComputerWorkspace.Entry?>
        ): WorkspacePanels {
            return WorkspacePanels(menu, backend, x, y, width, height, font, add, focusTerminal, clientAction)
        }

        @JvmStatic
        private fun tr(key: String): Component {
            val translation = "teutonlib.computer." + key
            return if (I18n.exists(translation)) Component.translatable(translation) else Component.literal(key)
        }

        @JvmStatic
        fun accept(packet: ComputerPayloads.Snapshot) {
            val screen = Minecraft.getInstance().screen as? Owner ?: return
            screen.`teutonlib$panels`()?.receive(packet)
        }
    }
}
