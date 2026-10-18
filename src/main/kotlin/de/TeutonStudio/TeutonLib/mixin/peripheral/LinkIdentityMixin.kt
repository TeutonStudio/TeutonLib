package de.TeutonStudio.TeutonLib.mixin.peripheral

import de.TeutonStudio.TeutonLib.integration.peripheral.LinkIdentity
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkRegistry
import java.util.UUID
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(BlockEntity::class)
abstract class LinkIdentityMixin : LinkIdentity {
    @Unique
    private var `teutonlib$linkIdentity`: UUID? = null

    override fun `teutonlib$linkTarget`(): UUID? {
        return `teutonlib$linkIdentity`
    }

    override fun `teutonlib$linkTarget`(id: UUID?) {
        `teutonlib$linkIdentity` = id
        val self = this as Any as BlockEntity
        self.setChanged()
        LinkRegistry.registerAnchor(self)
    }

    @Inject(method = ["saveWithoutMetadata", "saveCustomOnly"], at = [At("RETURN")])
    private fun `teutonlib$save`(provider: HolderLookup.Provider, cir: CallbackInfoReturnable<CompoundTag>) {
        if (`teutonlib$linkIdentity` != null) cir.returnValue!!
            .putUUID("TeutonLibLinkTarget", `teutonlib$linkIdentity`)
    }

    @Inject(method = ["loadWithComponents", "loadCustomOnly"], at = [At("RETURN")])
    private fun `teutonlib$load`(tag: CompoundTag, provider: HolderLookup.Provider, ci: CallbackInfo?) {
        `teutonlib$linkIdentity` = if (tag.hasUUID("TeutonLibLinkTarget")) tag.getUUID("TeutonLibLinkTarget") else null
        LinkRegistry.registerAnchor(this as Any as BlockEntity)
    }

    @Inject(method = ["setLevel"], at = [At("RETURN")])
    private fun `teutonlib$level`(level: Level, ci: CallbackInfo?) {
        LinkRegistry.registerAnchor(this as Any as BlockEntity)
    }

    @Inject(method = ["setRemoved"], at = [At("HEAD")])
    private fun `teutonlib$removed`(ci: CallbackInfo?) {
        LinkRegistry.removeAnchor(this as Any as BlockEntity)
    }
}
