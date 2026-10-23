package de.TeutonStudio.TeutonLib.integration.computer

import kotlin.math.*
import dan200.computercraft.api.component.ComputerComponents
import dan200.computercraft.api.lua.*
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.IComputerSystem
import dan200.computercraft.api.lua.ILuaAPI
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.lua.MethodResult
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.pocket.IPocketAccess
import dan200.computercraft.api.turtle.ITurtleAccess
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity
import de.TeutonStudio.TeutonLib.api.computer.ChannelView
import de.TeutonStudio.TeutonLib.api.computer.ComputerContext
import de.TeutonStudio.TeutonLib.api.computer.ComputerExtensions
import de.TeutonStudio.TeutonLib.api.computer.SourceView
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkRegistry
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.util.ArrayList
import java.util.LinkedHashSet
import java.util.TreeMap
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.concurrent.Volatile
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.fml.ModList

/** API-factory instance; all Lua entry points run on the server thread.  */
class ComputerRuntime(private val system: IComputerSystem) : ILuaAPI, ComputerContext {
    @Volatile
    private var running = false
    private val portable: Boolean

    init {
        portable =
            system.getComponent<IPocketAccess>(ComputerComponents.POCKET) != null || system.getComponent<ITurtleAccess>(
                ComputerComponents.TURTLE
            ) != null
    }

    fun system(): IComputerSystem {
        return system
    }

    override fun getNames(): Array<String> {
        return arrayOf<String>("teutonlib_channels")
    }

    override fun getModuleName(): String {
        return "teutonlib.channels"
    }

    override fun startup() {
        running = true
        if (!portable) ComputerIntegration.start(this)
    }

    override fun shutdown() {
        running = false
        ComputerIntegration.stop(this)
    }

    override fun computerId(): Int {
        return system.id
    }

    override fun level(): ServerLevel {
        return system.level
    }

    override fun position(): BlockPos {
        return system.position
    }

    override fun peripherals(): MutableMap<String, IPeripheral> {
        val result = java.util.LinkedHashMap(system.availablePeripherals)
        ComputerExtensions.peripheralSources(this).forEach { (name, source) -> result.putIfAbsent(name, source.peripheral) }
        return java.util.Map.copyOf<String, IPeripheral>(result)
    }

    override fun queueEvent(event: String, vararg arguments: Any?) {
        system.queueEvent(event, *arguments)
    }

    override fun host(): BlockEntity {
        check(!(!running || portable)) { "Computer is not available" }
        val chunk = level().chunkSource.getChunkNow(position().x shr 4, position().z shr 4)
        var host: BlockEntity? = (if (chunk == null) null else chunk.blockEntities.get(position()))!!
        if (host !is ComputerBlockEntity) host = ComputerExtensions.resolveHost(system)
        check(!(host == null || host.isRemoved() || host.level !== level())) { "Computer block is not loaded" }
        check(
            !(host is ComputerBlockEntity && (host.serverComputer == null
                    || host.serverComputer!!.mainThreadMonitor !== system.mainThreadMonitor))
        ) { "Computer instance is no longer available" }
        return host
    }

    fun bank(): ChannelBank? {
        check(!ComputerExtensions.externalChannels(this)) { "Use the owning addon channel API" }
        return (host() as ChannelHost).`teutonlib$channels`()
    }

    fun sourceNickname(name: String): String? {
        val host = host()
        val connection = LinkRegistry.connection(name)?.takeIf { it.owner === host }
        if (connection != null) return connection.link.nickname().takeIf(String::isNotEmpty)
        return (host as SourceNicknameHost).`teutonlib$sourceNicknames`().get(name)
    }

    fun setSourceNickname(name: String, nickname: String) {
        require(sourceViews().any { it.name == name }) { "Peripheral is no longer available" }
        val host = host()
        val nicknames = (host as SourceNicknameHost).`teutonlib$sourceNicknames`()
        val connection = LinkRegistry.connection(name)?.takeIf { it.owner === host }
        if (connection == null) nicknames.set(name, nickname)
        else {
            connection.link.nickname(nickname)
            nicknames.set(name, "")
        }
        host.setChanged()
        host.level!!.sendBlockUpdated(host.blockPos, host.blockState, host.blockState, 3)
    }

