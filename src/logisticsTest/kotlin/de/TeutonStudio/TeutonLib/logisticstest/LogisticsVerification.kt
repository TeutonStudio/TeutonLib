package de.TeutonStudio.TeutonLib.logisticstest

import com.simibubi.create.content.logistics.BigItemStack
import com.simibubi.create.content.logistics.packager.InventorySummary
import com.simibubi.create.content.logistics.packagerLink.RequestPromise
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue
import com.yision.fluidlogistics.api.packager.PackageResources
import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageResourceType
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.integration.LogisticsCompatibility
import net.minecraft.commands.Commands
import net.minecraft.core.component.DataComponents
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.PatchedDataComponentMap
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.fluids.FluidStack
import ru.zznty.create_factory_abstractions.api.generic.stack.GenericStack
import ru.zznty.create_factory_abstractions.generic.support.GenericInventorySummary
import ru.zznty.create_factory_abstractions.generic.support.GenericPromiseQueue
import ru.zznty.create_factory_logistics.logistics.generic.FluidKey

/** Calls the real transformed classes; run only in a disposable dedicated server. */
@Mod("teutonlib_logistics_test")
class LogisticsVerification {
    init { NeoForge.EVENT_BUS.register(this) }

    @SubscribeEvent fun commands(event: RegisterCommandsEvent) {
        event.dispatcher.register(Commands.literal("teutonlib_logistics_test").requires { it.hasPermission(4) }
            .executes { ctx ->
                try {
                    verify(ctx.source.level)
                    ctx.source.sendSuccess({ Component.literal("Logistics verification PASS") }, false)
                    1
                } catch (error: Throwable) {
                    TeutonLib.LOGGER.error("Logistics verification FAIL", error)
                    ctx.source.sendFailure(Component.literal("Logistics verification FAIL: $error"))
                    0
                }
            }.then(Commands.literal("restart").executes { ctx ->
                try {
                    verifyRestart(ctx.source.level)
                    ctx.source.sendSuccess({ Component.literal("Logistics restart PASS") }, false)
                    1
                } catch (error: Throwable) {
                    TeutonLib.LOGGER.error("Logistics restart FAIL", error)
                    ctx.source.sendFailure(Component.literal("Logistics restart FAIL: $error"))
                    0
                }
            }))
    }

    private fun water() = FluidPackageResourceType.createFluidKey(FluidStack(Fluids.WATER, 1))
    private fun lava() = FluidPackageResourceType.createFluidKey(FluidStack(Fluids.LAVA, 1))
    private fun variant() = water().also { it.set(DataComponents.CUSTOM_NAME, Component.literal("carrier variant")) }
    private fun componentFluid() = FluidPackageResourceType.createFluidKey(FluidStack(Fluids.WATER, 1).also {
        it.set(DataComponents.CUSTOM_NAME, Component.literal("distinct fluid components"))
    })
    private fun generic(queue: RequestPromiseQueue) = queue as GenericPromiseQueue
    private fun generic(summary: InventorySummary) = GenericInventorySummary.of(summary)
    private fun promise(stack: ItemStack, count: Int) = RequestPromise(BigItemStack(stack.copy(), count))

