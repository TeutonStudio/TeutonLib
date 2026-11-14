package de.TeutonStudio.TeutonLib.peripheraltest

import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.client.LinkAntennaClient
import de.TeutonStudio.TeutonLib.client.LinkAntennaScreen
import de.TeutonStudio.TeutonLib.client.PeripheralLinkScreen
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkAntennaPayloads
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.function.Consumer
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.network.PacketDistributor

/** Opt-in real client/server test: antenna hit, menus, both locators and expiry. */
@EventBusSubscriber(modid = "teutonlib_peripheral_test", value = [Dist.CLIENT])
object LinkAntennaClientVerification {
    private var titleTicks = 0
    private var connecting = false
    private var visualName = ""
    private var visualTicks = 0
    private var stage = 0
    private var ticks = 0
    private var stageStarted = 0L
    private val output get() = Path.of(System.getProperty("teutonlib.antennaClientReport"))
    private fun status(value: String) { Files.writeString(output.resolve("status.txt"), value) }
    private fun marker(): CompoundTag? = LinkAntennaClient::class.java.getDeclaredField("marker").let {
        it.isAccessible = true; it.get(null) as? CompoundTag
    }
    private fun capture(mc: Minecraft, name: String) {
        Screenshot.grab(output.toFile(), "$name.png", mc.mainRenderTarget, Consumer { _: Component? -> })
    }
    private fun button(screen: LinkAntennaScreen, key: String) = screen.children().filterIsInstance<Button>().single {
        it.message.string == Component.translatable(key).string
    }
    private fun side(screen: LinkAntennaScreen, side: Direction) = screen.children().filterIsInstance<Button>().single {
        it.message.string.endsWith(Component.translatable("teutonlib.antenna.side.${side.name.lowercase(java.util.Locale.ROOT)}").string)
    }
    private fun click(screen: LinkAntennaScreen, button: Button) {
        check(button.active && button.visible) { "Button unavailable: ${button.message.string}" }
        screen.mouseClicked((button.x + 4).toDouble(), (button.y + 4).toDouble(), 0)
    }
    private fun next(mc: Minecraft) { stage++; ticks = 0; stageStarted = mc.level!!.gameTime }
    @SubscribeEvent @JvmStatic fun tick(event: ClientTickEvent.Post) {
        if (!java.lang.Boolean.getBoolean("teutonlib.peripheralAntennaClientTests") || stage >= 100) return
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) {
            // Warm up the render pipeline before the server's configuration timeout starts.
            if (!connecting && mc.screen is net.minecraft.client.gui.screens.TitleScreen && mc.overlay == null && ++titleTicks >= 100) {
                connecting = true
                val address = System.getProperty("teutonlib.antennaClientServer")
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen!!, mc,
                    net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),
                    net.minecraft.client.multiplayer.ServerData("Antenna verification", address,
                        net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null)
            }
            return
        }
        if (stage == 0 && Files.exists(output.resolve("visual-request.txt"))) {
            try {
                val name = Files.readString(output.resolve("visual-request.txt")).trim()
                if (name != visualName) {
                    visualName = name; visualTicks = 0
                    mc.window.setWindowed(1280, 800)
                    mc.options.guiScale().set(2); mc.resizeDisplay(); mc.options.hideGui = true
                }
                if (++visualTicks == 40) {
                    val antennas = LinkAntennaClient::class.java.getDeclaredField("antennas").let { it.isAccessible = true; it.get(null) as List<*> }
                    check(antennas.size == 6) { "Visual fixture did not synchronize all six mount faces: $antennas" }
                    capture(mc, name)
                    Files.writeString(output.resolve("visual-done.txt"), name)
                }
            } catch (failure: Throwable) { fail(mc, failure) }
            return
        }
        if (stage == 0 && visualName.isNotEmpty()) mc.options.hideGui = false
        if (++ticks > 700) { fail(mc, IllegalStateException("Timed out at stage $stage")); return }
        try {
            when (stage) {
                0 -> {
                    if (!Files.exists(output.resolve("focus.txt"))) { status("READY"); return }
                    mc.window.setWindowed(1280, 800)
                    mc.options.guiScale().set(2); mc.resizeDisplay()
                    PacketDistributor.sendToServer(LinkAntennaPayloads.Command(CompoundTag().also {
                        it.putString("action", "open"); it.putUUID("target", UUID.randomUUID())
                        it.putLong("position", BlockPos(8, 200, 8).asLong())
                    }))
                    next(mc)
                }
                1 -> {
                    if (ticks < 25) return
                    if (ticks % 20 == 0) {
                        val antennas = LinkAntennaClient::class.java.getDeclaredField("antennas").let { it.isAccessible = true; it.get(null) }
                        Files.writeString(output.resolve("debug.txt"), "player=${mc.player!!.position()} look=${mc.player!!.lookAngle} antennas=$antennas screen=${mc.screen}")
                    }
                    if (ticks == 100) capture(mc, "debug-world")
                    check(mc.screen == null) { "Forged antenna identity opened a screen" }
                    val click = InputEvent.InteractionKeyMappingTriggered(1, mc.options.keyUse, InteractionHand.MAIN_HAND)
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(click)
                    if (!click.isCanceled) return
                    capture(mc, "antenna-world")
                    next(mc)
                }
                2 -> {
                    val screen = mc.screen as? LinkAntennaScreen ?: return
                    if (ticks < 30 || !button(screen, "teutonlib.antenna.mark_link").active) return
                    capture(mc, "antenna-menu-scale2")
                    mc.options.guiScale().set(3); mc.resizeDisplay()
                    next(mc)
                }
                3 -> {
                    if (ticks < 30) return
                    val screen = mc.screen as LinkAntennaScreen
                    capture(mc, "antenna-menu-scale3")
                    click(screen, button(screen, "teutonlib.antenna.sides"))
                    next(mc)
                }
                4 -> {
                    if (ticks < 15) return
                    val screen = mc.screen as LinkAntennaScreen
                    check(!side(screen, Direction.UP).active) { "UI allows disabling the last antenna face" }
                    click(screen, side(screen, Direction.EAST))
                    next(mc)
                }
                5 -> {
                    if (ticks < 25) return
                    val screen = mc.screen as LinkAntennaScreen
                    check(side(screen, Direction.UP).active && side(screen, Direction.EAST).active)
                    check(side(screen, Direction.EAST).message.string.startsWith("[x]")) { "Server did not enable second antenna" }
                    val antennas = LinkAntennaClient::class.java.getDeclaredField("antennas").let { it.isAccessible = true; it.get(null) as List<*> }
                    check(antennas.size == 2) { "Client did not render both configured faces: $antennas" }
                    capture(mc, "antenna-sides-multiple")
                    PacketDistributor.sendToServer(LinkAntennaPayloads.Command(CompoundTag().also {
                        it.putString("action", "sides"); it.putInt("menu", screen.menu.containerId)
                        it.putUUID("session", screen.session()); it.putInt("sides", 0)
                    }))
                    next(mc)
                }
                6 -> {
                    if (ticks < 25) return
                    val screen = mc.screen as LinkAntennaScreen
                    check(side(screen, Direction.UP).message.string.startsWith("[x]") && side(screen, Direction.EAST).message.string.startsWith("[x]")) {
                        "Forged empty selection cleared antenna faces"
                    }
                    click(screen, side(screen, Direction.UP))
                    next(mc)
                }
                7 -> {
                    if (ticks < 25) return
                    val screen = mc.screen as LinkAntennaScreen
                    check(!side(screen, Direction.EAST).active && side(screen, Direction.EAST).message.string.startsWith("[x]"))
                    check(side(screen, Direction.UP).active && side(screen, Direction.UP).message.string.startsWith("[ ]"))
                    val antennas = LinkAntennaClient::class.java.getDeclaredField("antennas").let { it.isAccessible = true; it.get(null) as List<*> }
                    check(antennas.size == 1) { "Client did not remove disabled antenna face" }
                    capture(mc, "antenna-sides-minimum-one")
                    click(screen, button(screen, "teutonlib.antenna.connections"))
                    click(screen, button(screen, "teutonlib.antenna.mark_link"))
                    next(mc)
                }
                8 -> {
                    if (mc.screen != null || marker() == null || ticks < 25) return
                    check(!marker()!!.getBoolean("antenna")) { "Antenna located wrong endpoint" }
                    capture(mc, "link-marker-through-wall")
                    status("LINK_FOCUS")
                    next(mc)
                }
                9 -> {
                    if (!Files.exists(output.resolve("link-focus.txt")) || ticks < 30) return
                    mc.gameMode!!.useItemOn(mc.player!!, InteractionHand.MAIN_HAND,
                        BlockHitResult(BlockPos(12, 201, 8).center, Direction.SOUTH, BlockPos(12, 201, 8), false))
                    next(mc)
                }
                10 -> {
                    val screen = mc.screen as? PeripheralLinkScreen ?: return
                    if (ticks < 30) return
                    val button = screen.children().filterIsInstance<Button>().firstOrNull {
                        it.message.string == Component.translatable("teutonlib.antenna.mark_anchor").string && it.active
                    } ?: return
                    capture(mc, "link-menu")
                    screen.mouseClicked((button.x + 4).toDouble(), (button.y + 4).toDouble(), 0)
                    next(mc)
                }
                11 -> {
                    if (mc.screen != null || marker()?.getBoolean("antenna") != true || ticks < 25) return
                    capture(mc, "antenna-marker-through-wall")
                    next(mc)
                }
                12 -> {
                    if (mc.level!!.gameTime - stageStarted < 310) return
                    check(marker() == null) { "Marker did not expire after 15 seconds" }
                    status("PASS: real antenna click, forged identity rejected, UI scales 2/3, multiple sides, last-side protection, forged empty mask rejected, both markers through wall, expiry")
                    stage = 100
                    mc.stop()
                }
            }
        } catch (failure: Throwable) { fail(mc, failure) }
    }
    private fun fail(mc: Minecraft, failure: Throwable) {
        TeutonLib.LOGGER.error("Peripheral antenna client verification failed", failure)
        Files.writeString(output.resolve("failure.txt"), failure.stackTraceToString())
        stage = 100; mc.stop()
    }
}
