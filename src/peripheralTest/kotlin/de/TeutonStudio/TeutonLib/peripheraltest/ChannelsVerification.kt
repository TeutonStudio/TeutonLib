package de.TeutonStudio.TeutonLib.peripheraltest

import com.mojang.authlib.GameProfile
import dan200.computercraft.api.filesystem.WritableMount
import dan200.computercraft.shared.ModRegistry
import dan200.computercraft.shared.computer.blocks.ComputerBlockEntity
import dan200.computercraft.shared.computer.core.ServerComputer
import dan200.computercraft.shared.computer.inventory.ComputerMenuWithoutInventory
import de.TeutonStudio.TeutonLib.TeutonLib
import de.TeutonStudio.TeutonLib.api.computer.SourceView
import de.TeutonStudio.TeutonLib.integration.computer.*
import de.TeutonStudio.TeutonLib.integration.computer.ChannelHost
import de.TeutonStudio.TeutonLib.integration.computer.ChannelPersistence
import de.TeutonStudio.TeutonLib.integration.computer.ComputerIntegration
import de.TeutonStudio.TeutonLib.integration.computer.ComputerPayloads
import de.TeutonStudio.TeutonLib.integration.computer.DbwAccess
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkIdentity
import de.TeutonStudio.TeutonLib.integration.peripheral.LinkRegistry
import de.TeutonStudio.TeutonLib.integration.peripheral.PeripheralLinkBlockEntity
import de.TeutonStudio.TeutonLib.integration.peripheral.PeripheralLinks
import java.lang.AssertionError
import java.lang.Exception
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.function.Predicate
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.neoforged.neoforge.common.util.FakePlayer

