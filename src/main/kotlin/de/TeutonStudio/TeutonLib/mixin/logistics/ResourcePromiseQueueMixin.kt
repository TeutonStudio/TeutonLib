package de.TeutonStudio.TeutonLib.mixin.logistics

import com.google.common.collect.Multimap
import com.simibubi.create.content.logistics.packagerLink.RequestPromise
import de.TeutonStudio.TeutonLib.integration.logistics.LogisticsResourceAdapter
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.gen.Accessor
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import ru.zznty.create_factory_abstractions.api.generic.key.GenericKey
import ru.zznty.create_factory_abstractions.api.generic.stack.GenericStack

// Run after Factory Logistics has added the storage and generic overloads.
@Mixin(targets = ["com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue"], priority = 900, remap = false)
abstract class ResourcePromiseQueueMixin {
    // Accessors resolve after all mixins have merged their fields; @Shadow resolves too early.
    @Accessor("createFactoryLogistics\$promises")
    protected abstract fun `teutonlib$promises`(): Multimap<GenericKey, RequestPromise>
    @Shadow private lateinit var onChanged: Runnable

    @Inject(method = ["getTotalPromisedAndRemoveExpired(Lru/zznty/create_factory_abstractions/api/generic/stack/GenericStack;I)I"],
        at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$countResource`(stack: GenericStack, expiry: Int, cir: CallbackInfoReturnable<Int>) {
        val count = LogisticsResourceAdapter.countPromises(`teutonlib$promises`().values(), stack, expiry, onChanged)
        if (count != null) cir.returnValue = count
    }

    @Inject(method = ["forceClear(Lru/zznty/create_factory_abstractions/api/generic/stack/GenericStack;)V"],
        at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$clearResource`(stack: GenericStack, ci: CallbackInfo) {
        if (LogisticsResourceAdapter.clearPromises(`teutonlib$promises`().values(), stack, onChanged)) ci.cancel()
    }

    @Inject(method = ["stackEnteredSystem(Lru/zznty/create_factory_abstractions/api/generic/stack/GenericStack;)V"],
        at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$receiveResource`(stack: GenericStack, ci: CallbackInfo) {
        if (LogisticsResourceAdapter.receive(`teutonlib$promises`().values(), stack, onChanged)) ci.cancel()
    }
}
