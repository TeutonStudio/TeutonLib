package de.TeutonStudio.TeutonLib.api.computer

import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.peripheral.IDynamicPeripheral
import dan200.computercraft.api.peripheral.IComputerAccess
import dan200.computercraft.api.lua.ILuaContext
import dan200.computercraft.api.lua.IArguments
import dan200.computercraft.api.lua.MethodResult
import de.TeutonStudio.TeutonLib.integration.computer.SourceIntrospection
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dan200.computercraft.shared.peripheral.modem.ModemPeripheral
import dan200.computercraft.shared.peripheral.modem.ModemState
import dan200.computercraft.api.network.*
import net.minecraft.world.phys.Vec3

class SourceApiTest {
    open class AnnotatedPeripheral : IPeripheral {
        var calls = 0
        override fun getType() = "example"
        override fun equals(other: IPeripheral?) = other === this
        @LuaFunction fun read(): Int { calls++; return 42 }
        @LuaFunction("write", "set") fun write(value: Int) { calls++ }
        fun internalOnly() {}
    }

    class DynamicPeripheral : AnnotatedPeripheral(), IDynamicPeripheral {
        override fun getMethodNames() = arrayOf("dynamic", "read")
        override fun callMethod(computer: IComputerAccess, context: ILuaContext, method: Int, arguments: IArguments): MethodResult {
            throw AssertionError("Introspection must never call methods")
        }
    }

    @Test fun discoversLuaNamesAndAliasesWithoutInvokingMethods() {
        val peripheral = AnnotatedPeripheral()
        assertEquals(listOf("read", "set", "write"), SourceIntrospection.methodNames(peripheral))
        assertEquals(0, peripheral.calls)
    }

    @Test fun dynamicMethodsAndAnnotationsAreBothIncluded() {
        val peripheral = DynamicPeripheral()
        assertEquals(setOf("dynamic", "read", "set", "write"), SourceIntrospection.methodNames(peripheral).toSet())
        assertEquals(0, peripheral.calls)
    }

    @Test fun documentationCannotInventPeripheralMethods() {
        val methods = SourceIntrospection.documentedMethods(AnnotatedPeripheral(),
            listOf(SourceMethod("read", "read() -> integer", "Read a value"), SourceMethod("missing")))
        assertEquals("read() -> integer", methods.first { it.name == "read" }.signature)
        assertFalse(methods.any { it.name == "missing" })
        assertEquals("write(value: integer) -> void", methods.first { it.name == "write" }.signature)
        assertEquals(listOf(SourceMethod("locate", "gps.locate()")), SourceIntrospection.documentedMethods(
            null, listOf(SourceMethod("locate", "gps.locate()"))))
    }

    @Test fun infersArgumentsReturnTypesAndAliasesWithoutInvokingMethods() {
        val peripheral = AnnotatedPeripheral()
        val methods = SourceIntrospection.documentedMethods(peripheral, emptyList()).associateBy { it.name }
        assertEquals("read() -> integer", methods.getValue("read").signature)
        assertEquals("write(value: integer) -> void", methods.getValue("write").signature)
        assertEquals("set(value: integer) -> void", methods.getValue("set").signature)
        assertEquals(0, peripheral.calls)
        assertEquals("dynamic(...: unknown) -> unknown", SourceIntrospection.documentedMethods(
            DynamicPeripheral(), emptyList()).first { it.name == "dynamic" }.signature)
    }

    class TestModem(private val wireless: Boolean, private val ender: Boolean) : ModemPeripheral(ModemState()) {
        var transmissions = 0
        override fun getLevel(): net.minecraft.world.level.Level? = null
        override fun getPosition() = Vec3.ZERO
        override fun getRange() = 128.0
        override fun isInterdimensional() = ender
        override fun equals(other: IPeripheral?) = other === this
        override fun getNetwork() = object : PacketNetwork {
            override fun isWireless() = wireless
            override fun addReceiver(receiver: PacketReceiver) {}
            override fun removeReceiver(receiver: PacketReceiver) {}
            override fun transmitSameDimension(packet: Packet, range: Double) { transmissions++ }
            override fun transmitInterdimensional(packet: Packet) { transmissions++ }
        }
    }

    @Test fun modemTypesRemainDistinctWithoutSendingPackets() {
        for ((wireless, ender, kind) in listOf(Triple(false, false, "wired"), Triple(true, false, "radio"), Triple(true, true, "ender"))) {
            val modem = TestModem(wireless, ender)
            val access = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(IComputerAccess::class.java)) { proxy, method, args ->
                when (method.name) {
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> null
                }
            } as IComputerAccess
            modem.attach(access)
            val details = SourceIntrospection.modemDetails(modem, "test:dimension")!!
            assertEquals(kind, details.kind)
            assertEquals(ender, details.interdimensional)
            assertEquals(if (wireless) 128.0 else null, details.range)
            assertEquals("test:dimension", details.dimension)
            assertTrue(SourceIntrospection.methodNames(modem).containsAll(listOf("open", "isOpen", "close", "closeAll", "transmit", "isWireless")))
            val signatures = SourceIntrospection.documentedMethods(modem, emptyList()).associate { it.name to it.signature }
            assertEquals("open(channel: integer) -> void", signatures["open"])
            assertEquals("isOpen(channel: integer) -> boolean", signatures["isOpen"])
            assertEquals("transmit(channel: integer, replyChannel: integer, payload: any) -> void", signatures["transmit"])
            assertEquals(0, modem.transmissions)
            modem.detach(access)
        }
    }

    @Test fun apiRoundTripPreservesNetworkAndDocumentation() {
        for (kind in listOf("wired", "radio", "ender")) {
            val api = SourceApi("left", listOf("modem"), "local",
                listOf(SourceMethod("open", "open(channel: integer)", "Open a channel")),
                ModemDetails(kind, "minecraft:overworld", if (kind == "wired") null else 256.0, kind == "ender"),
                "1, 2, 3", "4, 5, 6")
            assertEquals(api, SourceApi.decode(api.encode()))
        }
        val empty = SourceApi("extension", listOf("custom"), "local", emptyList())
        assertEquals(empty, SourceApi.decode(empty.encode()))
    }
}
