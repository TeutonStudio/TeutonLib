package de.TeutonStudio.TeutonLib.integration.computer

import java.util.LinkedHashMap

/** Persistent definitions and output values. All mutations run on the server thread.  */
class ChannelBank {
    @JvmRecord
    data class Entry(
        val name: String,
        val direction: String,
        val side: String,
        val published: Boolean,
        val value: Int
    )

    private val entries: MutableMap<String, Entry> = LinkedHashMap<String, Entry>()
    fun entries(): MutableList<Entry> {
        return java.util.List.copyOf<Entry>(entries.values)
    }

    fun find(name: String?): Entry? {
        return entries.get(name)
    }

    fun get(name: String?): Entry {
        val entry = requireNotNull(entries[name]) { "Unknown channel '" + name + "'" }
        return entry
    }

    fun create(name: String, direction: String, side: String?) {
        name(name)
        require(entries.size < LIMIT) { "Channel limit reached (64)" }
        require(!entries.containsKey(name)) { "Channel already exists" }
        require(!(direction != "input" && direction != "output")) { "Direction must be input or output" }
        require(
            !(direction == "input" && !mutableSetOf<String>(
                "front",
                "back",
                "left",
                "right",
                "top",
                "bottom"
            ).contains(side))
        ) { "Input requires a computer side" }
        entries.put(name, Entry(name, direction, if (direction == "input") side!! else "", false, 0))
    }

    fun remove(name: String?): Entry {
        val previous = get(name)
        entries.remove(name)
        return previous
    }

    fun rename(oldName: String, newName: String) {
        name(newName)
        val previous = get(oldName)
        if (oldName == newName) return
        require(!entries.containsKey(newName)) { "Channel already exists" }
        entries.remove(oldName)
        entries.put(newName, Entry(newName, previous.direction, previous.side, previous.published, previous.value))
    }

    fun published(name: String, published: Boolean) {
        val e = get(name)
        entries.put(name, Entry(name, e.direction, e.side, published, e.value))
    }

    fun set(name: String, value: Int) {
        val e = get(name)
        require(e.direction == "output") { "Input channels are read-only" }
        require(!(value < 0 || value > 15)) { "Signal must be an integer from 0 to 15" }
        entries.put(name, Entry(name, e.direction, e.side, e.published, value))
    }

    fun restore(e: Entry) {
        require(!(e.direction == "output" && (e.value < 0 || e.value > 15))) { "Invalid saved output" }
        create(e.name!!, e.direction!!, e.side)
        published(e.name, e.published)
        if (e.direction == "output") set(e.name, e.value)
    }

    companion object {
        const val LIMIT: Int = 64
        @JvmStatic
        fun name(value: String): String {
            require(!(value == null || !value.matches("[A-Za-z0-9_.-]{1,32}".toRegex()) || value == "world")) { "Channel name must contain 1-32 letters, digits, _, . or -; 'world' is reserved" }
            return value
        }
    }
}
