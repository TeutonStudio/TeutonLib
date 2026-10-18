@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package de.TeutonStudio.TeutonLib.mixin.computer.console

import kotlin.math.*
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemElement
import dan200.computercraft.shared.peripheral.modem.wired.WiredModemPeripheral
import de.TeutonStudio.TeutonLib.api.computer.ComputerExtensions
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkRegistry
import de.teutonstudio.ccaeroworks.computer.*
import de.teutonstudio.ccaeroworks.computer.ConsoleComputer
import de.teutonstudio.ccaeroworks.computer.PeripheralNetworkBuilder
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceKind
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceSnapshot
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceSnapshotBuilder
import de.teutonstudio.ccaeroworks.computer.source.InformationSourceView
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.function.Consumer
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Compatibility projection only. Existing display/GPS/script owners remain in Aeroworks.  */
@Mixin(value = [InformationSourceSnapshotBuilder::class], remap = false)
abstract class ConsoleSourcesMixin {
    @Inject(method = ["build"], at = [At("RETURN")], cancellable = true)
    private fun `teutonlib$sources`(owner: ConsoleComputer, cir: CallbackInfoReturnable<InformationSourceSnapshot>) {
        if (ComputerExtensions.hasWorkspace(ResourceLocation.fromNamespaceAndPath("cc_aeroworks", "desk"))) return
        val before = cir.returnValue
        val sources = ArrayList<InformationSourceView>(before.sources)
        val kind = InformationSourceKind("teutonlib:peripherals", "PERIPHERALS", 25)
        for (node in PeripheralNetworkBuilder.build(owner).peripherals) {
            sources.removeIf { v: InformationSourceView? -> v!!.id == "storage:" + node.address }
            val link = LinkRegistry.connection(node.address)
            val details = (if (link == null) "local" else "wireless · link " + link.link.blockPos
                .toShortString()) + " · " + node.types.joinToString(", ")
            val pos = node.pos
            sources.add(
                InformationSourceView(
                    "peripheral:" + node.address,
                    kind,
                    node.address,
                    "connected",
                    pos.x,
                    pos.y,
                    pos.z,
                    node.side.getName(),
                    details.substring(0, min(details.length, 160))
                )
            )
            val modem = node.target as? WiredModemPeripheral
            val element = modem?.node?.element as? WiredModemElement
            if (element != null) element.remotePeripherals.forEach { (name: kotlin.String, p: IPeripheral?) ->
                val target: BlockPos? = (p!!.target as? BlockEntity)?.blockPos
                val displayPos = if (target == null) pos else target
                val d =
                    "wired · " + p.type + (if (target == null) " · via modem; device position unavailable" else "")
                sources.add(
                    InformationSourceView(
                        "peripheral:" + name,
                        kind,
                        name!!,
                        "connected",
                        displayPos.x,
                        displayPos.y,
                        displayPos.z,
                        "",
                        d.substring(0, min(d.length, 160))
                    )
                )
            }
        }
        val unique = LinkedHashMap<kotlin.String, InformationSourceView>()
        sources.forEach(Consumer { v: InformationSourceView? -> unique.putIfAbsent(v!!.id, v) })
        cir.setReturnValue(
            InformationSourceSnapshot(
                java.util.List.copyOf<InformationSourceView>(unique.values),
                before.displayScripts
            )
        )
    }
}
