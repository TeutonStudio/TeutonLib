package de.TeutonStudio.TeutonLib.api.computer

import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ComputerWorkspaceTest {
    @Test
    fun hierarchicalSnapshotRetainsStableIdsDetailsAndActions() {
        val action =
            de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Action("rename", "Rename", true, false, false)
        val child = de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Entry(
            "wire:uuid",
            "Engine",
            "12/15",
            "group-uuid",
            "wire:uuid",
            true,
            false,
            kotlin.collections.mutableListOf<de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Entry>(),
            java.util.List.of<de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Action>(action),
            CompoundTag()
        )
        val source = de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Snapshot(
            "Channels", "DbW", java.util.List.of<de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Entry>(
                de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Entry(
                    "section",
                    "Wires",
                    "",
                    "",
                    "",
                    false,
                    true,
                    java.util.List.of<de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Entry>(child),
                    kotlin.collections.mutableListOf<de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Action>(),
                    CompoundTag()
                )
            ), kotlin.collections.mutableListOf<de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Action>()
        )
        val decoded: de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Snapshot =
            de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Snapshot.Companion.decode(source.encode())
        assertEquals(source, decoded)
        assertThrows(java.lang.UnsupportedOperationException::class.java, { (decoded.entries as MutableList<ComputerWorkspace.Entry>).clear() })
    }

    @Test
    fun rejectsExcessiveTreeDepthBeforeRendering() {
        var entry = de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Entry(
            "leaf",
            "Leaf",
            "",
            "",
            "",
            false,
            false,
            kotlin.collections.mutableListOf<de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Entry>(),
            kotlin.collections.mutableListOf<de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Action>(),
            CompoundTag()
        )
        for (i in 0..9) entry = de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Entry(
            "node" + i,
            "Node",
            "",
            "",
            "",
            false,
            false,
            java.util.List.of<de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Entry>(entry),
            kotlin.collections.mutableListOf<de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Action>(),
            CompoundTag()
        )
        val encoded: CompoundTag = de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Snapshot(
            "",
            "",
            java.util.List.of<de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Entry>(entry),
            kotlin.collections.mutableListOf<de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Action>()
        ).encode()
        assertThrows(
            java.lang.IllegalArgumentException::class.java,
            { de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Snapshot.Companion.decode(encoded) })
    }
}
