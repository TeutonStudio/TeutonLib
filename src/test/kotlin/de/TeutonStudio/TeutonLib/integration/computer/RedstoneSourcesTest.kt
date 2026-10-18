package de.TeutonStudio.TeutonLib.integration.computer

import dan200.computercraft.core.computer.ComputerSide
import de.TeutonStudio.TeutonLib.api.computer.ChannelView
import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Snapshot
import de.TeutonStudio.TeutonLib.api.computer.SourceApi
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RedstoneSourcesTest {
    @Test fun automaticallyListsEverySideOnceWithoutConfiguredChannels() {
        val category = RedstoneSources.category({ if (it == ComputerSide.LEFT) 13 else 0 }, emptyList())
        val inputs = category.children.first().children
        assertEquals(6, inputs.size)
        assertEquals(ComputerSide.NAMES.toSet(), inputs.map { it.label.substringAfterLast('.') }.toSet())
        assertEquals(6, inputs.map { it.id }.toSet().size)
        val left = inputs.single { it.label.endsWith(".left") }
        assertEquals("13/15", left.detail)
        assertFalse(left.selectable)
        assertTrue(left.actions.isEmpty())
        val api = SourceApi.decode(left.metadata.getCompound("api"))
        assertTrue(api.methods.any { it.signature == "redstone.getAnalogInput(side: string) -> integer" })
        assertTrue(category.children.last().children.isEmpty())
    }

    @Test fun outputsContainOnlyUserChannelsAndRefreshTheirValues() {
        val channels = listOf(
            ChannelView("throttle", "output", "", false, 7, 0, "teutonlib", true),
            ChannelView("left", "output", "", false, 11, 0, "addon", false),
            ChannelView("old_input", "input", "left", false, 3, 0, "teutonlib", true)
        )
        val category = RedstoneSources.category({ 2 }, channels)
        val outputs = category.children.last().children
        assertEquals(listOf("left", "throttle"), outputs.map { it.label })
        assertEquals("11/15 · addon", outputs.first().detail)
        assertEquals(6, category.children.first().children.size)
        val changed = RedstoneSources.category({ 9 }, channels.map { it.copy(value = 15) })
        assertEquals("9/15", changed.children.first().children.first().detail)
        assertEquals("15/15 · teutonlib", changed.children.last().children.last().detail)
        val snapshot = Snapshot("sources", "live", listOf(category), emptyList())
        assertEquals(snapshot, Snapshot.decode(snapshot.encode()))
    }
}
