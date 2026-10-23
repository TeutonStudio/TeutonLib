package de.TeutonStudio.TeutonLib.peripheraltest

import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.api.computer.SourceApi
import de.TeutonStudio.TeutonLib.api.computer.SourceMethod
import de.TeutonStudio.TeutonLib.client.PeripheralLinkScreen
import de.TeutonStudio.TeutonLib.integration.peripheral.PeripheralLinkMenu
import de.TeutonStudio.TeutonLib.integration.peripheral.PeripheralLinkPayloads
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import org.lwjgl.glfw.GLFW

/** Opt-in render, focus, scrolling, Escape and resize verification for the standalone link UI. */
@EventBusSubscriber(modid = "teutonlib_peripheral_test", value = [Dist.CLIENT])
object PeripheralLinkUiVerification {
    private var stage = 0
    private var ticks = 0

    private fun snapshot() = CompoundTag().also { tag ->
        tag.putString("nickname", "Cargo inventory")
        tag.putString("status", "connected")
        tag.putString("address", "cc_aeroworks:link_00000000-0000-0000-0000-000000000001")
        tag.putString("targetId", "00000000-0000-0000-0000-000000000002")
        tag.putString("targetBlock", "computercraft:computer_advanced")
        tag.putString("targetPosition", "8, 200, 8")
        tag.putString("computerBlock", "computercraft:computer_advanced")
        tag.putString("computerPosition", "8, 200, 8")
        tag.putString("targetDimension", "minecraft:overworld")
        tag.putString("linkDimension", "minecraft:overworld")
        tag.putString("linkPosition", "12, 201, 8")
        tag.putString("deviceBlock", "minecraft:chest")
        tag.putString("devicePosition", "12, 200, 8")
        tag.putString("side", "down")
        tag.putString("primaryType", "inventory")
        tag.put("additionalTypes", ListTag())
        tag.put("types", ListTag().also { it.add(StringTag.valueOf("inventory")) })
        tag.put("api", SourceApi(
            tag.getString("address"), listOf("inventory"), "wireless",
            (0..35).map {
                SourceMethod("method$it", "method$it(slot: integer) -> table", "Forwarded method documentation.")
            },
            null, tag.getString("devicePosition"), tag.getString("linkPosition")
        ).encode())
    }

    private fun open(mc: Minecraft, scale: Int) {
        mc.options.guiScale().set(scale)
        mc.resizeDisplay()
        val inventory = Inventory(null)
        val menu = PeripheralLinkMenu(19, inventory, BlockPos.ZERO)
        val screen = PeripheralLinkScreen(menu, inventory, Component.literal("Peripheral Link"))
        mc.setScreen(screen)
        screen.receive(PeripheralLinkPayloads.Response(19, screen.session(), snapshot()))
        val field = screen.children().filterIsInstance<EditBox>().single()
        check(field.value == "Cargo inventory") { "Nickname not populated" }
        val button = screen.children().filterIsInstance<Button>().single {
            it.message.string == Component.translatable("teutonlib.peripheral_link.show_api").string
        }
        screen.mouseClicked((button.x + 4).toDouble(), (button.y + 4).toDouble(), 0)
        check(screen.apiOpen()) { "API overlay did not open" }
    }

    private fun finish(mc: Minecraft) {
        val screen = mc.screen as PeripheralLinkScreen
        screen.mouseScrolled((screen.width / 2).toDouble(), (screen.height / 2).toDouble(), 0.0, -4.0)
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0)
        check(!screen.apiOpen() && mc.screen === screen) { "API Escape closed the menu" }
        val field = screen.children().filterIsInstance<EditBox>().single()
        field.isFocused = true
        field.setValue("")
        check(screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0)) { "Empty nickname was not accepted as removal" }
        mc.resizeDisplay()
        check(screen.children().filterIsInstance<EditBox>().single().value == "Cargo inventory") {
            "Resize lost confirmed snapshot"
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun tick(event: ClientTickEvent.Post) {
        if (!java.lang.Boolean.getBoolean("teutonlib.peripheralLinkUiTests")) return
        val mc = Minecraft.getInstance()
        if (stage == 0) {
            if (mc.screen == null || mc.overlay != null) return
            mc.window.setWindowed(1280, 800)
            Files.deleteIfExists(Path.of("../../build/reports/smoke/peripheral-link-ui.txt"))
            stage = 1
        }
        if (++ticks < 35) return
        ticks = 0
        try {
            when (stage) {
                1, 3 -> open(mc, if (stage == 1) 2 else 3)
                2, 4 -> {
                    Screenshot.grab(
                        mc.gameDirectory,
                        "teutonlib-peripheral-link-ui-$stage.png",
                        mc.mainRenderTarget,
                        Consumer { _: Component? -> }
                    )
                    finish(mc)
                }
                else -> {
                    val report = Path.of("../../build/reports/smoke/peripheral-link-ui.txt")
                    Files.createDirectories(report.parent)
                    Files.writeString(report, "PASS: GUI scales 2/3; nickname, API open/scroll/Escape, resize state\n")
                    mc.stop()
                }
            }
            stage++
        } catch (failure: Throwable) {
            TeutonLib.LOGGER.error("Peripheral Link UI verification failed", failure)
            val report = Path.of("../../build/reports/smoke/peripheral-link-ui.txt")
            Files.createDirectories(report.parent)
            Files.writeString(report, "FAIL: ${failure.stackTraceToString()}\n")
            mc.stop()
            stage = 100
        }
    }
}
