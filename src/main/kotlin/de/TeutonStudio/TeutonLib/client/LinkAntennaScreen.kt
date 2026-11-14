package de.TeutonStudio.TeutonLib.client

import de.TeutonStudio.TeutonLib.integration.peripheral.LinkAntennaMenu
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkAntennaPayloads
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkAntennaSides
import java.util.UUID
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.neoforge.network.PacketDistributor
import kotlin.math.max
import kotlin.math.sign

class LinkAntennaScreen(menu: LinkAntennaMenu, inventory: Inventory, title: Component) :
    AbstractContainerScreen<LinkAntennaMenu>(menu, inventory, title) {
    private val session = UUID.randomUUID()
    private var snapshot = CompoundTag()
    private var selected: UUID? = null
    private var offset = 0
    private var lastRequest = Long.MIN_VALUE
    private var settings = false
    private var received = false
    private var pending = false
    private var sides = LinkAntennaSides.bit(Direction.UP)
    private lateinit var mark: Button
    private val sideButtons = linkedMapOf<Direction, Button>()
    init { imageWidth = 390; imageHeight = 250; inventoryLabelY = -10000; titleLabelY = -10000 }
    override fun init() {
        super.init()
        addRenderableWidget(PeripheralLinkTheme.button(leftPos + 14, topPos + 33, 156, 18,
            text("teutonlib.antenna.connections"), { !settings }) { settings = false; refresh() })
        addRenderableWidget(PeripheralLinkTheme.button(leftPos + 176, topPos + 33, 200, 18,
            text("teutonlib.antenna.sides"), { settings }) { settings = true; refresh() })
        mark = addRenderableWidget(PeripheralLinkTheme.button(leftPos + 181, topPos + 207, 193, 18,
            text("teutonlib.antenna.mark_link")) { selected?.let { id -> send("mark", id) } })
        sideButtons.clear()
        val order = listOf(Direction.UP, Direction.NORTH, Direction.WEST, Direction.DOWN, Direction.SOUTH, Direction.EAST)
        order.forEachIndexed { index, side ->
            sideButtons[side] = addRenderableWidget(PeripheralLinkTheme.button(leftPos + 19 + index % 3 * 119,
                topPos + 100 + index / 3 * 40, 114, 32, Component.empty(), { sides and LinkAntennaSides.bit(side) != 0 }) {
                val next = sides xor LinkAntennaSides.bit(side)
                if (LinkAntennaSides.valid(next) && !pending) { pending = true; send("sides", mask = next); refresh() }
            })
        }
        refresh()
        send("snapshot")
    }
    private fun send(action: String, link: UUID? = null, mask: Int? = null) {
        if (minecraft?.connection == null) return
        PacketDistributor.sendToServer(LinkAntennaPayloads.Command(CompoundTag().also {
            it.putString("action", action); it.putInt("menu", menu.containerId); it.putUUID("session", session)
            link?.let { id -> it.putUUID("link", id) }; mask?.let { itMask -> it.putInt("sides", itMask) }
        }))
        lastRequest = minecraft?.level?.gameTime ?: 0L
    }
    override fun containerTick() {
        super.containerTick()
        val now = minecraft?.level?.gameTime ?: 0L
        if (lastRequest == Long.MIN_VALUE || now - lastRequest >= 20) send("snapshot")
    }
    fun receive(data: CompoundTag) {
        if (data.getInt("menu") != menu.containerId || !data.hasUUID("session") || data.getUUID("session") != session) return
        snapshot = data
        sides = data.getInt("sides").takeIf(LinkAntennaSides::valid) ?: LinkAntennaSides.bit(Direction.UP)
        received = true; pending = false
        if (rows().none { it.getUUID("id") == selected }) selected = rows().firstOrNull()?.getUUID("id")
        refresh()
    }
    fun session(): UUID = session
    private fun rows(): List<CompoundTag> = snapshot.getList("links", 10).map { it as CompoundTag }
    private fun chosen(): CompoundTag? = rows().firstOrNull { it.getUUID("id") == selected }
    private fun refresh() {
        if (::mark.isInitialized) {
            mark.visible = !settings
            mark.active = chosen()?.getCompound("location")?.contains("distance") == true
        }
        sideButtons.forEach { (side, button) ->
            val enabled = sides and LinkAntennaSides.bit(side) != 0
            val last = enabled && Integer.bitCount(sides) == 1
            button.message = Component.literal(if (enabled) "[x] " else "[ ] ").append(sideName(side))
            button.visible = settings
            button.active = received && snapshot.getBoolean("editable") && !pending && !last
            button.setTooltip(Tooltip.create(if (last) text("teutonlib.antenna.minimum_sides") else sideName(side)))
        }
    }
    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)
        super.render(graphics, mouseX, mouseY, partialTick)
        if (settings) return
        if (mouseX in leftPos + 14..leftPos + 169 && mouseY in topPos + 56..topPos + 202) {
            rows().getOrNull(offset + (mouseY - topPos - 56) / 21)?.let { row ->
                graphics.renderTooltip(font, Component.literal(row.getString("nickname").ifEmpty { row.getCompound("device").getString("block") }), mouseX, mouseY)
            }
        }
        if (mouseX in leftPos + 176..leftPos + 375 && mouseY in topPos + 56..topPos + 200) {
            chosen()?.let { row ->
                val loc = row.getCompound("location")
                graphics.renderTooltip(font, Component.literal("cc_aeroworks:link_${row.getUUID("id")}\n${row.getCompound("device").getString("block")}\n${loc.getString("dimension")}\n${BlockPositions.text(loc)}"), mouseX, mouseY)
            }
        }
    }
    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val x = leftPos; val y = topPos
        PeripheralLinkTheme.frame(graphics, x, y, imageWidth, imageHeight, title)
        if (settings) {
            graphics.drawString(font, text("teutonlib.antenna.sides"), x + 19, y + 64, PeripheralLinkTheme.ACCENT, false)
            graphics.drawWordWrap(font, text("teutonlib.antenna.sides_hint"), x + 19, y + 79, 350, PeripheralLinkTheme.MUTED)
            graphics.drawWordWrap(font, text("teutonlib.antenna.minimum_sides"), x + 19, y + 190, 350, PeripheralLinkTheme.MUTED)
        } else renderConnections(graphics)
        val error = snapshot.getString("error")
        val footer = if (error.isNotEmpty()) error else if (!settings && chosen()?.getCompound("location")?.getBoolean("lastKnown") == true)
            "teutonlib.antenna.last_known" else "teutonlib.peripheral_link.live"
        graphics.drawString(font, font.plainSubstrByWidth(text(footer).string, imageWidth - 30), x + 15, y + 229,
            if (error.isNotEmpty()) 0xffcc4c4c.toInt() else PeripheralLinkTheme.MUTED, false)
    }
    private fun renderConnections(graphics: GuiGraphics) {
        val x = leftPos; val y = topPos
        graphics.fill(x + 14, y + 55, x + 170, y + 224, PeripheralLinkTheme.PANEL)
        graphics.fill(x + 176, y + 55, x + 376, y + 224, PeripheralLinkTheme.PANEL)
        val rows = rows()
        offset = offset.coerceIn(0, max(0, rows.size - 7))
        rows.drop(offset).take(7).forEachIndexed { index, row ->
            val rowY = y + 56 + index * 21
            if (row.getUUID("id") == selected) {
                graphics.fill(x + 15, rowY, x + 169, rowY + 20, PeripheralLinkTheme.HOVER)
                graphics.fill(x + 15, rowY, x + 17, rowY + 20, PeripheralLinkTheme.ACCENT)
            }
            val name = row.getString("nickname").ifEmpty { row.getCompound("device").getString("block") }
            graphics.drawString(font, font.plainSubstrByWidth(name, 145), x + 20, rowY + 2, PeripheralLinkTheme.TEXT, false)
            val status = text("teutonlib.peripheral_link.status.${row.getString("status")}").string
            val loc = row.getCompound("location")
            val distance = if (loc.contains("distance")) " · ${"%.1f".format(java.util.Locale.ROOT, loc.getDouble("distance"))} m" else ""
            graphics.drawString(font, font.plainSubstrByWidth(status + distance, 145), x + 20, rowY + 11, PeripheralLinkTheme.MUTED, false)
        }
        if (rows.size > 7) {
            val h = max(8, 147 * 7 / rows.size)
            val thumb = y + 56 + offset * (147 - h) / max(1, rows.size - 7)
            graphics.fill(x + 167, thumb, x + 169, thumb + h, PeripheralLinkTheme.ACCENT)
        }
        val row = chosen()
        if (row == null) graphics.drawWordWrap(font, text("teutonlib.antenna.empty"), x + 183, y + 62, 187, PeripheralLinkTheme.MUTED)
        else {
            val loc = row.getCompound("location")
            var lineY = y + 58
            fun line(label: String, value: String, maxLines: Int = 1) {
                graphics.drawString(font, text(label), x + 182, lineY, PeripheralLinkTheme.ACCENT, false)
                lineY += 11
                font.split(Component.literal(value), 188).take(maxLines).forEach {
                    graphics.drawString(font, it, x + 182, lineY, PeripheralLinkTheme.TEXT, false); lineY += 10
                }
                lineY += 3
            }
            line("teutonlib.computer.address", "cc_aeroworks:link_${row.getUUID("id")}", 2)
            line("teutonlib.peripheral_link.device_block", font.plainSubstrByWidth(row.getCompound("device").getString("block"), 188))
            line("teutonlib.antenna.world_position", BlockPositions.text(loc))
            line("teutonlib.peripheral_link.target_dimension", font.plainSubstrByWidth(loc.getString("dimension"), 188))
            line("teutonlib.antenna.block_position", net.minecraft.core.BlockPos.of(loc.getLong("position")).toShortString())
        }
    }
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!settings && button == 0 && mouseX >= leftPos + 14 && mouseX < leftPos + 170 && mouseY >= topPos + 56 && mouseY < topPos + 203) {
            rows().getOrNull(offset + ((mouseY - topPos - 56) / 21).toInt())?.let { selected = it.getUUID("id"); refresh() }
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }
    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!settings && mouseX >= leftPos + 14 && mouseX < leftPos + 170 && mouseY >= topPos + 55 && mouseY < topPos + 224) {
            offset = (offset - sign(scrollY).toInt()).coerceIn(0, max(0, rows().size - 7)); return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }
    private fun sideName(side: Direction) = text("teutonlib.antenna.side.${side.name.lowercase(java.util.Locale.ROOT)}")
    private fun text(key: String): Component = Component.translatable(key)
}

internal object BlockPositions {
    fun text(tag: CompoundTag): String = "%.1f, %.1f, %.1f".format(java.util.Locale.ROOT, tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"))
}
