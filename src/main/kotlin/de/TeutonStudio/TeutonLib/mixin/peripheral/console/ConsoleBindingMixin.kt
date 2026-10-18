@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package de.TeutonStudio.TeutonLib.mixin.peripheral.console

import dan200.computercraft.api.lua.*
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.ILuaContext
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.MethodResult
import dan200.computercraft.api.peripheral.NotAttachedException
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkRegistry
import de.teutonstudio.ccaeroworks.computer.PeripheralBinding
import de.teutonstudio.ccaeroworks.computer.PeripheralNetworkNode
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(value = [PeripheralBinding::class], remap = false)
abstract class ConsoleBindingMixin {
    @Shadow
    private val node: PeripheralNetworkNode? = null

    @Shadow
    private val attached = false

    @Unique
    private var `teutonlib$closing` = false

    @Unique
    private var `teutonlib$invalidated` = false
    private fun `teutonlib$validLink`(): Boolean {
        if (!node.address.startsWith("cc_aeroworks:link_")) return true
        if (`teutonlib$invalidated`) return false
        var current = LinkRegistry.connection(node.address)
        if (current == null) {
            `teutonlib$invalidated` = true
            return false
        }
        if (current.link.level!!.server!!.isSameThread()) {
            LinkRegistry.validate()
            current = current.link.connection
        }
        val valid =
            current != null && current.anchor === node.desk.desk && (current.peripheral === node.target || current.peripheral.equals(
                node.target
            ))
        if (!valid) `teutonlib$invalidated` = true
        return valid
    }

    @Inject(method = ["checkAttached"], at = [At("HEAD")])
    private fun `teutonlib$check`(ci: CallbackInfo?) {
        if (!`teutonlib$closing` && !`teutonlib$validLink`()) throw NotAttachedException()
    }

    @Inject(method = ["call"], at = [At("HEAD")])
    @Throws(LuaException::class)
    private fun `teutonlib$call`(
        context: ILuaContext, name: String, arguments: IArguments,
        cir: CallbackInfoReturnable<MethodResult>
    ) {
        if (node.address.startsWith("cc_aeroworks:link_") && (!attached || !`teutonlib$validLink`())) throw LuaException(
            "Peripheral is no longer attached"
        )
    }

    @Inject(method = ["checkValid"], at = [At("RETURN")], cancellable = true)
    private fun `teutonlib$guard`(cir: CallbackInfoReturnable<Boolean>) {
        if (cir.returnValue && !`teutonlib$validLink`()) cir.setReturnValue(false)
    }

    @Inject(method = ["close"], at = [At("HEAD")])
    private fun `teutonlib$closing`(ci: CallbackInfo?) {
        `teutonlib$closing` = true
    }

    @Inject(method = ["close"], at = [At("RETURN")])
    private fun `teutonlib$closed`(ci: CallbackInfo?) {
        `teutonlib$closing` = false
    }
}
