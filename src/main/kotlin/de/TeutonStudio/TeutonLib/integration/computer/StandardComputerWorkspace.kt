package de.TeutonStudio.TeutonLib.integration.computer

import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity
import dan200.computercraft.shared.computer.core.ServerComputer
import de.TeutonStudio.TeutonLib.api.computer.*
import de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.entity.BlockEntity

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
        val nickname = runtime.sourceNickname(source.name).orEmpty()
        val technicalLabel = if (source.name.startsWith("cc_aeroworks:link_"))
            source.types.firstOrNull().orEmpty() + " · Link " + source.name.removePrefix("cc_aeroworks:link_").take(8) else source.name
        val blockName = (peripheral?.target as? BlockEntity)?.blockState?.block?.let(BuiltInRegistries.BLOCK::getKey)?.toString()
        val detail = listOfNotNull(source.name, blockName, source.devicePosition, source.connection,
            source.types.joinToString(", "), source.linkPosition).filter(String::isNotEmpty).joinToString(" · ")
        val children = listOfNotNull(
            Entry("peripheral:${source.name}/address", key("address"), source.name, "", "", false, false, emptyList(), emptyList(), CompoundTag()),
            blockName?.let { Entry("peripheral:${source.name}/block", key("block"), it, "", "", false, false, emptyList(), emptyList(), CompoundTag()) },
            source.devicePosition?.takeIf { it.isNotEmpty() }?.let { Entry("peripheral:${source.name}/position", key("position"), it, "", "", false, false, emptyList(), emptyList(), CompoundTag()) },
            Entry("peripheral:${source.name}/connection", key("connection"), key(source.connection), "", "", false, false, emptyList(), emptyList(), CompoundTag()),
            source.linkPosition?.takeIf { it.isNotEmpty() }?.let { Entry("peripheral:${source.name}/link", key("link_position"), it, "", "", false, false, emptyList(), emptyList(), CompoundTag()) }
        )
        val actions = buildList {
            add(action("source_nickname", "set_nickname", text = true))
            if (nickname.isNotEmpty()) add(action("clear_source_nickname", "clear_nickname"))
        }
        return Entry("peripheral:${source.name}", nickname.ifEmpty { technicalLabel }, detail, "", "", true, true, children, actions,
            CompoundTag().also {
                it.put("api", api.encode())
                it.putString("source", source.name)
                it.putString("nickname", nickname)
            })
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
        val runtime = runtime(computer)
        if (command.page == 1) {
            require(command.action == "source_nickname" || command.action == "clear_source_nickname") { "Unsupported source action" }
            val source = command.selected.removePrefix("peripheral:")
            require(command.selected == "peripheral:$source" && runtime.sourceViews().any { it.name == source }) {
                "Peripheral is no longer available"
            }
            runtime.setSourceNickname(source, if (command.action == "clear_source_nickname") "" else command.text)
            return
        }
        require(command.page == 2) { "Invalid workspace page" }
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