    private fun verify(level: ServerLevel) {
        check(LogisticsCompatibility.active())
        check(PackageResources.isBootstrapped())
        val water = water()
        val lava = lava()
        val variant = variant()
        val component = componentFluid()
        check(PackageResources.sameResource(water, variant))
        check(!PackageResources.sameResource(water, component))
        check(water.item === lava.item)
        var dirty = 0
        val queue = RequestPromiseQueue { dirty++ }
        queue.add(promise(water, 100))
        generic(queue).add(GenericStack.wrap(variant).withAmount(50))
        queue.add(promise(lava, 70))
        queue.add(promise(component, 30))
        check(queue.getTotalPromisedAndRemoveExpired(water, -1) == 150)
        check(generic(queue).getTotalPromisedAndRemoveExpired(GenericStack.wrap(variant), -1) == 150)
        check(queue.getTotalPromisedAndRemoveExpired(component, -1) == 30)
        val before = dirty
        queue.itemEnteredSystem(water, 20)
        check(dirty == before + 1) { "partial delivery did not mark queue dirty" }
        check(queue.getTotalPromisedAndRemoveExpired(water, -1) == 130)
        generic(queue).stackEnteredSystem(GenericStack.wrap(variant).withAmount(60))
        check(queue.getTotalPromisedAndRemoveExpired(water, -1) == 70)
        queue.itemEnteredSystem(water, -4)
        check(queue.getTotalPromisedAndRemoveExpired(water, -1) == 70)
        check(queue.getTotalPromisedAndRemoveExpired(lava, -1) == 70)
        queue.tick()
        val restored = RequestPromiseQueue.read(queue.write(level.registryAccess()), level.registryAccess()) { dirty++ }
        check(restored.getTotalPromisedAndRemoveExpired(variant, -1) == 70)
        check(restored.flatten(false).all { it.ticksExisted == 1 })
        check(restored.getTotalPromisedAndRemoveExpired(water, 1) == 0)
        check(restored.getTotalPromisedAndRemoveExpired(lava, -1) == 70)
        check(restored.getTotalPromisedAndRemoveExpired(component, -1) == 30)
        generic(restored).forceClear(GenericStack.wrap(lava))
        check(restored.getTotalPromisedAndRemoveExpired(lava, -1) == 0)
        restored.forceClear(component)
        check(restored.isEmpty)

        val large = RequestPromiseQueue {}
        large.add(promise(water, BigItemStack.INF))
        large.add(promise(variant, BigItemStack.INF))
        large.add(promise(water, BigItemStack.INF))
        check(large.getTotalPromisedAndRemoveExpired(water, -1) == BigItemStack.INF)

        val summary = InventorySummary()
        summary.add(water, 100)
        generic(summary).add(GenericStack.wrap(variant).withAmount(50))
        summary.add(lava, 70)
        summary.add(component, 30)
        check(summary.getCountOf(water) == 150)
        check(generic(summary).getCountOf(GenericStack.wrap(variant).key()) == 150)
        check(summary.getCountOf(component) == 30)
        check(summary.totalCount == 250)
        check(summary.getTotalOfMatching { PackageResources.sameResource(it, water) } == 150)
        check(summary.itemMap.getValue(water.item).sumOf { it.count } == 250)
        val ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE)
        val encodedSummary = InventorySummary.CODEC.encodeStart(ops, summary).orThrow
        val decodedSummary = InventorySummary.CODEC.parse(ops, encodedSummary).orThrow
        check(decodedSummary.getCountOf(water) == 150)
        check(decodedSummary.getCountOf(lava) == 70)
        check(decodedSummary.getCountOf(component) == 30)
        summary.stacksByCount // populate Factory Logistics' cache before mutation
        summary.add(water, 25)
        check(summary.getCountOf(water) == 175)
        check(summary.stacksByCount.sumOf { it.count } == 275)
        val copied = summary.copy()
        check(copied.getCountOf(water) == 175)
        val merged = InventorySummary()
        merged.add(copied)
        check(merged.getCountOf(water) == 175)
        check(generic(merged).erase(GenericStack.wrap(variant).key()))
        check(merged.getCountOf(water) == 0)
        check(merged.getCountOf(lava) == 70)
        check(merged.getCountOf(component) == 30)
        check(!merged.erase(water))
        check(merged.stacksByCount.sumOf { it.count } == 100)
        check(merged.totalCount == 100)
        summary.add(water, BigItemStack.INF)
        summary.add(water, BigItemStack.INF)
        summary.add(variant, BigItemStack.INF)
        check(summary.getCountOf(water) == BigItemStack.INF)
        check(summary.totalCount == BigItemStack.INF)
        check(summary.getTotalOfMatching { true } == BigItemStack.INF)
        check(summary.stacks.all { it.count in 0..BigItemStack.INF })

        // Native items and Factory Logistics' non-item generic resources still use its implementation.
        val iron = ItemStack(Items.IRON_INGOT)
        val native = GenericStack(FluidKey(Fluids.WATER.builtInRegistryHolder(), PatchedDataComponentMap(DataComponentMap.EMPTY)), 300)
        val ordinary = InventorySummary()
        ordinary.add(iron, 16)
        generic(ordinary).add(native)
        check(ordinary.getCountOf(iron) == 16)
        check(generic(ordinary).getCountOf(native.key()) == 300)
        val ordinaryQueue = RequestPromiseQueue {}
        ordinaryQueue.add(promise(iron, 12))
        generic(ordinaryQueue).add(native)
        ordinaryQueue.itemEnteredSystem(iron, 5)
        generic(ordinaryQueue).stackEnteredSystem(native.withAmount(50))
        check(ordinaryQueue.getTotalPromisedAndRemoveExpired(iron, -1) == 7)
        check(generic(ordinaryQueue).getTotalPromisedAndRemoveExpired(native, -1) == 250)

        // Persist the partially delivered queue to this disposable world, then probe after a process restart.
        val persisted = queue.write(level.registryAccess())
        persisted.put("test_summary", encodedSummary)
        NbtIo.writeCompressed(persisted, level.server.getWorldPath(LevelResource.ROOT)
            .resolve("teutonlib-logistics-test.nbt"))
    }

    private fun verifyRestart(level: ServerLevel) {
        val tag = NbtIo.readCompressed(level.server.getWorldPath(LevelResource.ROOT)
            .resolve("teutonlib-logistics-test.nbt"), NbtAccounter.unlimitedHeap())
        val queue = RequestPromiseQueue.read(tag, level.registryAccess()) {}
        check(queue.getTotalPromisedAndRemoveExpired(water(), -1) == 70)
        check(queue.getTotalPromisedAndRemoveExpired(lava(), -1) == 70)
        val ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE)
        val summary = InventorySummary.CODEC.parse(ops, tag.get("test_summary")!!).orThrow
        check(summary.getCountOf(water()) == 150)
        check(summary.getCountOf(lava()) == 70)
        check(summary.getCountOf(componentFluid()) == 30)
        check(summary.totalCount == 250)
        check(queue.getTotalPromisedAndRemoveExpired(componentFluid(), -1) == 30)
        check(queue.flatten(false).all { it.ticksExisted == 1 })
        queue.itemEnteredSystem(water(), 70)
        check(queue.getTotalPromisedAndRemoveExpired(water(), -1) == 0)
        check(queue.getTotalPromisedAndRemoveExpired(lava(), -1) == 70)
    }
}
