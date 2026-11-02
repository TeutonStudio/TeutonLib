package de.TeutonStudio.TeutonLib.client.train

import dev.ryanhcode.sable.sublevel.ClientSubLevel
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData
import net.minecraft.client.Camera
import net.minecraft.client.PrioritizeChunkUpdates
import net.minecraft.client.renderer.chunk.RenderRegionCache
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/** A section-cached graphical level backed by the train's one live simulation.
 * The inherited GPU buffers contain model geometry, while block entities and
 * entity-animated blocks retain their live renderers (including Flywheel).
 * Keeping Sable's render-data ABI also lets IP reuse these exact buffers for
 * the clipped near/far halves, including its handoff mesh lifetime handling. */
class ContraptionLevel(sub: ClientSubLevel, dispatcher: SectionRenderDispatcher) :
    VanillaChunkedSubLevelRenderData(sub, dispatcher) {
    data class ModelBlock(val pos: BlockPos, val state: BlockState)
    data class AnimatedBlock(val pos: BlockPos, val state: BlockState, val blockEntity: BlockEntity?)
    data class Section(val models: List<ModelBlock>, val animated: List<AnimatedBlock>)

    private val sections = mutableMapOf<Long, Section>()
    private val dirty = mutableSetOf<Long>()
    private var captureAll = true
    var capturedSections = 0L
        private set

    /** Read-only snapshots; animations use the real block entities, never copies. */
    fun graphicalSections(): Map<Long, Section> = sections.toMap()

    override fun setDirty(x: Int, y: Int, z: Int, playerChanged: Boolean) {
        dirty += SectionPos.asLong(x, y, z)
        super.setDirty(x, y, z, playerChanged)
    }

    override fun rebuild() {
        captureAll = true
        super.rebuild()
    }

    override fun compileSections(updates: PrioritizeChunkUpdates, cache: RenderRegionCache, camera: Camera) {
        val present = allRenderSections().map { SectionPos.of(it.origin).asLong() }.toSet()
        sections.keys.retainAll(present)
        for (key in present) {
            if (!captureAll && key !in dirty && key in sections) continue
            val origin = SectionPos.of(key).origin()
            val models = mutableListOf<ModelBlock>()
            val animated = mutableListOf<AnimatedBlock>()
            val level = subLevel.level
            for (y in 0..15) for (z in 0..15) for (x in 0..15) {
                val pos = origin.offset(x, y, z)
                val state = level.getBlockState(pos)
                if (state.isAir) continue
                if (state.renderShape == RenderShape.MODEL || !state.fluidState.isEmpty) models += ModelBlock(pos, state)
                if (state.hasBlockEntity() || state.renderShape == RenderShape.ENTITYBLOCK_ANIMATED) {
                    animated += AnimatedBlock(pos, state, level.getBlockEntity(pos))
                }
            }
            sections[key] = Section(models.toList(), animated.toList())
            capturedSections++
        }
        captureAll = false
        dirty.clear()
        super.compileSections(updates, cache, camera)
    }

    override fun close() {
        sections.clear()
        dirty.clear()
        super.close()
    }
}
