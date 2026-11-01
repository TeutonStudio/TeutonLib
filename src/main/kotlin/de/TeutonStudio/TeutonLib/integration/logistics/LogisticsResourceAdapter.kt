package de.TeutonStudio.TeutonLib.integration.logistics

import com.google.common.collect.Multimap
import com.simibubi.create.content.logistics.BigItemStack
import com.simibubi.create.content.logistics.packagerLink.RequestPromise
import com.yision.fluidlogistics.api.packager.PackageResources
import com.yision.fluidlogistics.content.logistics.packageResource.PackageResourceKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.util.IdentityHashMap
import java.util.function.Predicate
import ru.zznty.create_factory_abstractions.api.generic.key.GenericKey
import ru.zznty.create_factory_abstractions.api.generic.stack.GenericStack
import ru.zznty.create_factory_abstractions.generic.key.item.ItemKey
import ru.zznty.create_factory_abstractions.generic.support.BigGenericStack

/** All mutations use Factory Logistics' live collections. No second index or persistence format. */
object LogisticsResourceAdapter {
    private fun resource(key: GenericKey): PackageResourceKey? {
        if (key !is ItemKey || !PackageResources.isBootstrapped()) return null
        return PackageResources.keyOf(key.stack()).orElse(null)
    }

    private fun promised(promise: RequestPromise): GenericStack = BigGenericStack.of(promise.promisedStack).get()
    private fun clamp(count: Long): Int = count.coerceIn(0, BigItemStack.INF.toLong()).toInt()

    @JvmStatic fun countPromises(
        promises: MutableCollection<RequestPromise>, stack: GenericStack, expiry: Int, changed: Runnable
    ): Int? {
        val key = resource(stack.key()) ?: return null
        var count = 0L
        var dirty = false
        val iterator = promises.iterator()
        while (iterator.hasNext()) {
            val promise = iterator.next()
            val value = promised(promise)
            if (resource(value.key()) != key) continue
            if (expiry != -1 && promise.ticksExisted >= expiry) {
                iterator.remove()
                dirty = true
            } else count += value.amount().toLong()
        }
        if (dirty) changed.run()
        return clamp(count)
    }

    @JvmStatic fun clearPromises(
        promises: MutableCollection<RequestPromise>, stack: GenericStack, changed: Runnable
    ): Boolean {
        val key = resource(stack.key()) ?: return false
        var dirty = false
        val iterator = promises.iterator()
        while (iterator.hasNext()) {
            if (resource(promised(iterator.next()).key()) == key) {
                iterator.remove()
                dirty = true
            }
        }
        if (dirty) changed.run()
        return true
    }

    @JvmStatic fun receive(
        promises: MutableCollection<RequestPromise>, stack: GenericStack, changed: Runnable
    ): Boolean {
        val key = resource(stack.key()) ?: return false
        var remaining = stack.amount().coerceAtLeast(0)
        var dirty = false
        val iterator = promises.iterator()
        while (iterator.hasNext() && remaining > 0) {
            val promise = iterator.next()
            val big = BigGenericStack.of(promise.promisedStack)
            val value = big.get()
            if (resource(value.key()) != key) continue
            val consumed = minOf(remaining, value.amount().coerceAtLeast(0))
            remaining -= consumed
            big.setAmount(value.amount() - consumed)
            if (big.get().amount() <= 0) iterator.remove()
            dirty = dirty || consumed > 0
        }
        // Partial deliveries must also persist, even if no promise was removed.
        if (dirty) changed.run()
        return true
    }

    @JvmStatic fun addStock(stacks: Multimap<GenericKey, BigGenericStack>, stack: GenericStack): Boolean {
        if (resource(stack.key()) == null) return false
        if (stack.amount() <= 0) return true
        val itemKey = stack.key() as ItemKey
        val stored = GenericStack.wrap(itemKey.stack()).withAmount(clamp(stack.amount().toLong()))
        val bucket = stacks.get(stored.key())
        val iterator = bucket.iterator()
        while (iterator.hasNext()) {
            val existing = iterator.next()
            if (existing.get().canStack(stored)) {
                // BigItemStack's Factory Logistics hash includes the amount. Remove before changing it.
                iterator.remove()
                existing.setAmount(clamp(existing.get().amount().toLong() + stored.amount()))
                stacks.put(stored.key(), existing)
                return true
            }
        }
        bucket.add(BigGenericStack.of(stored))
        return true
    }

    @JvmStatic fun countStock(stacks: Multimap<GenericKey, BigGenericStack>, key: GenericKey): Int? {
        val requested = resource(key) ?: return null
        var count = 0L
        for (entry in stacks.values()) {
            if (resource(entry.get().key()) == requested) count += entry.get().amount().toLong()
        }
        return clamp(count)
    }

    /** erase removes a resource, including carrier variants with the same normalized identity. */
    @JvmStatic fun eraseStock(stacks: Multimap<GenericKey, BigGenericStack>, key: GenericKey): Boolean? {
        val requested = resource(key) ?: return null
        var dirty = false
        val iterator = stacks.values().iterator()
        while (iterator.hasNext()) {
            if (resource(iterator.next().get().key()) == requested) {
                iterator.remove()
                dirty = true
            }
        }
        return dirty
    }

    @JvmStatic fun totalStock(stacks: Multimap<GenericKey, BigGenericStack>): Int {
        var total = 0L
        for (entry in stacks.values()) total += entry.get().amount().toLong()
        return clamp(total)
    }

    @JvmStatic fun matchingStock(stacks: Multimap<GenericKey, BigGenericStack>, predicate: Predicate<ItemStack>): Int {
        var total = 0L
        for (entry in stacks.values()) {
            val value = entry.get()
            val key = value.key() as? ItemKey ?: continue
            if (predicate.test(key.stack())) total += value.amount().toLong()
        }
        return clamp(total)
    }

    /** Group all component variants under their carrier item without overwriting earlier buckets. */
    @JvmStatic fun itemMap(stacks: Multimap<GenericKey, BigGenericStack>): Map<Item, List<BigItemStack>> {
        val result = IdentityHashMap<Item, MutableList<BigItemStack>>()
        for (entry in stacks.values()) {
            val key = entry.get().key() as? ItemKey ?: continue
            result.getOrPut(key.stack().item) { ArrayList() }.add(entry.asStack())
        }
        return result
    }
}
