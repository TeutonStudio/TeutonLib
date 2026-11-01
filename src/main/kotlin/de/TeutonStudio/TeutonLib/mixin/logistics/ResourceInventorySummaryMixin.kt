package de.TeutonStudio.TeutonLib.mixin.logistics

import com.google.common.collect.Multimap
import com.simibubi.create.content.logistics.BigItemStack
import com.simibubi.create.content.logistics.packager.InventorySummary
import de.TeutonStudio.TeutonLib.integration.logistics.LogisticsResourceAdapter
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.gen.Accessor
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.util.function.Predicate
import ru.zznty.create_factory_abstractions.api.generic.key.GenericKey
import ru.zznty.create_factory_abstractions.api.generic.stack.GenericStack
import ru.zznty.create_factory_abstractions.generic.support.BigGenericStack

@Mixin(targets = ["com.simibubi.create.content.logistics.packager.InventorySummary"], priority = 900, remap = false)
abstract class ResourceInventorySummaryMixin {
    @Accessor("createFactoryLogistics\$stacks")
    protected abstract fun `teutonlib$stacks`(): Multimap<GenericKey, BigGenericStack>
    @Accessor("createFactoryLogistics\$stacksByCount")
    protected abstract fun `teutonlib$setStacksByCount`(value: List<BigGenericStack>?)
    @Shadow private var totalCount: Int = 0

    @Inject(method = ["add(Lru/zznty/create_factory_abstractions/api/generic/stack/GenericStack;)V"],
        at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$addResource`(stack: GenericStack, ci: CallbackInfo) {
        if (!LogisticsResourceAdapter.addStock(`teutonlib$stacks`(), stack)) return
        totalCount = LogisticsResourceAdapter.totalStock(`teutonlib$stacks`())
        `teutonlib$setStacksByCount`(null)
        ci.cancel()
    }

    @Inject(method = ["getCountOf(Lru/zznty/create_factory_abstractions/api/generic/key/GenericKey;)I"],
        at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$countResource`(key: GenericKey, cir: CallbackInfoReturnable<Int>) {
        val count = LogisticsResourceAdapter.countStock(`teutonlib$stacks`(), key)
        if (count != null) cir.returnValue = count
    }

    @Inject(method = ["erase(Lru/zznty/create_factory_abstractions/api/generic/key/GenericKey;)Z"],
        at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$eraseResource`(key: GenericKey, cir: CallbackInfoReturnable<Boolean>) {
        val erased = LogisticsResourceAdapter.eraseStock(`teutonlib$stacks`(), key) ?: return
        if (erased) {
            totalCount = LogisticsResourceAdapter.totalStock(`teutonlib$stacks`())
            `teutonlib$setStacksByCount`(null)
        }
        cir.returnValue = erased
    }

    @Inject(method = ["getItemMap()Ljava/util/Map;"], at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$allCarrierVariants`(cir: CallbackInfoReturnable<Map<Item, List<BigItemStack>>>) {
        cir.returnValue = LogisticsResourceAdapter.itemMap(`teutonlib$stacks`())
    }

    @Inject(method = ["getTotalOfMatching(Ljava/util/function/Predicate;)I"],
        at = [At("HEAD")], cancellable = true, require = 1)
    private fun `teutonlib$matchingStock`(predicate: Predicate<ItemStack>, cir: CallbackInfoReturnable<Int>) {
        cir.returnValue = LogisticsResourceAdapter.matchingStock(`teutonlib$stacks`(), predicate)
    }

    private companion object {
        // Create's codec encoder otherwise reads its now-unused items field directly.
        @JvmStatic
        @Inject(method = ["lambda\$static\$2(Lcom/simibubi/create/content/logistics/packager/InventorySummary;)Ljava/util/List;"],
            at = [At("HEAD")], cancellable = true, require = 1)
        private fun `teutonlib$encodeCarrierStock`(summary: InventorySummary, cir: CallbackInfoReturnable<List<BigItemStack>>) {
            cir.returnValue = summary.itemMap.values.flatten()
        }
    }
}
