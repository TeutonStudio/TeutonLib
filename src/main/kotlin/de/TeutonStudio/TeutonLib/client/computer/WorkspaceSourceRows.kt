package de.TeutonStudio.TeutonLib.client.computer

import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Entry
import de.TeutonStudio.TeutonLib.api.computer.SourceApi

internal object WorkspaceSourceRows {
    fun detail(entry: Entry): String {
        val current = entry.detail
        if (!entry.metadata.contains("api")) return current
        val api = SourceApi.decode(entry.metadata.getCompound("api"))
        if (api.types.none { it.equals("gps", ignoreCase = true) }) return current
        val coordinate = entry.children.firstOrNull { it.id == "${entry.id}/detail" }
            ?.label?.takeIf(String::isNotBlank)
            ?: api.devicePosition?.takeIf(String::isNotBlank)
            ?: return current
        return when {
            current.isBlank() -> coordinate
            current.contains(coordinate) -> current
            else -> "$coordinate · $current"
        }
    }
}
