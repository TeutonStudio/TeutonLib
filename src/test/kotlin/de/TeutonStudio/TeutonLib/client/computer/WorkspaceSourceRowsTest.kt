package de.TeutonStudio.TeutonLib.client.computer

import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Entry
import de.TeutonStudio.TeutonLib.api.computer.SourceApi
import net.minecraft.nbt.CompoundTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkspaceSourceRowsTest {
    @Test
    fun gpsUsesPreciseChildDetailInTheVisibleRow() {
        val coordinate = "12.75, 64.50, -8.25 · 4 hosts · age 1s"
        val entry = source("ready", coordinate)

        assertEquals("$coordinate · ready", WorkspaceSourceRows.detail(entry))
    }

    @Test
    fun currentBackendsDoNotDuplicateTheCoordinate() {
        val coordinate = "12.75, 64.50, -8.25 · 4 hosts · age 1s"
        val entry = source("$coordinate · ready", coordinate)

        assertEquals("$coordinate · ready", WorkspaceSourceRows.detail(entry))
    }

    private fun source(detail: String, coordinate: String): Entry {
        val id = "source:gps:test"
        val child = Entry("$id/detail", coordinate, "", "", "", false, false, emptyList(), emptyList(), CompoundTag())
        val metadata = CompoundTag().also {
            it.put("api", SourceApi("GPS fix", listOf("gps"), "local", emptyList(), devicePosition = "13, 65, -8").encode())
        }
        return Entry(id, "GPS fix", detail, "", "", false, false, listOf(child), emptyList(), metadata)
    }
}
