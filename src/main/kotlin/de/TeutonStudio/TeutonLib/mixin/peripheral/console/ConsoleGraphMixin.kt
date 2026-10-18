@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package de.TeutonStudio.TeutonLib.mixin.peripheral.console

import de.TeutonStudio.TeutonLib.integration.peripheral.LinkRegistry
import de.teutonstudio.ccaeroworks.computer.*
import de.teutonstudio.ccaeroworks.computer.ConsoleComputer
import de.teutonstudio.ccaeroworks.computer.DeskNetworkNode
import de.teutonstudio.ccaeroworks.computer.PeripheralNetworkBuilder
import de.teutonstudio.ccaeroworks.computer.PeripheralNetworkGraph
import de.teutonstudio.ccaeroworks.computer.PeripheralNetworkNode
import de.teutonstudio.ccaeroworks.computer.PeripheralTypeNames
import java.util.ArrayList
import java.util.LinkedHashSet
import net.minecraft.core.Direction
import net.minecraft.world.level.block.DirectionalBlock
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(value = [PeripheralNetworkBuilder::class], remap = false)
abstract class ConsoleGraphMixin {
    @Inject(method = ["build"], at = [At("RETURN")], cancellable = true)
    private fun `teutonlib$links`(owner: ConsoleComputer, cir: CallbackInfoReturnable<PeripheralNetworkGraph>) {
        LinkRegistry.validate()
        val graph = cir.returnValue
        val nodes: MutableList<PeripheralNetworkNode> = ArrayList<PeripheralNetworkNode>(graph.peripherals)
        for (connection in LinkRegistry.connections(owner.host)) {
            val desk =
                graph.desks.stream().filter { candidate: DeskNetworkNode? -> candidate.desk === connection.anchor }
                    .findFirst().orElse(null)
            if (desk == null) continue
            val types = LinkedHashSet<String>()
            types.add(connection.peripheral.type)
            types.addAll(connection.peripheral.additionalTypes)
            nodes.add(
                PeripheralNetworkNode(
                    desk, connection.link.devicePosition(),
                    connection.link.blockState
                        .getValue<net.minecraft.core.Direction>(net.minecraft.world.level.block.DirectionalBlock.FACING)
                        .opposite,
                    connection.name(), connection.peripheral, connection.peripheral.type, types,
                    PeripheralTypeNames.aliases(types)
                )
            )
        }
        cir.setReturnValue(
            graph.copy(
                graph.networkId,
                graph.revision,
                graph.state,
                graph.desks,
                nodes,
                graph.dimension
            )
        )
    }
}
