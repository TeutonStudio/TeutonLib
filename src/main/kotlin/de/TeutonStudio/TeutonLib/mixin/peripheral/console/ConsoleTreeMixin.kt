@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package de.TeutonStudio.TeutonLib.mixin.peripheral.console

import de.teutonstudio.ccaeroworks.computer.*
import de.teutonstudio.ccaeroworks.computer.PeripheralNetworkRuntime
import java.util.LinkedHashMap
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** Retains the tree's side keys for adjacent devices; wireless siblings use their stable names.  */
@Mixin(targets = ["de.teutonstudio.ccaeroworks.computer.PeripheralTreeKt"], remap = false)
abstract class ConsoleTreeMixin {
    private companion object {
        @Inject(method = ["describeTree"], at = [At("RETURN")], cancellable = true)
        @JvmStatic
        private fun `teutonlib$tree`(
            runtime: PeripheralNetworkRuntime,
            cir: CallbackInfoReturnable<MutableMap<String, Any?>>
        ) {
            val graph = runtime.graph(false)
            val result = LinkedHashMap<String, Any?>()
            for (desk in graph.desks) {
                val children = LinkedHashMap<String, Any?>()
                for (node in graph.peripherals) {
                    if (node.desk.address != desk.address) continue
                    val info = LinkedHashMap<String, Any?>(runtime.describePeripheral(node))
                    info.put("x", node.pos.x)
                    info.put("y", node.pos.y)
                    info.put("z", node.pos.z)
                    info.put("handle", runtime.peripheralHandle(node))
                    children.put(
                        if (node.address.startsWith("cc_aeroworks:link_")) node.address else node.side.name.lowercase(),
                        info
                    )
                }
                val info = LinkedHashMap<String, Any?>(runtime.describeDesk(desk.address))
                info.put("handle", runtime.deskHandle(desk))
                info.put("peripherals", children)
                result.put(desk.address, info)
            }
            cir.setReturnValue(result)
        }
    }
}
