package de.TeutonStudio.TeutonLib.integration.computer

import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity
import dan200.computercraft.shared.computer.core.ServerComputer
import de.TeutonStudio.TeutonLib.api.computer.*
import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/** The ordinary computer uses the same tree, actions and API viewer as addon computers. */
object StandardComputerWorkspace : ComputerWorkspace {
    @JvmField val ID = ResourceLocation.fromNamespaceAndPath("teutonlib", "computer")
    private fun key(value: String) = "teutonlib.computer.$value"
    private fun action(id: String, label: String = id, text: Boolean = false, confirm: Boolean = false) =
        Action(id, key(label), text, confirm, false)
    private fun runtime(computer: ServerComputer) = checkNotNull(ComputerIntegration.runtime(computer)) { "Computer unavailable" }
    override fun owns(computer: ServerComputer): Boolean =
        ComputerIntegration.runtime(computer)?.let { it.host() is ChannelHost && !ComputerExtensions.externalChannels(it) } == true

    fun sourceEntry(runtime: ComputerRuntime, source: SourceView,
                    peripheral: dan200.computercraft.api.peripheral.IPeripheral? = runtime.peripherals()[source.name]): Entry {
        val api = SourceIntrospection.describe(runtime, source, peripheral)
        val detail = listOf(source.connection, source.types.joinToString(", ")).joinToString(" · ")
        val children = listOfNotNull(
            source.devicePosition?.takeIf { it.isNotEmpty() }?.let { Entry("peripheral:${source.name}/position", it, "", "", "", false, false, emptyList(), emptyList(), CompoundTag()) },
            source.linkPosition?.takeIf { it.isNotEmpty() }?.let { Entry("peripheral:${source.name}/link", it, "", "", "", false, false, emptyList(), emptyList(), CompoundTag()) }
        )
        val label = if (source.name.startsWith("cc_aeroworks:link_"))
            source.types.firstOrNull().orEmpty() + " · Link " + source.name.removePrefix("cc_aeroworks:link_").take(8) else source.name
        return Entry("peripheral:${source.name}", label, detail, "", "", false, true, children, emptyList(),
            CompoundTag().also { it.put("api", api.encode()) })
    }

    override fun snapshot(player: ServerPlayer, computer: ServerComputer, page: Int): Snapshot {
        val runtime = runtime(computer)
        if (page == 1) {
            val sources = runtime.sourceViews()
            val peripherals = runtime.peripherals()
            val entries = sources.groupBy { it.connection }.toSortedMap(compareBy<String> {
                listOf("local", "wired", "wireless").indexOf(it).let { index -> if (index < 0) 3 else index }
            }.thenBy { it }).map { (connection, sources) ->
                Entry("connection:$connection", key(connection), "", "", "", false, false,
                    sources.map { sourceEntry(runtime, it, peripherals[it.name]) }, emptyList(), CompoundTag())
            }
            return Snapshot(key("sources"), key("live"), entries, emptyList())
        }
        val entries = runtime.channelViews().groupBy { if (it.editable) it.direction else "extensions" }.map { (kind, channels) ->
            Entry("channels:$kind", key(if (kind == "extensions") kind else kind + "s"), "", "", "", false, false,
                channels.map { channel ->
                    val actions = if (!channel.editable) emptyList() else buildList {
                        add(action("rename", text = true)); add(action("remove", confirm = true))
                        add(action("public", if (channel.published) "private" else "public"))
                        if (channel.direction == "output") { add(action("minus")); add(action("plus")) }
                    }
                    Entry(channel.name, channel.name,
                        "${channel.value}/15 · ${channel.connections} · ${channel.side}", "", "", true, false,
                        emptyList(), actions, CompoundTag())
                }, emptyList(), CompoundTag())
        }
        return Snapshot(key("channels"), if (DbwAccess.available()) "DbW" else key("dbw_unavailable"),
            entries, listOf(action("create", text = true)))
    }

    override fun execute(player: ServerPlayer, computer: ServerComputer, command: Command) {
        require(command.page == 2) { "Sources are read-only" }
        val runtime = runtime(computer)
        if (command.action == "create") {
            require(command.target == "output") { "Only output channels can be created in the workspace" }
            runtime.createChannel(command.text, "output", "")
            return
        }
        val channel = runtime.channelViews().firstOrNull { it.name == command.selected && it.editable }
            ?: error("Channel no longer available")
        when (command.action) {
            "rename" -> runtime.rename(channel.name, command.text)
            "remove" -> runtime.remove(channel.name)
            "public" -> runtime.setPublic(channel.name, !channel.published)
            "minus" -> runtime.setValue(channel.name, (channel.value - 1).coerceIn(0, 15))
            "plus" -> runtime.setValue(channel.name, (channel.value + 1).coerceIn(0, 15))
            else -> error("Unknown channel action")
        }
    }
}