    private fun changed() {
        val host = host()
        host.setChanged()
        host.level!!.sendBlockUpdated(host.blockPos, host.blockState, host.blockState, 3)
        queueEvent("teutonlib_channels_changed")
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun create(args: IArguments) {
        val name = args.getString(0)
        val direction = args.getString(1)
        val side: String = args.optString(2, "")!!
        createChannel(name, direction, side)
    }

    @Throws(LuaException::class)
    fun createChannel(name: String, direction: String, side: String?) {
        lua<Any?>(Supplier {
            bank()!!.create(name, direction, side)
            if (direction == "output") DbwAccess.set(host(), name, 0)
            changed()
            null
        })
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun remove(name: String) {
        lua<Any?>(Supplier {
            val e = bank()!!.get(name)
            if (e.direction == "output") DbwAccess.remove(host(), name)
            bank()!!.remove(name)
            changed()
            null
        })
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun rename(oldName: String, newName: String) {
        lua<Any?>(Supplier {
            ChannelBank.Companion.name(newName)
            val e = bank()!!.get(oldName)
            require(
                !(oldName != newName && bank()!!.entries().stream()
                    .anyMatch { v: ChannelBank.Entry? -> v!!.name == newName })
            ) { "Channel already exists" }
            if (e.direction == "output") DbwAccess.rename(host(), oldName, newName, e.value)
            bank()!!.rename(oldName, newName)
            changed()
            null
        })
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun setPublic(name: String, published: Boolean) {
        lua<Any?>(Supplier {
            bank()!!.published(name, published)
            changed()
            null
        })
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun set(args: IArguments) {
        val value = args.getDouble(1)
        if (!java.lang.Double.isFinite(value) || value != floor(value) || value < 0 || value > 15) throw LuaException("Signal must be an integer from 0 to 15")
        setValue(args.getString(0), value.toInt())
    }

    @Throws(LuaException::class)
    fun setValue(name: String, value: Int) {
        lua<Any?>(Supplier {
            bank()!!.set(name, value)
            DbwAccess.set(host(), name, value)
            changed()
            null
        })
    }

    private fun modem(): Boolean {
        return peripherals()!!.values.stream()
            .anyMatch { p: IPeripheral? -> p!!.type == "modem" || p.additionalTypes.contains("modem") }
    }

    private fun remote(id: Int): ComputerRuntime {
        host()
        check(modem()) { "This computer needs a connected modem" }
        val matches = ComputerIntegration.active().stream()
            .filter { r: ComputerRuntime -> r.computerId() == id && r.level() === level() }
            .filter { r: ComputerRuntime ->
                try {
                    r.host()
                    return@filter true
                } catch (e: IllegalStateException) {
                    return@filter false
                }
            }.toList()
        check(!matches.isEmpty()) { "Target computer is not available in this dimension" }
        check(matches.size == 1) { "Ambiguous computer ID" }
        val target: ComputerRuntime = matches.first()
        check(target.modem()) { "Target computer needs a connected modem" }
        return target
    }

    fun channelViews(): MutableList<ChannelView> {
        val host = host()
        val views = TreeMap<String, ChannelView>()
        for (e in if (ComputerExtensions.externalChannels(this)) mutableListOf<ChannelBank.Entry>() else bank()!!.entries()) views.put(
            e.name, ChannelView(
                e.name, e.direction, e.side, e.published,
                if (e.direction == "input") DbwAccess.read(host, e.side) else e.value,
                if (e.direction == "output") DbwAccess.connections(host, e.name) else 0, "teutonlib", true
            )
        )
        ComputerExtensions.channels(
            this,
            Consumer { v: ChannelView? ->
                views.putIfAbsent(
                    v!!.name,
                    ChannelView(
                        v.name,
                        v.direction,
                        v.side,
                        v.published,
                        v.value,
                        v.connections,
                        v.provider,
                        false
                    )
                )
            })
        return java.util.List.copyOf<ChannelView>(views.values)
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun list(args: IArguments): MethodResult? {
        val remote = args.count() > 0 && args.get(0) != null
        val id = if (remote) args.getInt(0) else computerId()
        return lua<MethodResult>(Supplier {
            val views = (if (remote) remote(id) else this).channelViews()
            val visible = views.stream().filter { c: ChannelView? -> !remote || c!!.published }
                .map<MutableMap<String, Any?>> { c: ChannelView? -> Companion.describe(c!!) }.toList()
            MethodResult.of(
                visible,
                if (remote) views.stream().filter { c: ChannelView? -> !c!!.published }.count() else 0L
            )
        })
    }

    @LuaFunction(mainThread = true)
    @Throws(LuaException::class)
    fun read(args: IArguments): Int {
        val remote = args.count() > 1
        val id = if (remote) args.getInt(0) else computerId()
        val name = args.getString(if (remote) 1 else 0)
        return ComputerRuntime.Companion.lua<Int>(java.util.function.Supplier {
            (if (remote) remote(id) else this).channelValue(
                name
            )
        })!!
    }

    private fun channelValue(name: String?): Int {
        val entry = if (ComputerExtensions.externalChannels(this)) null else bank()!!.find(name)
        if (entry != null) return if (entry.direction == "input") DbwAccess.read(host(), entry.side) else entry.value
        val matches = ArrayList<ChannelView>()
        ComputerExtensions.channels(this, Consumer { c: ChannelView? -> if (c!!.name == name) matches.add(c) })
        require(!matches.isEmpty()) { "Unknown channel '" + name + "'" }
        return matches.first()!!.value
    }

    fun sourceViews(): MutableList<SourceView> {
        val result = TreeMap<String, SourceView>()
        val addonSources = ComputerExtensions.peripheralSources(this)
        for (e in peripherals()!!.entries) {
            val p = e.value
            val types = LinkedHashSet<String>()
            types.add(p.type)
            types.addAll(p.additionalTypes)
            var connection = if (mutableSetOf<String>(
                    "front",
                    "back",
                    "left",
                    "right",
                    "top",
                    "bottom"
                ).contains(e.key)
            ) "local" else "wired"
            var device = (p.target as? BlockEntity)?.let { position(it) } ?: ""
            var linkPosition = ""
            if (ModList.get().isLoaded("create")) {
                val link = LinkRegistry.connection(e.key)
                if (link != null) {
                    connection = "wireless"
                    device = link.link.devicePosition().toShortString()
                    linkPosition = position(link.link)
                }
            }
            addonSources[e.key]?.let { source ->
                connection = source.connection
                device = source.devicePosition ?: device
                linkPosition = source.linkPosition ?: linkPosition
            }
            result.put(e.key, SourceView(e.key, java.util.List.copyOf<String>(types), connection, device, linkPosition))
        }
        ComputerExtensions.sources(this, Consumer { v: SourceView? -> result.putIfAbsent(v!!.name, v) })
        return java.util.List.copyOf<SourceView>(result.values)
    }

    companion object {
        @Throws(LuaException::class)
        @JvmStatic
        private fun <T> lua(action: Supplier<T>): T? {
            try {
                return action.get()
            } catch (e: IllegalArgumentException) {
                throw LuaException(e.message)
            } catch (e: IllegalStateException) {
                throw LuaException(e.message)
            }
        }

        @JvmStatic
        private fun describe(c: ChannelView): MutableMap<String, Any?> {
            return java.util.Map.of<String, Any?>(
                "name",
                c.name,
                "direction",
                c.direction,
                "side",
                c.side,
                "public",
                c.published,
                "value",
                c.value,
                "connections",
                c.connections,
                "provider",
                c.provider
            )
        }

        @JvmStatic
        private fun position(target: BlockEntity): String {
            return target.blockPos.toShortString()
        }
    }
}
