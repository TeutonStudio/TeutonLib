@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package de.TeutonStudio.TeutonLib.mixin.peripheral.console

import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.lua.LuaException
import de.TeutonStudio.TeutonLib.integration.peripheral.*
import de.TeutonStudio.TeutonLib.integration.peripheral.ConsoleLinkDevices
import de.TeutonStudio.TeutonLib.integration.peripheral.ConsoleRuntimeAccess
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkDevice
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkLuaApi
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkRegistry
import de.teutonstudio.ccaeroworks.computer.ComputerConsoleAccess
import de.teutonstudio.ccaeroworks.computer.PeripheralBinding
import de.teutonstudio.ccaeroworks.computer.PeripheralNetworkNode
import de.teutonstudio.ccaeroworks.computer.PeripheralNetworkRuntime
import de.teutonstudio.ccaeroworks.computer.PeripheralNetworkRuntimes
import java.lang.AssertionError
import java.lang.Exception
import java.lang.RuntimeException
import java.util.LinkedHashMap
import kotlin.concurrent.Volatile
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(value = [PeripheralNetworkRuntime::class], remap = false)
abstract class ConsoleRuntimeMixin : ConsoleRuntimeAccess {
    @Shadow
    @Final
    private lateinit var bindings: LinkedHashMap<String, PeripheralBinding>

    @Shadow
    @Final
    private lateinit var system: IComputerSystem

    @Shadow
    @Final
    private val access: ComputerConsoleAccess? = null

    @Unique
    @Volatile
    private var `teutonlib$active` = false

    @Shadow
    private fun queueDetached(node: PeripheralNetworkNode?) {
        throw AssertionError()
    }

    @Shadow
    private fun invalidateGraph(queueEvents: Boolean) {
        throw AssertionError()
    }

    @Synchronized
    override fun `teutonlib$devices`(): MutableMap<String, LinkDevice> {
        return ConsoleLinkDevices.devices(java.util.Map.copyOf<String, PeripheralBinding>(bindings))
    }

    @Synchronized
    override fun `teutonlib$activate`() {
        `teutonlib$active` = true
        LinkRegistry.console(system, this)
        val owner = access!!.owner()
        if (owner != null) PeripheralNetworkRuntimes.register(owner, this as Any as PeripheralNetworkRuntime)
    }

    @Synchronized
    override fun `teutonlib$refresh`() {
        if (!`teutonlib$active`) return
        // A call can invalidate a handle between two graph refreshes. Retire that
        // attachment before the next graph is built, even if its device has returned.
        val iterator = bindings!!.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!entry.key!!.startsWith("cc_aeroworks:link_") || entry.value.checkValid()) continue
            entry.value.close()
            queueDetached(entry.value.node)
            iterator.remove()
        }
        try {
            (this as Any as PeripheralNetworkRuntime).graph(true)
        } catch (unavailable: Exception) {
            if (unavailable is LuaException) invalidateGraph(true)
            else throw RuntimeException(unavailable)
        }
    }

    @Inject(method = ["tick"], at = [At("HEAD")], cancellable = true)
    private fun `teutonlib$tick`(ci: CallbackInfo) {
        if (!`teutonlib$active`) ci.cancel()
    }

    @Inject(method = ["close"], at = [At("HEAD")])
    private fun `teutonlib$closed`(ci: CallbackInfo?) {
        `teutonlib$active` = false
        LinkRegistry.removeConsole(system)
    }

    @Inject(method = ["describePeripheral"], at = [At("RETURN")], cancellable = true)
    private fun `teutonlib$describe`(
        node: PeripheralNetworkNode,
        cir: CallbackInfoReturnable<MutableMap<String, Any?>>
    ) {
        val result = LinkedHashMap<String, Any?>(cir.returnValue)
        val connection = LinkRegistry.connection(node.address)
        if (connection != null) result.putAll(LinkLuaApi.Companion.describe(connection))
        else result.put("connectionType", "adjacent")
        cir.setReturnValue(result)
    }
}
