package de.TeutonStudio.TeutonLib.integration.computer

import dan200.computercraft.core.computer.ComputerSide
import dan200.computercraft.shared.computer.core.ServerComputer
import de.TeutonStudio.TeutonLib.api.computer.ChannelView
import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Entry
import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Snapshot
import de.TeutonStudio.TeutonLib.api.computer.SourceApi
import de.TeutonStudio.TeutonLib.api.computer.SourceMethod
import de.TeutonStudio.TeutonLib.mixin.computer.ServerComputerAccessor
import net.minecraft.nbt.CompoundTag

object RedstoneSources {
    private fun entry(id: String, label: String, detail: String = "", children: List<Entry> = emptyList(),
                      api: SourceApi? = null) = Entry(id, label, detail, "", "", false, false, children, emptyList(),
        CompoundTag().also { tag -> api?.let { tag.put("api", it.encode()) } })

    fun category(input: (ComputerSide) -> Int, channels: List<ChannelView>): Entry {
        val inputs = ComputerSide.entries.map { side ->
            entry("redstone:input:${side.name}", "teutonlib.computer.${side.getName()}", "${input(side)}/15",
                api = SourceApi("redstone · ${side.getName()}", listOf("redstone"), "local", listOf(
                    SourceMethod("getInput", "redstone.getInput(side: string) -> boolean", "side = \"${side.getName()}\""),
                    SourceMethod("getAnalogInput", "redstone.getAnalogInput(side: string) -> integer", "side = \"${side.getName()}\"")
                )))
        }
        val outputs = channels.filter { it.direction == "output" }.sortedBy { it.name }.map { channel ->
            entry("redstone:output:${channel.name}", channel.name, "${channel.value}/15 · ${channel.provider}")
        }
        return entry("source_kind:redstone", "teutonlib.computer.redstone", children = listOf(
            entry("redstone:inputs", "teutonlib.computer.inputs", children = inputs),
            entry("redstone:outputs", "teutonlib.computer.outputs", children = outputs)
        ))
    }

    fun append(snapshot: Snapshot, computer: ServerComputer): Snapshot {
        val redstone = (computer as ServerComputerAccessor).`teutonlib$computer`().redstone
        val channels = ComputerIntegration.runtime(computer)?.channelViews().orEmpty()
        return Snapshot(snapshot.title, snapshot.status,
            snapshot.entries + category(redstone::getInput, channels), snapshot.actions)
    }
}
