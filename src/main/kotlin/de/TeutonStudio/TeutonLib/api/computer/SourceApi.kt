package de.TeutonStudio.TeutonLib.api.computer

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag

/** Display metadata only. Reading a source API never invokes a peripheral method. */
data class SourceMethod(val name: String, val signature: String = "", val description: String = "")

data class ModemDetails(val kind: String, val dimension: String, val range: Double?, val interdimensional: Boolean) {
    fun encode() = CompoundTag().also {
        it.putString("kind", kind); it.putString("dimension", dimension)
        range?.let { range -> it.putDouble("range", range) }
        it.putBoolean("interdimensional", interdimensional)
    }
}

data class SourceApi(val name: String, val types: List<String>, val connection: String,
                     val methods: List<SourceMethod>, val modem: ModemDetails? = null,
                     val devicePosition: String? = null, val linkPosition: String? = null) {
    fun encode() = CompoundTag().also { tag ->
        tag.putString("name", name); tag.putString("connection", connection)
        tag.put("types", ListTag().also { list -> types.forEach { list.add(StringTag.valueOf(it)) } })
        tag.put("methods", ListTag().also { list -> methods.forEach { method ->
            list.add(CompoundTag().also {
                it.putString("name", method.name); it.putString("signature", method.signature)
                it.putString("description", method.description)
            })
        } })
        modem?.let { tag.put("modem", it.encode()) }
        devicePosition?.let { tag.putString("position", it) }
        linkPosition?.let { tag.putString("link", it) }
    }

    companion object {
        @JvmStatic fun decode(tag: CompoundTag): SourceApi = SourceApi(
            tag.getString("name"),
            tag.getList("types", Tag.TAG_STRING.toInt()).map { it.asString },
            tag.getString("connection"),
            tag.getList("methods", Tag.TAG_COMPOUND.toInt()).map {
                val method = it as CompoundTag
                SourceMethod(method.getString("name"), method.getString("signature"), method.getString("description"))
            },
            if (!tag.contains("modem")) null else tag.getCompound("modem").let {
                ModemDetails(it.getString("kind"), it.getString("dimension"),
                    if (it.contains("range")) it.getDouble("range") else null, it.getBoolean("interdimensional"))
            },
            tag.getString("position").takeIf { it.isNotEmpty() },
            tag.getString("link").takeIf { it.isNotEmpty() }
        )
    }
}

/** Return documentation for this source. For peripherals only discovered methods are displayed. */
fun interface SourceDocumentationProvider {
    fun methods(computer: ComputerContext, source: SourceView): List<SourceMethod>
}

/** Additional peripherals already owned by an addon, without creating another attachment. */
data class PeripheralSource(val peripheral: dan200.computercraft.api.peripheral.IPeripheral,
                            val connection: String = "local", val devicePosition: String? = null,
                            val linkPosition: String? = null)
fun interface PeripheralSourceProvider {
    fun peripherals(computer: ComputerContext): Map<String, PeripheralSource>
}