object ChannelsVerification {
    private val A = BlockPos(24, 200, 8)
    private val B = BlockPos(10024, 200, 8)
    private val N = BlockPos(24, 200, 8)
    private var reader: ServerComputer? = null
    private var writer: ServerComputer? = null
    private var otherDimension: ServerComputer? = null
    private var world: ServerLevel? = null
    @JvmStatic
    private fun block(id: String): Block {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id))
    }

    @JvmStatic
    private fun require(c: Boolean, message: String?) {
        if (!c) throw AssertionError(message)
    }

    @Throws(Exception::class)
    @JvmStatic
    private fun make(level: ServerLevel, pos: BlockPos, family: String, program: String): ServerComputer {
        level.setChunkForced(pos.x shr 4, pos.z shr 4, true)
        level.removeBlock(pos, false)
        level.setBlockAndUpdate(pos, block("computercraft:computer_" + family).defaultBlockState())
        val host = level.getBlockEntity(pos) as ComputerBlockEntity?
        val computer = host!!.createServerComputer()
        val mount: WritableMount = computer.createRootMount()!!
        if (mount.exists("channel-result")) mount.delete("channel-result")
        mount.openFile(
            "startup.lua",
            java.util.Set.of<OpenOption>(
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
        ).use { file ->
            file.write(StandardCharsets.UTF_8.encode(program))
        }
        computer.turnOn()
        return computer
    }

    @JvmStatic
    private fun guarded(body: String?): String {
        return ("local function result(s) local f=fs.open('channel-result','w'); f.write(s); f.close() end\n"
                + "local ok,err=pcall(function()\nlocal c=require('teutonlib.channels')\n" + body + "\nend)\nif not ok then result('FAIL '..tostring(err)) end\n")
    }

    @Throws(Exception::class)
    @JvmStatic
    private fun result(computer: ServerComputer): String? {
        val mount: WritableMount = computer.createRootMount()!!
        if (!mount.exists("channel-result")) return "WAITING"
        mount.openForRead("channel-result").use { file ->
            val buffer = ByteBuffer.allocate(4096)
            file.read(buffer)
            buffer.flip()
            return StandardCharsets.UTF_8.decode(buffer).toString()
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    private fun menuSafety(level: ServerLevel, first: ServerComputer, second: ServerComputer) {
        val one = FakePlayer(level, GameProfile(UUID.randomUUID(), "ChannelViewerA"))
        val two = FakePlayer(level, GameProfile(UUID.randomUUID(), "ChannelViewerB"))
        one.setPos(A.x + .5, A.y.toDouble(), A.z + .5)
        two.setPos(B.x + .5, B.y.toDouble(), B.z + .5)
        one.containerMenu = ComputerMenuWithoutInventory(
            ModRegistry.Menus.COMPUTER.get(),
            7,
            one.inventory,
            Predicate { player: Player? -> player!!.distanceToSqr(A.center) < 64 },
            first
        )
        two.containerMenu = ComputerMenuWithoutInventory(
            ModRegistry.Menus.COMPUTER.get(),
            7,
            two.inventory,
            Predicate { player: Player? -> player!!.distanceToSqr(B.center) < 64 },
            second
        )
        val handle = ComputerPayloads::class.java.getDeclaredMethod(
            "handle",
            ComputerPayloads.Request::class.java,
            ServerPlayer::class.java
        )
        handle.setAccessible(true)
        val session = UUID.randomUUID()
        handle.invoke(null, ComputerPayloads.Request(7, session, "set", "local_signal", "", 15, false), one)
        require(
            (level.getBlockEntity(A) as ChannelHost).`teutonlib$channels`().get("local_signal").value == 15,
            "valid menu write rejected"
        )
        handle.invoke(null, ComputerPayloads.Request(7, session, "public", "secret_signal", "", 0, true), two)
        require(
            (level.getBlockEntity(B) as ChannelHost).`teutonlib$channels`().get("secret_signal").published,
            "second player menu write rejected"
        )
        handle.invoke(null, ComputerPayloads.Request(7, session, "public", "secret_signal", "", 0, false), one)
        require(
            (level.getBlockEntity(B) as ChannelHost).`teutonlib$channels`().get("secret_signal").published,
            "request escaped its player's open computer menu"
        )
        handle.invoke(null, ComputerPayloads.Request(8, session, "set", "local_signal", "", 1, false), one)
        require(
            (level.getBlockEntity(A) as ChannelHost).`teutonlib$channels`().get("local_signal").value == 15,
            "stale menu ID accepted"
        )
        // A spectator may retain a valid menu after a game-mode transition.
        val spectatorMenu = one.containerMenu
        one.setGameMode(net.minecraft.world.level.GameType.SPECTATOR)
        one.containerMenu = spectatorMenu
        require(one.isSpectator && spectatorMenu.stillValid(one), "spectator fixture must retain a valid nearby menu")
        val bank = (level.getBlockEntity(A) as ChannelHost).`teutonlib$channels`()
        val before = bank.entries()
        val writes = listOf(
            ComputerPayloads.Request(7, session, "create", "spectator_new", "output", 0, false),
            ComputerPayloads.Request(7, session, "remove", "local_signal", "", 0, false),
            ComputerPayloads.Request(7, session, "rename", "local_signal", "spectator_rename", 0, false),
            ComputerPayloads.Request(7, session, "public", "local_signal", "", 0, true),
            ComputerPayloads.Request(7, session, "set", "local_signal", "", 1, false)
        )
        writes.forEach { request ->
            handle.invoke(null, request, one)
            require(bank.entries() == before, "spectator changed channels with action " + request.action)
        }
        handle.invoke(null, ComputerPayloads.Request(7, session, "snapshot", "", "", 0, false), one)
        require(bank.entries() == before, "spectator snapshot mutated data")
        one.setGameMode(net.minecraft.world.level.GameType.SURVIVAL)
        one.containerMenu = spectatorMenu
        handle.invoke(null, ComputerPayloads.Request(7, session, "set", "local_signal", "", 14, false), one)
        require(bank.get("local_signal").value == 14, "survival write rejected after spectator transition")
        two.setPos(B.x.toDouble(), (B.y + 100).toDouble(), B.z.toDouble())
        handle.invoke(null, ComputerPayloads.Request(7, session, "set", "secret_signal", "", 1, false), two)
        require(
            (level.getBlockEntity(B) as ChannelHost).`teutonlib$channels`().get("secret_signal").value == 7,
            "out of reach menu write accepted"
        )
        ComputerIntegration.runtime(first)!!.setValue("local_signal", 9)
        ComputerIntegration.runtime(second)!!.setPublic("secret_signal", false)
        one.containerMenu.removed(one)
        two.containerMenu.removed(two)
        TeutonLib.Companion.LOGGER.info("Verified independent player menus, stale menu IDs, cross-computer isolation, reach permissions and spectator protection")
    }

    @Throws(Exception::class)
    @JvmStatic
    fun run(level: ServerLevel, action: String): String? {
        if (action == "normal" || action == "advanced") {
            world = level
            level.setChunkForced(A.x shr 4, 0, true)
            level.setChunkForced(B.x shr 4, 0, true)
            for (pos in java.util.List.of<BlockPos>(A, B)) for (d in Direction.entries) level.removeBlock(
                pos!!.relative(d),
                false
            )
            level.setBlockAndUpdate(B.east(), block("computercraft:wired_modem_full").defaultBlockState())
            level.setBlockAndUpdate(A.east(), block("computercraft:wired_modem_full").defaultBlockState())
            writer = make(
                level, B, action, guarded(
                    """
                if #c.list()==0 then
                  c.create('public_signal','output'); c.set('public_signal',12)
                  c.create('secret_signal','output'); c.set('secret_signal',7)
                  -- Publish last: readers must not observe the private channel before its value is set.
                  c.setPublic('public_signal',true)
                end
                result('WRITER_READY')
                while true do os.pullEvent('hold') end
                """.trimIndent()
                )
            )
            reader = make(
                level, A, action, guarded(
                    """
                c.create('incoming','input','top')
                c.create('local_signal','output'); c.set('local_signal',9)
                assert(not pcall(c.set,'local_signal',1.5), 'fractional signals rejected')
                local all=c.list(); assert(#all==2 and all[1].name=='incoming')
                local id=%d
                local visible,hidden,last
                for i=1,100 do
                  local ok,a,b=pcall(c.list,id); last=tostring(ok)..":"..tostring(a)..":"..tostring(b)
                  if ok and #a==1 and b==1 then visible,hidden=a,b; break end
                  sleep(0.05)
                end
                assert(visible and #visible==1 and hidden==1, 'public/private listing '..tostring(last))
                assert(visible[1].name=='public_signal' and visible[1].value==12)
                local privateValue=c.read(id,'secret_signal')
                assert(privateValue==7, 'known private read '..tostring(privateValue))
                assert(not pcall(c.read,id,'missing'), 'unknown read rejected')
                local lines={}; local previous=print
                print=function(...) local words={...}; for i,v in ipairs(words) do words[i]=tostring(v) end; lines[#lines+1]=table.concat(words,' ') end
                local command=assert(loadfile('rom/programs/channels.lua',nil,setmetatable({print=print},{__index=_ENV})))
                local ran=pcall(command,tostring(id)); print=previous
                assert(ran and lines[#lines]=='1 private channels not shown', 'CLI private footer')
                for _,line in ipairs(lines) do assert(not line:find('secret_signal',1,true), 'private name leaked') end
                result('READY')
                while true do
                  local _,phase=os.pullEvent('channel_phase'); sleep(0.25)
                  if phase=='target_modem' or phase=='local_modem' or phase=='offline' then
                    assert(not pcall(c.list,id),'unavailable remote must fail')
                  elseif phase=='reconnected' or phase=='link_modem' then
                    assert(c.read(id,'secret_signal')==7,'reconnected remote read')
                  end
                  result('PASS '..phase)
                end
                """.trimIndent().format(writer!!.id)
                )
            )
            val nether = level.server.getLevel(Level.NETHER)
            nether!!.setChunkForced(N.x shr 4, N.z shr 4, true)
            nether.setBlockAndUpdate(N.east(), block("computercraft:wired_modem_full").defaultBlockState())
            otherDimension = ChannelsVerification.make(
                nether!!,
                N,
                action,
                guarded("sleep(.5); assert(peripheral.find('modem')); assert(not pcall(c.list," + writer!!.id + ")); result('DIMENSION_PASS'); while true do os.pullEvent('hold') end")
            )
            (nether.getBlockEntity(N) as ComputerBlockEntity).updateInputsImmediately()
            return "STARTED"
        }
        if (action == "result") {
            var output = ChannelsVerification.result(reader!!)!!
            if (output.startsWith("FAIL")) output += " writer=" + (level.getBlockEntity(B) as ChannelHost).`teutonlib$channels`()
                .entries() + " reader=" + (level.getBlockEntity(A) as ChannelHost).`teutonlib$channels`()
                .entries() + " writerResult=" + ChannelsVerification.result(writer!!)
            return output
        }
        if (action == "dimension") return ChannelsVerification.result(otherDimension!!)
        if (action == "target_modem") {
            level.removeBlock(B.east(), false)
            (level.getBlockEntity(B) as ComputerBlockEntity).updateInputsImmediately()
        }
        if (action == "local_modem") {
            level.removeBlock(A.east(), false)
            (level.getBlockEntity(A) as ComputerBlockEntity).updateInputsImmediately()
        }
        if (action == "reconnected") {
            level.setBlockAndUpdate(B.east(), block("computercraft:wired_modem_full").defaultBlockState())
            level.setBlockAndUpdate(A.east(), block("computercraft:wired_modem_full").defaultBlockState())
            (level.getBlockEntity(A) as ComputerBlockEntity).updateInputsImmediately()
            (level.getBlockEntity(B) as ComputerBlockEntity).updateInputsImmediately()
        }
        if (action == "link_modem") {
            val host = level.getBlockEntity(A)
            val id = UUID.randomUUID()
            (host as LinkIdentity).`teutonlib$linkTarget`(id)
            val pos = A.south(4)
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState())
            level.setBlockAndUpdate(
                pos,
                block("computercraft:wireless_modem_normal").defaultBlockState()
                    .setValue<Direction, Direction>(BlockStateProperties.FACING, Direction.UP)
            )
            level.setBlockAndUpdate(pos.above(), PeripheralLinks.BLOCK.get().defaultBlockState())
            (level.getBlockEntity(pos.above()) as PeripheralLinkBlockEntity).target(id)
            LinkRegistry.validate()
            level.removeBlock(A.east(), false)
        }
        if (action == "offline") {
            writer!!.shutdown()
            require(
                (level.getBlockEntity(B) as ChannelHost).`teutonlib$channels`().get("public_signal").value == 12,
                "shutdown lost output"
            )
        }
        if (action == "ui_prepare") {
            level.setBlockAndUpdate(A.east(), block("computercraft:wired_modem_full").defaultBlockState())
            level.setBlockAndUpdate(A.west(), block("computercraft:wireless_modem_advanced").defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST))
            level.setBlockAndUpdate(A.north(), Blocks.CHEST.defaultBlockState())
            val host = level.getBlockEntity(A) as ComputerBlockEntity
            Direction.entries.forEach { host.neighborChanged(A.relative(it)) }
            host.updateInputsImmediately()
            return "REQUESTED"
        }
        if (action == "ui_ready") {
            val views = ComputerIntegration.runtime(reader!!)!!.sourceViews()
            val kinds = views.mapNotNull { SourceIntrospection.describe(ComputerIntegration.runtime(reader!!)!!, it).modem?.kind }.toSet()
            return if (kinds == setOf("wired", "radio", "ender") && views.any { "inventory" in it.types }) "UI_READY" else "WAITING $kinds ${views.map { it.name + ":" + it.types.joinToString() }} " +
                dan200.computercraft.core.computer.ComputerSide.entries.map { it.toString() + ":" + reader!!.getPeripheral(it)?.type }
        }
        if (action == "inspect") {
            val runtime = ComputerIntegration.runtime(reader!!)!!
            require(runtime != null, "missing runtime")
            require(
                runtime.sourceViews().stream().anyMatch { s: SourceView? -> s!!.types.contains("modem") },
                "modem source missing"
            )
            val viewer = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level)
            val sourceSnapshot = StandardComputerWorkspace.snapshot(viewer, reader!!, 1)
            val devices = sourceSnapshot.entries.flatMap { it.children }
            val modemApis = devices.filter { it.metadata.getCompound("api").contains("modem") }
            require(modemApis.isNotEmpty(), "shared workspace modem missing")
            val modemKinds = modemApis.map { it.metadata.getCompound("api").getCompound("modem").getString("kind") }.toSet()
            require(modemKinds == setOf("wired", "radio", "ender"), "missing modem kinds: $modemKinds")
            require(devices.any { entry ->
                val api = de.TeutonStudio.TeutonLib.api.computer.SourceApi.decode(entry.metadata.getCompound("api"))
                "inventory" in api.types && api.methods.any { it.name == "list" } && api.methods.any { it.name == "pushItems" }
            }, "generic inventory API missing")
            modemApis.forEach { entry ->
                val api = de.TeutonStudio.TeutonLib.api.computer.SourceApi.decode(entry.metadata.getCompound("api"))
                if (api.modem?.kind == "wired")
                    require(api.methods.any { it.name == "getNamesRemote" }, "wired-specific API missing")
                require(api.methods.any { it.name == "transmit" }, "base modem API missing")
            }
            fun ui(action: String, selected: String = "", text: String = "", target: String = "") =
                StandardComputerWorkspace.execute(viewer, reader!!,
                    de.TeutonStudio.TeutonLib.api.computer.ComputerWorkspace.Command(2, action, selected, "", target, text))
            ui("create", text = "ui_signal", target = "output")
            ui("plus", "ui_signal"); ui("public", "ui_signal")
            require(runtime.bank()!!.get("ui_signal").value == 1 && runtime.bank()!!.get("ui_signal").published, "workspace value/privacy")
            ui("rename", "ui_signal", "ui_renamed")
            ui("minus", "ui_renamed")
            require(runtime.bank()!!.get("ui_renamed").value == 0, "workspace rename/decrement")
            ui("remove", "ui_renamed")
            val rejectedInput = runCatching { ui("create", text = "ui_input", target = "input:top") }.exceptionOrNull()
            require(rejectedInput is IllegalArgumentException, "workspace must reject input creation")
            runtime.createChannel("ui_input", "input", "top")
            require(runtime.bank()!!.get("ui_input").side == "top", "runtime input side")
            ui("remove", "ui_input")
            val data = ComputerPayloads.snapshot(runtime, "")
            require(data.getList("channels", Tag.TAG_COMPOUND.toInt()).size == 2, "channel snapshot")
            val host = level.getBlockEntity(B)
            val saved = host!!.saveWithFullMetadata(level.registryAccess())
            val bank = ChannelPersistence.load(saved.getList("TeutonLibChannels", Tag.TAG_COMPOUND.toInt()))
            require(
                bank.get("public_signal").value == 12 && bank.get("public_signal").published && !bank.get("secret_signal").published,
                "saved output/privacy"
            )
            if (DbwAccess.available()) {
                val manager = Class.forName("edn.stratodonut.drivebywire.wire.WireNetworkManager")
                manager.getMethod(
                    "createConnection",
                    Level::class.java,
                    BlockPos::class.java,
                    BlockPos::class.java,
                    Direction::class.java,
                    String::class.java
                ).invoke(null, level, B, A, Direction.UP, "public_signal")
                require(DbwAccess.read(level.getBlockEntity(A)!!, "top") == 12, "DbW input/output")
                DbwAccess.rename(host, "public_signal", "renamed_signal", 12)
                require(
                    DbwAccess.connections(host, "renamed_signal") == 1 && DbwAccess.connections(
                        host,
                        "public_signal"
                    ) == 0, "DbW rename migrates cables"
                )
                DbwAccess.rename(host, "renamed_signal", "public_signal", 12)
                require(DbwAccess.connections(host, "public_signal") == 1, "DbW reverse rename")
                val values = manager.getMethod("getSourceSignals", BlockPos::class.java)
                    .invoke(manager.getMethod("get", Level::class.java).invoke(null, level), B) as MutableMap<*, *>
                require(values.get("public_signal") == 12, "shutdown reset DbW source signal")
            }
            return "PASS inspect"
        }
        if (action == "unload") {
            level.setChunkForced(B.x shr 4, 0, false)
            return "STARTED unload"
        }
        if (action == "unloaded") {
            if (level.chunkSource.getChunkNow(B.x shr 4, 0) != null) return "WAITING"
            if (DbwAccess.available()) require(
                DbwAccess.read(level.getBlockEntity(A)!!, "top") == 12,
                "unload reset DbW output"
            )
            return "PASS unloaded"
        }
        if (action == "reload") {
            level.setChunkForced(B.x shr 4, 0, true)
            level.getChunkAt(B)
            return "STARTED reload"
        }
        if (action == "restored") {
            val host = level.getBlockEntity(B)
            require((host as ChannelHost).`teutonlib$channels`().get("public_signal").value == 12, "reload lost output")
            if (DbwAccess.available() && DbwAccess.read(level.getBlockEntity(A)!!, "top") != 12) return "WAITING"
            return "PASS restored"
        }
        if (action == "restart") {
            level.setChunkForced(B.x shr 4, 0, true)
            val host = level.getBlockEntity(B)
            require(host != null, "restart host absent")
            val bank = (host as ChannelHost).`teutonlib$channels`()
            require(
                bank.get("public_signal").value == 12 && bank.get("public_signal").published && !bank.get("secret_signal").published,
                "restart bank lost state"
            )
            if (DbwAccess.available() && DbwAccess.read(level.getBlockEntity(A)!!, "top") != 12) return "WAITING"
            // No real player opens these synthetic menus. Create both live computer instances explicitly.
            val first = (level.getBlockEntity(A) as ComputerBlockEntity).createServerComputer()
            first.turnOn()
            val second = (host as ComputerBlockEntity).createServerComputer()
            second.turnOn()
            if (first == null || ComputerIntegration.runtime(first) == null || ComputerIntegration.runtime(second) == null) return "WAITING"
            menuSafety(level, first, second)
            return "PASS restart"
        }
        reader!!.queueEvent("channel_phase", arrayOf<Any>(action))
        return "REQUESTED"
    }
}
