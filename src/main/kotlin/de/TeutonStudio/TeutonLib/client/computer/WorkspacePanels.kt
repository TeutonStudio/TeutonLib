package de.TeutonStudio.TeutonLib.client.computer

import kotlin.math.*
import dan200.computercraft.shared.computer.core.ComputerFamily
import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace
import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.*
import de.TeutonStudio.TeutonLib.api.computer.WorkspaceSession
import de.TeutonStudio.TeutonLib.integration.computer.WorkspacePayloads
import java.util.ArrayList
import java.util.HashMap
import java.util.UUID
import java.util.function.BiConsumer
import java.util.function.Consumer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Button.OnPress
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.PacketDistributor

/** Shared addon controller: hierarchical sources/channels, editor, focus and session isolation.  */
class WorkspacePanels(
    private val menu: Int,
    private val backend: ResourceLocation,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val font: Font,
    add: Consumer<AbstractWidget>,
    private val terminalFocus: Runnable,
    private val clientAction: BiConsumer<ComputerWorkspace.Action, ComputerWorkspace.Entry?>
) {
    interface Owner {
        fun `teutonlib$workspace`(): WorkspacePanels?
    }

    private val widgets: MutableList<AbstractWidget> = ArrayList<AbstractWidget>()
    private val actions: MutableList<Button> = ArrayList<Button>()
    private val name: EditBox
    private val rows: Rows
    private val collapsed: MutableMap<String, Boolean> = HashMap<String, Boolean>()
    private var session: UUID = UUID.randomUUID()
    private var snapshot = ComputerWorkspace.Snapshot(
        "",
        "",
        mutableListOf<ComputerWorkspace.Entry>(),
        mutableListOf<ComputerWorkspace.Action>()
    )
    private var selected = ""
    private var group = ""
    private var target = ""
    private var armed = ""
    private var error = ""
    private var page = 0
    private var lastRequest = Long.MIN_VALUE
    private var received = false
    private var requestFocus = false
    private var family: ComputerFamily? = ComputerFamily.NORMAL

    init {
        rows = Rows(x, y, width, height)
        widgets.add(rows)
        val editorWidth = min(138, max(100, width / 3))
        val editorX = x + width - editorWidth - 7
        name = EditBox(font, editorX, y + 32, editorWidth, 18, text("teutonlib.computer.name"))
        name.setMaxLength(32)
        name.setHint(text("teutonlib.computer.name"))
        widgets.add(name)
        // Two columns leave room for all six Aeroworks actions even on the smaller normal monitor.
        for (i in 0..7) {
            val index = i
            val button = Button.builder(Component.empty(), OnPress { b: Button? -> perform(index) })
                .bounds(editorX + (i % 2) * (editorWidth / 2 + 1), y + 58 + (i / 2) * 21, editorWidth / 2 - 1, 18)
                .build()
            actions.add(button)
            widgets.add(button)
        }
        widgets.forEach(add)
        refresh()
    }

    fun family(value: ComputerFamily?) {
        family = value
    }

    fun page(): Int {
        return page
    }

    fun session(): UUID {
        return session
    }

    fun show(next: Int) {
        require(!(next < 0 || next > 2)) { "Invalid page" }
        if (next != page) {
            page = next
            session = UUID.randomUUID()
            error = ""
            armed = error
            target = armed
            group = target
            selected = group
            snapshot = ComputerWorkspace.Snapshot(
                "",
                "",
                mutableListOf<ComputerWorkspace.Entry>(),
                mutableListOf<ComputerWorkspace.Action>()
            )
            received = false
            rows.offset = 0
            name.setValue("")
            requestFocus = next == 2
            if (next == 0) terminalFocus.run() else send("snapshot")
        }
        refresh()
    }

    fun tick() {
        val now = if (Minecraft.getInstance().level == null) 0 else Minecraft.getInstance().level!!.gameTime
        if (page != 0 && (lastRequest == Long.MIN_VALUE || now - lastRequest >= 20)) send("snapshot")
        refresh()
    }

    fun takeRequestedFocus(): AbstractWidget? {
        if (!requestFocus || page != 2 || !name.visible) return null
        requestFocus = false
        return name
    }

    private fun find(entries: List<ComputerWorkspace.Entry>, id: String?): ComputerWorkspace.Entry? {
        for (entry in entries) {
            if (entry.id == id) return entry
            val nested = find(entry.children, id)
            if (nested != null) return nested
        }
        return null
    }

    private fun hasGroup(entries: List<ComputerWorkspace.Entry>, id: String?): Boolean {
        for (entry in entries) if (entry.group == id || hasGroup(entry.children, id)) return true
        return false
    }

    private fun selection(): ComputerWorkspace.Entry? {
        return if (selected.isEmpty()) null else find(snapshot.entries, selected)
    }

    private fun available(): MutableList<ComputerWorkspace.Action> {
        val all = ArrayList<ComputerWorkspace.Action>(snapshot.actions)
        val entry = selection()
        if (entry != null) all.addAll(entry.actions)
        return all
    }

    private fun refresh() {
        rows.active = page != 0
        rows.visible = rows.active
        val available = available()
        name.active = page == 2 && available.stream().anyMatch(ComputerWorkspace.Action::needsText)
        name.visible = name.active
        for (i in actions.indices) {
            val b = actions.get(i)
            b.visible = page == 2 && i < available.size
            b.active = b.visible
            if (!b.visible) continue
            val action = available.get(i)
            val key = selected + ":" + action.id
            b.setMessage(text(if (armed == key) "teutonlib.computer.confirm_short" else action.label))
            b.setTooltip(Tooltip.create(text(if (armed == key) "teutonlib.computer.confirm_remove" else action.label)))
            b.active = !action.needsText || !name.value.trim { it <= ' ' }.isEmpty()
        }
    }

    private fun perform(index: Int) {
        val list = available()
        if (index >= list.size) return
        val action = list.get(index)
        val entry = selection()
        val key = selected + ":" + action.id
        if (action.confirm && armed != key) {
            armed = key
            refresh()
            return
        }
        armed = ""
        if (action.clientSide) clientAction.accept(action, entry)
        else send(action.id)
    }

    private fun send(action: String) {
        if (Minecraft.getInstance().connection == null) return
        PacketDistributor.sendToServer(
            WorkspacePayloads.Request(
                menu, session, backend,
                ComputerWorkspace.Command(page, action, selected, group, target, name.value.trim { it <= ' ' })
            )
        )
        lastRequest = if (Minecraft.getInstance().level == null) 0 else Minecraft.getInstance().level!!.gameTime
    }

    fun receive(response: WorkspacePayloads.Response) {
        if (!matches(
                response.menu,
                response.session,
                response.backend,
                response.page,
                menu,
                session,
                backend,
                page
            ) || response.data == null
        ) return
        snapshot = ComputerWorkspace.Snapshot.Companion.decode(response.data)
        error = response.data.getString("error")
        received = true
        if (!selected.isEmpty() && selection() == null) {
            selected = ""
            armed = ""
        }
        if (!group.isEmpty() && !hasGroup(snapshot.entries, group)) group = ""
        refresh()
    }

    private fun accent(): Int {
        return ComputerSidebarTabs.accent(family)
    }

    @JvmRecord
    private data class Row(val entry: ComputerWorkspace.Entry, val depth: Int)
    private inner class Rows(x: Int, y: Int, width: Int, height: Int) :
        AbstractWidget(x, y, width, height, Component.translatable("teutonlib.computer.sources")) {
        var offset = 0
        fun listWidth(): Int {
            return if (page == 2) name.x - getX() - 15 else width - 12
        }

        fun capacity(): Int {
            return max(1, (height - 46) / 20)
        }

        fun flatten(entries: List<ComputerWorkspace.Entry>, depth: Int, rows: MutableList<Row>) {
            for (entry in entries) {
                rows.add(Row(entry, depth))
                if (!collapsed.getOrDefault(entry.id, entry.collapsed)!!) flatten(entry.children, depth + 1, rows)
            }
        }

        fun entries(): MutableList<Row> {
            val result = ArrayList<Row>()
            flatten(snapshot.entries, 0, result)
            return result
        }

        override fun renderWidget(g: GuiGraphics, mx: Int, my: Int, delta: Float) {
            g.fill(getX(), getY(), getX() + width, getY() + height, -0xeeeeef)
            g.drawString(
                font,
                text(if (snapshot.title.isEmpty()) "teutonlib.computer." + (if (page == 1) "sources" else "channels") else snapshot.title),
                getX() + 6,
                getY() + 7,
                accent(),
                false
            )
            g.fill(getX() + 5, getY() + 23, getX() + width - 5, getY() + 24, -0xd4d4d5)
            val rows = entries()
            offset = Math.clamp(offset.toLong(), 0, max(0, rows.size - capacity()))
            val left = getX() + 6
            val top = getY() + 28
            val right = left + listWidth()
            var tooltip: String? = null
            g.enableScissor(left, top, right, getY() + height - 18)
            try {
                for (i in offset..<min(rows.size, offset + capacity())) {
                    val row = rows.get(i)
                    val entry = row.entry
                    val y = top + (i - offset) * 20
                    val hover = mx >= left && mx < right && my >= y && my < y + 20
                    if (hover || selected == entry.id) g.fill(left, y, right - 3, y + 19, -0xd5d5d6)
                    val x = left + min(row.depth, 5) * 8
                    val arrow = if (entry.children.isEmpty()) "" else if (collapsed.getOrDefault(
                            entry.id,
                            entry.collapsed
                        )
                    ) "> " else "v "
                    val label = arrow + text(entry.label).string
                    g.drawString(
                        font,
                        font.plainSubstrByWidth(label, max(1, right - x - 6)),
                        x,
                        y + 2,
                        if (selected == entry.id) accent() else -0x1f1f20,
                        false
                    )
                    g.drawString(
                        font,
                        font.plainSubstrByWidth(entry.detail, max(1, right - x - 6)),
                        x,
                        y + 11,
                        -0x777778,
                        false
                    )
                    if (hover) tooltip = label + (if (entry.detail.isEmpty()) "" else "\n" + entry.detail)
                }
            } finally {
                g.disableScissor()
            }
            if (rows.size > capacity()) {
                val rail = capacity() * 20
                val thumb = max(8, rail * capacity() / rows.size)
                val y = top + offset * (rail - thumb) / max(1, rows.size - capacity())
                g.fill(right - 2, top, right - 1, top + rail, -0xd4d4d5)
                g.fill(right - 2, y, right - 1, y + thumb, -0x777778)
            }
            val status =
                if (!error.isEmpty()) error else if (received) snapshot.status else text("teutonlib.computer.scanning").string
            g.drawString(
                font,
                font.plainSubstrByWidth(status, width - 14),
                left,
                getY() + height - 11,
                if (error.isEmpty()) -0x777778 else accent(),
                false
            )
            setTooltip(if (tooltip == null) null else Tooltip.create(Component.literal(tooltip)))
        }

        override fun mouseClicked(x: Double, y: Double, button: Int): Boolean {
            if (!visible || !active || button != 0 || x < getX() + 6 || x >= getX() + 6 + listWidth() || y < getY() + 28 || y >= getY() + 28 + capacity() * 20) return false
            val rows = entries()
            val index = offset + ((y - getY() - 28) / 20).toInt()
            if (index >= rows.size) return true
            val entry = rows.get(index).entry
            if (!entry.children.isEmpty()) collapsed.put(entry.id, !collapsed.getOrDefault(entry.id, entry.collapsed)!!)
            if (entry.selectable) {
                selected = entry.id
                armed = ""
                if (!entry.group.isEmpty()) group = entry.group
                if (!entry.target.isEmpty()) target = entry.target
                refresh()
            }
            playDownSound(Minecraft.getInstance().soundManager)
            return true
        }

        override fun mouseScrolled(x: Double, y: Double, h: Double, v: Double): Boolean {
            if (!visible || !active || !isMouseOver(x, y)) return false
            offset = Math.clamp((offset - sign(v).toInt()).toLong(), 0, max(0, entries().size - capacity()))
            return true
        }

        override fun updateWidgetNarration(output: NarrationElementOutput) {
            defaultButtonNarrationText(output)
        }
    }

    companion object {
        @JvmStatic
        private fun text(value: String): Component {
            return if (I18n.exists(value)) Component.translatable(value) else Component.literal(value)
        }

        /** Pure identity check, also exercised without a running Minecraft client.  */
        @JvmStatic
        fun matches(
            responseMenu: Int, responseSession: UUID, responseBackend: ResourceLocation, responsePage: Int,
            menu: Int, session: UUID, backend: ResourceLocation, page: Int
        ): Boolean {
            return WorkspaceSession(menu, session, backend.toString(), page)
                .accepts(WorkspaceSession(responseMenu, responseSession, responseBackend.toString(), responsePage))
        }

        @JvmStatic
        fun accept(response: WorkspacePayloads.Response) {
            val screen = Minecraft.getInstance().screen as? Owner ?: return
            screen.`teutonlib$workspace`()?.receive(response)
        }
    }
}
