package de.TeutonStudio.TeutonLib.mixin.computer.client

import dan200.computercraft.client.gui.AbstractComputerScreen
import dan200.computercraft.client.gui.widgets.TerminalWidget
import dan200.computercraft.shared.computer.core.ComputerFamily
import dan200.computercraft.shared.computer.inventory.AbstractComputerMenu
import de.TeutonStudio.TeutonLib.ClientConfig
import de.TeutonStudio.TeutonLib.api.computer.client.ComputerWorkspaceClientContext
import de.TeutonStudio.TeutonLib.api.computer.client.ComputerWorkspaceClientDescriptor
import de.TeutonStudio.TeutonLib.api.computer.client.ComputerWorkspaceClients
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
    private var `teutonlib$descriptor`: ComputerWorkspaceClientDescriptor? = null

    override fun `teutonlib$setWorkspace`(workspace: WorkspacePanels) {
        `teutonlib$panels` = workspace
    }

    @Unique
    private var `teutonlib$uiEnabled` = false
    override fun `teutonlib$workspace`(): WorkspacePanels? {
        return `teutonlib$panels`
    }

    @Inject(method = ["init()V"], at = [At("TAIL")])
    private fun `teutonlib$init`(ci: CallbackInfo?) {
        val descriptor = ComputerWorkspaceClients.resolve(menu)
        `teutonlib$descriptor` = descriptor
        `teutonlib$uiEnabled` = descriptor != null || ClientConfig.COMPUTER_UI_ENABLED.get()
        if (!`teutonlib$uiEnabled`) {
            `teutonlib$panels` = null
            return
        }
        if (descriptor == null) {
            val block = menu.displayStack.item as? BlockItem ?: return
            if ((BuiltInRegistries.BLOCK.getKey(block.block).namespace != "computercraft") ||
                !BuiltInRegistries.BLOCK.getKey(block.block).path.startsWith("computer_")) return
        }
        val terminal = terminal ?: return
        val page = if (`teutonlib$panels` == null) 0 else `teutonlib$panels`!!.page()
        val clientContext = ComputerWorkspaceClientContext(this, Runnable { `teutonlib$show`(0) })
        `teutonlib$panels` = WorkspacePanels(
            menu.containerId,
            descriptor?.workspace ?: StandardComputerWorkspace.ID,
            terminal.x,
            terminal.y,
            terminal.width,
            terminal.height,
            font,
            Consumer { w: AbstractWidget? -> addRenderableWidget<AbstractWidget>(w) },
            Runnable { setFocused(terminal) }, java.util.function.BiConsumer { action, entry ->
                descriptor?.handleAction(clientContext, action, entry)
            })
        `teutonlib$panels`!!.family(family)
        var index = 0
        addRenderableWidget(ComputerSidebarTabs.standardTab(
            leftPos, ComputerSidebarTabs.top(topPos, sidebarYOffset, index++), family!!,
            BooleanSupplier { `teutonlib$panels`!!.page() == 0 }, Runnable { `teutonlib$show`(0) }
        ))
        descriptor?.tabs(clientContext).orEmpty().forEach { extra ->
            addRenderableWidget(ComputerSidebarTabs.tab(
                leftPos, ComputerSidebarTabs.top(topPos, sidebarYOffset, index++), family!!,
                extra.label, extra.icon, extra.hoverIcon, BooleanSupplier { false },
                Runnable { extra.activate(clientContext) }
            ))
        }
        for ((target, name) in listOf(1 to "sources", 2 to "channels")) {
            addRenderableWidget(ComputerSidebarTabs.tab(
                leftPos, ComputerSidebarTabs.top(topPos, sidebarYOffset, index++), family!!, name,
                BooleanSupplier { `teutonlib$panels`!!.page() == target },
                Runnable { `teutonlib$show`(if (`teutonlib$panels`!!.page() == target) 0 else target) }
            ))
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
        if (`teutonlib$panels` == null) return
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
        val shouldEnable = `teutonlib$descriptor` != null || ClientConfig.COMPUTER_UI_ENABLED.get()
        if (`teutonlib$uiEnabled` != shouldEnable) {
            init(minecraft, width, height)
            return
        }
        if (`teutonlib$panels` != null) { `teutonlib$panels`!!.tick(); `teutonlib$focus`() }
    }

    @Inject(method = ["keyPressed(III)Z"], at = [At("HEAD")], cancellable = true)
    private fun `teutonlib$keys`(key: Int, scan: Int, mods: Int, cir: CallbackInfoReturnable<Boolean>) {
        if (`teutonlib$panels` == null || `teutonlib$panels`!!.page() == 0) return
        if (key == 256) {
            if (`teutonlib$panels`!!.closeSourceApi()) { cir.setReturnValue(true); return }
            `teutonlib$show`(0)
            cir.setReturnValue(true)
        } else if (getFocused() is EditBox && minecraft!!.options.keyInventory.matches(key, scan)) cir.setReturnValue(
            true
        )
    }
}
