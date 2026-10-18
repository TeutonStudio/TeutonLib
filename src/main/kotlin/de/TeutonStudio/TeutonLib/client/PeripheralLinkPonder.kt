package de.TeutonStudio.TeutonLib.client

import dan200.computercraft.shared.ModRegistry
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.integration.peripheral.PeripheralLinks
import net.createmod.catnip.math.Pointing
import net.createmod.ponder.api.registration.PonderPlugin
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper
import net.createmod.ponder.api.scene.PonderStoryBoard
import net.createmod.ponder.api.scene.SceneBuilder
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.createmod.ponder.foundation.PonderIndex
import net.minecraft.client.resources.language.I18n
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks

class PeripheralLinkPonder : PonderPlugin {
    override fun getModId(): String {
        return TeutonLib.Companion.MOD_ID
    }

    override fun registerScenes(helper: PonderSceneRegistrationHelper<ResourceLocation>) {
        helper.forComponents(ResourceLocation.fromNamespaceAndPath(TeutonLib.Companion.MOD_ID, "peripheral_link"))
            .addStoryBoard(
                "peripheral_link",
                PonderStoryBoard { scene: SceneBuilder, util: SceneBuildingUtil? -> Companion.scene(scene!!, util!!) })
    }

    companion object {
        @JvmStatic
        fun register() {
            PonderIndex.addPlugin(PeripheralLinkPonder())
        }

        @JvmStatic
        private fun scene(scene: SceneBuilder, util: SceneBuildingUtil) {
            scene.title("peripheral_link", I18n.get("teutonlib.ponder.peripheral_link.title"))
            scene.configureBasePlate(0, 0, 5)
            scene.showBasePlate()
            val computer = util.grid().at(1, 1, 2)
            val chest = util.grid().at(3, 1, 2)
            val link = chest.above()
            scene.world().setBlock(computer, ModRegistry.Blocks.COMPUTER_NORMAL.get().defaultBlockState(), false)
            scene.world().setBlock(chest, Blocks.CHEST.defaultBlockState(), false)
            scene.world().showSection(util.select().fromTo(1, 1, 2, 3, 1, 2), Direction.DOWN)
            scene.overlay().showControls(util.vector().topOf(computer), Pointing.DOWN, 60).rightClick()
                .withItem(ItemStack(PeripheralLinks.ITEM.get()))
            scene.overlay().showText(70).text(I18n.get("teutonlib.ponder.peripheral_link.select"))
                .pointAt(util.vector().centerOf(computer)).placeNearTarget()
            scene.idle(80)
            scene.world().setBlock(link, PeripheralLinks.BLOCK.get().defaultBlockState(), false)
            scene.world().showSection(util.select().position(link), Direction.DOWN)
            scene.overlay().showText(80).text(I18n.get("teutonlib.ponder.peripheral_link.place"))
                .pointAt(util.vector().centerOf(link)).placeNearTarget()
            scene.idle(90)
            scene.overlay().showText(90).text(I18n.get("teutonlib.ponder.peripheral_link.lua"))
                .pointAt(util.vector().centerOf(computer)).placeNearTarget()
            scene.idle(100)
            scene.overlay().showText(90).text(I18n.get("teutonlib.ponder.peripheral_link.range"))
                .pointAt(util.vector().centerOf(link)).placeNearTarget()
            scene.idle(100)
            scene.markAsFinished()
        }
    }
}
