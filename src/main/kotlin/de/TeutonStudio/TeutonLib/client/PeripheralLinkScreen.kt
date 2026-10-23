package de.TeutonStudio.TeutonLib.client

import de.TeutonStudio.TeutonLib.api.computer.SourceApi
import de.TeutonStudio.TeutonLib.integration.peripheral.PeripheralLinkMenu
import de.TeutonStudio.TeutonLib.integration.peripheral.PeripheralLinkPayloads
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.resources.language.I18n
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

/** Standalone source-style UI for a placed Peripheral Link. */
class PeripheralLinkScreen(
    menu: PeripheralLinkMenu,
    inventory: Inventory,
    title: Component
) : AbstractContainerScreen<PeripheralLinkMenu>(menu, inventory, title) {
    private val session = UUID.randomUUID()
    private var snapshot = CompoundTag()
    private var received = false
    private var pending = false
    private var confirmedNickname = ""
    private var error = ""
    private var lastRequest = Long.MIN_VALUE
    private var rowOffset = 0
    private var apiOffset = 0
    private var apiOpen = false
    private var hoveredText: Component? = null
    private lateinit var nickname: EditBox
    private lateinit var apply: Button
    private lateinit var clear: Button
    private lateinit var api: Button

    init {
        imageWidth = 330
        imageHeight = 224
        inventoryLabelY = -10_000
        titleLabelY = -10_000
    }

    override fun init() {
        super.init()
        val editorX = leftPos + 218
        nickname = EditBox(font, editorX, topPos + 52, 104, 18, text("teutonlib.peripheral_link.nickname"))
        nickname.setMaxLength(32)
        nickname.setHint(text("teutonlib.peripheral_link.nickname"))
        nickname.setValue(if (received) confirmedNickname else "")
        nickname.setResponder { refreshWidgets() }
        addRenderableWidget(nickname)
        apply = addRenderableWidget(
            Button.builder(text("teutonlib.peripheral_link.apply")) { send("nickname", nickname.value) }
                .bounds(editorX, topPos + 76, 104, 18).build()
        )
        clear = addRenderableWidget(
            Button.builder(text("teutonlib.peripheral_link.clear")) { send("clear_nickname", "") }
                .bounds(editorX, topPos + 98, 104, 18).build()
        )
        api = addRenderableWidget(
            Button.builder(text("teutonlib.peripheral_link.show_api")) {
                apiOpen = true
                apiOffset = 0
                refreshWidgets()
            }.bounds(editorX, topPos + 126, 104, 18).build()
        )
        refreshWidgets()
        send("snapshot", "")
    }

    override fun containerTick() {
        super.containerTick()
        val now = minecraft?.level?.gameTime ?: 0L
        if (lastRequest == Long.MIN_VALUE || now - lastRequest >= 20) send("snapshot", "")
    }

    private fun refreshWidgets() {
        if (!::nickname.isInitialized) return
        nickname.visible = !apiOpen
        nickname.active = !apiOpen
        val next = nickname.value.trim()
        apply.visible = !apiOpen
        apply.active = !pending && next.isNotEmpty() && next != confirmedNickname
        clear.visible = !apiOpen
        clear.active = !pending && (next.isNotEmpty() || confirmedNickname.isNotEmpty())
        api.visible = !apiOpen
        api.active = api.visible
    }

    private fun send(action: String, value: String) {
        if (minecraft?.connection == null) return
        if (action != "snapshot") pending = true
        PacketDistributor.sendToServer(PeripheralLinkPayloads.Request(menu.containerId, session, action, value))
        lastRequest = minecraft?.level?.gameTime ?: 0L
        refreshWidgets()
    }

    fun receive(response: PeripheralLinkPayloads.Response) {
        if (response.menu != menu.containerId || response.session != session) return
        val previousConfirmed = confirmedNickname
        snapshot = response.data
        received = true
        confirmedNickname = snapshot.getString("nickname")
        error = snapshot.getString("error")
        if (!::nickname.isInitialized) return
        val draftChanged = nickname.value.trim() != previousConfirmed
        if ((pending && error.isEmpty()) || !draftChanged) nickname.setValue(confirmedNickname)
        pending = false
        refreshWidgets()
    }

    fun session(): UUID = session

    fun apiOpen(): Boolean = apiOpen

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)
        hoveredText = null
        super.render(graphics, mouseX, mouseY, partialTick)
        hoveredText?.let { graphics.renderTooltip(font, it, mouseX, mouseY) }
    }

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        val left = leftPos
        val top = topPos
        graphics.fill(left, top, left + imageWidth, top + imageHeight, 0xff111317.toInt())
        graphics.fill(left + 4, top + 4, left + imageWidth - 4, top + imageHeight - 4, 0xffeeeeee.toInt())
        graphics.fill(left + 4, top + 4, left + imageWidth - 4, top + 27, 0xff20242a.toInt())
        graphics.fill(left + 4, top + 27, left + imageWidth - 4, top + 29, ACCENT)
        graphics.drawString(font, title, left + 11, top + 11, 0xffffffff.toInt(), false)
        val status = if (!received) text("teutonlib.computer.scanning")
        else text("teutonlib.peripheral_link.status.${snapshot.getString("status")}")
        graphics.drawString(font, status, left + imageWidth - 11 - font.width(status), top + 11, statusColor(), false)

        if (apiOpen) renderApi(graphics, mouseX, mouseY)
        else renderOverview(graphics, mouseX, mouseY)

        val footer = if (error.isNotEmpty()) Component.literal(error) else text("teutonlib.peripheral_link.live")
        graphics.drawString(
            font,
            font.plainSubstrByWidth(footer.string, imageWidth - 22),
            left + 11,
            top + imageHeight - 14,
            if (error.isEmpty()) 0xff777777.toInt() else 0xffb33b3b.toInt(),
            false
        )
    }

    private fun renderOverview(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val listLeft = leftPos + 9
        val listTop = topPos + 34
        val listRight = leftPos + 210
        val listBottom = topPos + imageHeight - 22
        graphics.fill(listLeft, listTop, listRight, listBottom, 0xfff7f7f7.toInt())
        graphics.fill(leftPos + 214, listTop, leftPos + imageWidth - 8, listBottom, 0xfff7f7f7.toInt())
        graphics.drawString(font, text("teutonlib.peripheral_link.forwarding"), listLeft + 5, listTop + 5, ACCENT, false)
        graphics.drawString(
            font,
            text("teutonlib.peripheral_link.nickname"),
            leftPos + 218,
            topPos + 39,
            0xff1f1f1f.toInt(),
            false
        )

        val rows = rows()
        val rowTop = listTop + 20
        val capacity = max(1, (listBottom - rowTop) / 20)
        rowOffset = rowOffset.coerceIn(0, max(0, rows.size - capacity))
        graphics.enableScissor(listLeft, rowTop, listRight, listBottom)
        try {
            rows.drop(rowOffset).take(capacity).forEachIndexed { index, row ->
                val y = rowTop + index * 20
                val hovered = mouseX in listLeft until listRight && mouseY in y until y + 19
                if (hovered) graphics.fill(listLeft + 2, y, listRight - 2, y + 19, 0xffe3e3e3.toInt())
                graphics.drawString(font, row.first, listLeft + 5, y + 2, 0xff1f1f1f.toInt(), false)
                val value = row.second.ifEmpty { "—" }
                graphics.drawString(
                    font,
                    font.plainSubstrByWidth(value, listRight - listLeft - 12),
                    listLeft + 5,
                    y + 11,
                    0xff777777.toInt(),
                    false
                )
                if (hovered) hoveredText = Component.literal("${row.first.string}: $value")
            }
        } finally {
            graphics.disableScissor()
        }
        if (rows.size > capacity) renderScrollbar(graphics, listRight - 3, rowTop, capacity * 20, rowOffset, rows.size, capacity)
    }

    private fun rows(): List<Pair<Component, String>> {
        fun value(key: String) = snapshot.getString(key)
        val statusReason = if (received) text("teutonlib.peripheral_link.status.${value("status")}").string else ""
        fun available(value: String) = if (value.isNotEmpty()) value else if (received) "— ($statusReason)" else "—"
        val types = if (!snapshot.contains("types")) "" else
            snapshot.getList("types", Tag.TAG_STRING.toInt()).joinToString(", ") { it.asString }
        val additionalTypes = if (!snapshot.contains("additionalTypes")) "" else
            snapshot.getList("additionalTypes", Tag.TAG_STRING.toInt()).joinToString(", ") { it.asString }
        return listOf(
            text("teutonlib.peripheral_link.connection_status") to
                (if (received) text("teutonlib.peripheral_link.status.${value("status")}").string else ""),
            text("teutonlib.computer.address") to value("address"),
            text("teutonlib.peripheral_link.target_id") to value("targetId"),
            text("teutonlib.peripheral_link.target_block") to value("targetBlock"),
            text("teutonlib.peripheral_link.target_position") to value("targetPosition"),
            text("teutonlib.peripheral_link.computer_block") to value("computerBlock"),
            text("teutonlib.peripheral_link.computer_position") to value("computerPosition"),
            text("teutonlib.peripheral_link.target_dimension") to value("targetDimension"),
            text("teutonlib.peripheral_link.link_dimension") to value("linkDimension"),
            text("teutonlib.peripheral_link.link_position") to value("linkPosition"),
            text("teutonlib.peripheral_link.device_block") to available(value("deviceBlock")),
            text("teutonlib.peripheral_link.device_position") to value("devicePosition"),
            text("teutonlib.peripheral_link.attachment_side") to value("side").takeIf(String::isNotEmpty)
                ?.let { text("teutonlib.computer.$it").string }.orEmpty(),
            text("teutonlib.peripheral_link.primary_type") to available(value("primaryType")),
            text("teutonlib.peripheral_link.additional_types") to
                (additionalTypes.ifEmpty { if (value("primaryType").isNotEmpty()) "—" else available("") }),
            text("teutonlib.peripheral_link.types") to available(types)
        )
    }

    private fun renderApi(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val api = snapshot.getCompound("api").takeIf { snapshot.contains("api") }?.let(SourceApi::decode)
        val left = leftPos + 9
        val top = topPos + 34
        val right = leftPos + imageWidth - 9
        val bottom = topPos + imageHeight - 22
        graphics.fill(left, top, right, bottom, 0xfff7f7f7.toInt())
        graphics.fill(left + 3, top + 3, right - 3, top + 22, 0xffe3e3e3.toInt())
        graphics.drawString(
            font,
            api?.let { Component.literal(it.name) } ?: text("teutonlib.computer.api_unavailable"),
            left + 7,
            top + 9,
            ACCENT,
            false
        )
        graphics.drawString(font, "×", right - 15, top + 9, ACCENT, false)
        if (mouseX in right - 22 until right && mouseY in top until top + 27)
            hoveredText = text("teutonlib.computer.close_api")

        val lines = mutableListOf<String>()
        fun add(value: String) {
            font.split(Component.literal(value), right - left - 18).forEach { sequence ->
                val builder = StringBuilder()
                sequence.accept { _, _, codePoint -> builder.appendCodePoint(codePoint); true }
                lines.add(builder.toString())
            }
        }
        if (api == null) {
            add(text("teutonlib.computer.api_unavailable").string)
            if (received) add(text("teutonlib.peripheral_link.status.${snapshot.getString("status")}").string)
        }
        else {
            add(api.types.joinToString(", "))
            add(text("teutonlib.computer.${api.connection}").string)
            lines.add("")
            if (api.methods.isEmpty()) add(text("teutonlib.computer.api_unavailable").string)
            api.methods.forEach { method ->
                add(method.signature.ifEmpty { method.name })
                if (method.description.isNotEmpty()) add(method.description)
                lines.add("")
            }
        }
        val lineTop = top + 29
        val capacity = max(1, (bottom - lineTop - 3) / 11)
        apiOffset = apiOffset.coerceIn(0, max(0, lines.size - capacity))
        graphics.enableScissor(left, lineTop, right, bottom)
        try {
            lines.drop(apiOffset).take(capacity).forEachIndexed { index, line ->
                graphics.drawString(font, line, left + 7, lineTop + index * 11, 0xff1f1f1f.toInt(), false)
            }
        } finally {
            graphics.disableScissor()
        }
        if (lines.size > capacity) renderScrollbar(graphics, right - 3, lineTop, capacity * 11, apiOffset, lines.size, capacity)
    }

    private fun renderScrollbar(
        graphics: GuiGraphics,
        x: Int,
        y: Int,
        height: Int,
        offset: Int,
        size: Int,
        capacity: Int
    ) {
        val thumb = max(8, height * capacity / size)
        val thumbY = y + offset * (height - thumb) / max(1, size - capacity)
        graphics.fill(x, y, x + 1, y + height, 0xffd4d4d4.toInt())
        graphics.fill(x, thumbY, x + 1, thumbY + thumb, 0xff777777.toInt())
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (apiOpen && button == 0 && mouseX >= leftPos + imageWidth - 31 && mouseY in
            (topPos + 34).toDouble()..(topPos + 61).toDouble()
        ) {
            apiOpen = false
            refreshWidgets()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (mouseX !in leftPos.toDouble()..(leftPos + imageWidth).toDouble() ||
            mouseY !in topPos.toDouble()..(topPos + imageHeight).toDouble()
        ) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        if (apiOpen) apiOffset = max(0, apiOffset - sign(scrollY).toInt() * 3)
        else rowOffset = max(0, rowOffset - sign(scrollY).toInt())
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && apiOpen) {
            apiOpen = false
            refreshWidgets()
            return true
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && nickname.isFocused) {
            val next = nickname.value.trim()
            if (!pending && next != confirmedNickname) send("nickname", nickname.value)
            return true
        }
        if (nickname.isFocused && minecraft!!.options.keyInventory.matches(keyCode, scanCode)) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    private fun statusColor(): Int = when (snapshot.getString("status")) {
        "connected" -> 0xff55d68b.toInt()
        else -> 0xffffc36a.toInt()
    }

    companion object {
        private const val ACCENT = 0xff21a9a8.toInt()

        @JvmStatic
        fun accept(response: PeripheralLinkPayloads.Response) {
            (Minecraft.getInstance().screen as? PeripheralLinkScreen)?.receive(response)
        }

        private fun text(key: String): Component =
            if (I18n.exists(key)) Component.translatable(key) else Component.literal(key)
    }
}
