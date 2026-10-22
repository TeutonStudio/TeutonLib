package de.TeutonStudio.TeutonLib.integration.peripheral.heli

import dan200.computercraft.api.lua.LuaException

internal object HeliArguments {
    @Throws(LuaException::class)
    fun vectorTable(table: Map<*, *>, keys: Set<String>): Map<String, Double> {
        if (table.keys.any { it !is String } || table.keys.map { it as String }.toSet() != keys) {
            throw LuaException("Expected table fields exactly: ${keys.joinToString()}")
        }
        return keys.associateWith { key ->
            val value = table[key]
            if (value !is Number) throw LuaException("Field '$key' must be a number")
            value.toDouble().also { if (!it.isFinite()) throw LuaException("Field '$key' must be finite") }
        }
    }
}
