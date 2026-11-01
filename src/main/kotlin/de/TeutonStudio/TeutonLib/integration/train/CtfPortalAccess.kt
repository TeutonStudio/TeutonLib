package de.TeutonStudio.TeutonLib.integration.train

import com.simibubi.create.content.contraptions.AbstractContraptionEntity
import com.simibubi.create.content.trains.entity.Carriage
import createtrainfunctionality.LinkRegistry
import createtrainfunctionality.SubLevelLinks
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import java.lang.ref.WeakReference
import java.util.UUID

/** Private CTF 0.1.47 access is isolated here and fingerprinted before activation. */
object CtfPortalAccess {
    private val type = SubLevelLinks::class.java
    private val linksField = type.getDeclaredField("LINKS").also { it.isAccessible = true }
    private val carriageField = SubLevelLinks.Link::class.java.getDeclaredField("carriageRef").also { it.isAccessible = true }
    private val entityField = SubLevelLinks.Link::class.java.getDeclaredField("entityRef").also { it.isAccessible = true }
    private val republish = type.getDeclaredMethod("republish").also { it.isAccessible = true }
    private val notify = type.getDeclaredMethod("notifyTrackers", net.minecraft.world.entity.Entity::class.java,
        Boolean::class.javaPrimitiveType, BlockPos::class.java).also { it.isAccessible = true }
    private val unseal = type.getDeclaredMethod("unseal", UUID::class.java).also { it.isAccessible = true }
    private val forgetOurs = LinkRegistry::class.java.getDeclaredMethod("forgetOurs", ServerLevel::class.java,
        UUID::class.java).also { it.isAccessible = true }
    private val unregister = type.getDeclaredMethod("unregisterEntityCollider", ServerLevel::class.java,
        AbstractContraptionEntity::class.java).also { it.isAccessible = true }
    private val peekRegistry = LinkRegistry::class.java.getDeclaredMethod("peek", ServerLevel::class.java).also { it.isAccessible = true }

    @Suppress("UNCHECKED_CAST")
    private fun links() = linksField.get(null) as MutableMap<UUID, SubLevelLinks.Link>

    @Suppress("UNCHECKED_CAST")
    fun carriage(link: SubLevelLinks.Link): Carriage? =
        (carriageField.get(link) as WeakReference<Carriage>?)?.get()

    @Suppress("UNCHECKED_CAST")
    fun entity(link: SubLevelLinks.Link): AbstractContraptionEntity? =
        (entityField.get(link) as WeakReference<AbstractContraptionEntity>).get()

    fun unregister(level: ServerLevel, entity: AbstractContraptionEntity) { unregister.invoke(null, level, entity) }
    fun unseal(entityId: UUID) { unseal.invoke(null, entityId) }
    fun entries(level: ServerLevel): Collection<LinkRegistry.Entry> =
        (peekRegistry.invoke(null, level) as LinkRegistry?)?.entries().orEmpty()
    fun clearChunkCache(level: ServerLevel) {
        (level.chunkSource as createtrainfunctionality.mixin.ServerChunkCacheInvoker).`ctf$clearCache`()
    }

    fun detachLink(link: SubLevelLinks.Link) {
        check(links().remove(link.entityId) === link)
        republish.invoke(null)
        SubLevelLinks.forgetRemembered(link.entityId)
        entity(link)?.let { notify.invoke(null, it, false, link.plotOrigin) }
        unseal(link.entityId)
    }

    fun forgetSource(level: ServerLevel, id: UUID) {
        LinkRegistry.forget(level, id)
        forgetOurs.invoke(null, level, id)
    }

    fun restoreLink(link: SubLevelLinks.Link) {
        links()[link.entityId] = link
        republish.invoke(null)
        entity(link)?.let { notify.invoke(null, it, true, link.plotOrigin) }
    }
}
