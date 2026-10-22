package de.TeutonStudio.TeutonLib.mixin.simulated

import de.TeutonStudio.TeutonLib.integration.peripheral.heli.GimbalHeading
import dev.simulated_team.simulated.content.blocks.gimbal_sensor.GimbalSensorBlockEntity
import dev.simulated_team.simulated.data.SimLang
import java.util.Locale
import net.createmod.catnip.lang.Lang
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Overwrite

@Mixin(value = [GimbalSensorBlockEntity::class], remap = false)
abstract class GimbalSensorTooltipMixin {
    /** Adds the world-relative Y heading between Simulated's X and Z angle rows. */
    @Overwrite
    fun addToGoggleTooltip(tooltip: MutableList<Component>, isPlayerSneaking: Boolean): Boolean {
        val sensor = this as GimbalSensorBlockEntity
        val x = SimLang.text(String.format(Locale.ROOT, "%.2f", Math.toDegrees(sensor.xAngle)))
            .style(ChatFormatting.RED).component()
        val y = try {
            Component.literal(String.format(Locale.ROOT, "%.2f°", GimbalHeading.degrees(sensor)))
                .withStyle(ChatFormatting.GREEN)
        } catch (_: IllegalArgumentException) {
            Component.literal("—").withStyle(ChatFormatting.DARK_GRAY)
        }
        val z = SimLang.text(String.format(Locale.ROOT, "%.2f", Math.toDegrees(sensor.zAngle)))
            .style(ChatFormatting.BLUE).component()

        SimLang.blockName(sensor.blockState).forGoggles(tooltip, 1)
        SimLang.translate("gimbal_sensor.x_angle", x).style(ChatFormatting.GRAY).forGoggles(tooltip, 2)
        Lang.builder("teutonlib").translate("gimbal_sensor.y_angle", y)
            .style(ChatFormatting.GRAY).forGoggles(tooltip, 2)
        SimLang.translate("gimbal_sensor.z_angle", z).style(ChatFormatting.GRAY).forGoggles(tooltip, 2)
        return true
    }
}
