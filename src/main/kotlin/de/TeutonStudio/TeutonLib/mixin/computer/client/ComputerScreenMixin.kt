package de.TeutonStudio.TeutonLib.mixin.computer.client

import dan200.computercraft.client.gui.AbstractComputerScreen
import dan200.computercraft.client.gui.widgets.TerminalWidget
import dan200.computercraft.shared.computer.core.ComputerFamily
import dan200.computercraft.shared.computer.inventory.AbstractComputerMenu
import de.TeutonStudio.TeutonLib.ClientConfig
import de.TeutonStudio.TeutonLib.client.computer.WorkspacePanels
import de.TeutonStudio.TeutonLib.integration.computer.StandardComputerWorkspace
import de.TeutonStudio.TeutonLib.client.computer.ComputerSidebarTabs
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.BlockItem
import org.spongepowered.asm.mixin.*
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(value = [AbstractComputerScreen::class], remap = false)
abstract class ComputerScreenMixin protected constructor(
    menu: AbstractComputerMenu,
    inventory: Inventory,
    title: Component
) : AbstractContainerScreen<AbstractComputerMenu>(menu, inventory, title), WorkspacePanels.Host {
    @Shadow
    @JvmField
    protected var terminal: TerminalWidget? = null

    @Shadow
    @Final
    @JvmField
    protected val sidebarYOffset: Int = 0

    @Shadow
    @Final
    @JvmField
    protected val family: ComputerFamily? = null

    @Unique
    private var `teutonlib$panels`: WorkspacePanels? = null

    @Unique
    private var `teutonlib$addonWorkspace` = false

    override fun `teutonlib$setWorkspace`(workspace: WorkspacePanels) {
        `teutonlib$addonWorkspace` = true
        `teutonlib$panels` = workspace
    }

    @Unique
    private var `teutonlib$uiEnabled` = false
    override fun `teutonlib$workspace`(): WorkspacePanels? {
        return `teutonlib$panels`
    }

    @Inject(method = ["init()V"], at = [At("TAIL")])
    private fun `teutonlib$init`(ci: CallbackInfo?) {
        val deskComponent =
            BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.fromNamespaceAndPath("cc_aeroworks", "desk_id"))
        if (deskComponent != null && menu.displayStack.has(deskComponent)) return
        `teutonlib$addonWorkspace` = false
        `teutonlib$uiEnabled` = ClientConfig.COMPUTER_UI_ENABLED.get()
        if (!`teutonlib$uiEnabled`) {
            `teutonlib$panels` = null
            return
        }
        val block = menu.displayStack.item as? BlockItem ?: return
        if (terminal == null || (BuiltInRegistries.BLOCK.getKey(
                block.block
            ).namespace != "computercraft") || !BuiltInRegistries.BLOCK.getKey(block.block).path
                .startsWith("computer_")
        ) return
        val page = if (`teutonlib$panels` == null) 0 else `teutonlib$panels`!!.page()
        `teutonlib$panels` = WorkspacePanels(
            menu.containerId,
            StandardComputerWorkspace.ID,
            terminal!!.x,
            terminal!!.y,
            terminal!!.width,
            terminal!!.height,
            font,
            Consumer { w: AbstractWidget? -> addRenderableWidget<AbstractWidget>(w) },
            Runnable { setFocused(terminal) }, java.util.function.BiConsumer { _, _ -> })
        `teutonlib$panels`!!.family(family)
        for (i in 1..2) {
            val target = i
            addRenderableWidget<ComputerSidebarTabs.Tab>(
                ComputerSidebarTabs.tab(
                    leftPos, ComputerSidebarTabs.top(topPos, sidebarYOffset, i - 1), family!!,
                    if (i == 1) "sources" else "channels", BooleanSupplier { `teutonlib$panels`!!.page() == target },
                    Runnable { `teutonlib$show`(if (`teutonlib$panels`!!.page() == target) 0 else target) })
            )
        }
        `teutonlib$show`(page)
    }

    @Unique
    private fun `teutonlib$show`(page: Int) {
        `teutonlib$panels`!!.show(page)
        terminal!!.visible = page == 0
        terminal!!.active = page == 0
        if (page != 0) setFocused(null)
    }

    @Unique
    private fun `teutonlib$focus`() {
        if (`teutonlib$addonWorkspace` || `teutonlib$panels` == null) return
        val requested = `teutonlib$panels`!!.takeRequestedFocus()
        if (requested != null) {
            setFocused(requested)
            requested.setFocused(true)
        } else if (getFocused() is ComputerSidebarTabs.Tab) setFocused(if (`teutonlib$panels`!!.page() == 0) terminal else null)
    }

    @Inject(method = ["mouseClicked(DDI)Z"], at = [At("TAIL")])
    private fun `teutonlib$clickFocus`(x: Double, y: Double, button: Int, cir: CallbackInfoReturnable<Boolean>) {
        `teutonlib$focus`()
    }

    @Inject(method = ["keyPressed(III)Z"], at = [At("TAIL")])
    private fun `teutonlib$keyFocus`(key: Int, scan: Int, mods: Int, cir: CallbackInfoReturnable<Boolean>) {
        `teutonlib$focus`()
    }

    @Inject(method = ["containerTick()V"], at = [At("TAIL")])
    private fun `teutonlib$tick`(ci: CallbackInfo?) {
        if (`teutonlib$addonWorkspace`) return
        if (`teutonlib$uiEnabled` != ClientConfig.COMPUTER_UI_ENABLED.get()) {
            init(minecraft, width, height)
            return
        }
        if (`teutonlib$panels` != null) { `teutonlib$panels`!!.tick(); `teutonlib$focus`() }
    }

    @Inject(method = ["keyPressed(III)Z"], at = [At("HEAD")], cancellable = true)
    private fun `teutonlib$keys`(key: Int, scan: Int, mods: Int, cir: CallbackInfoReturnable<Boolean>) {
        if (`teutonlib$addonWorkspace` || `teutonlib$panels` == null || `teutonlib$panels`!!.page() == 0) return
        if (key == 256) {
            if (`teutonlib$panels`!!.closeSourceApi()) { cir.setReturnValue(true); return }
            `teutonlib$show`(0)
            cir.setReturnValue(true)
        } else if (getFocused() is EditBox && minecraft!!.options.keyInventory.matches(key, scan)) cir.setReturnValue(
            true
        )
    }
}
