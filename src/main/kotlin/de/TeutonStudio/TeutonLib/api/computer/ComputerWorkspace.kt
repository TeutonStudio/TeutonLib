package de.TeutonStudio.TeutonLib.api.computer

import dan200.computercraft.shared.computer.core.ServerComputer
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerPlayer

/** Authoritative addon backend. Callbacks run on the server thread after menu validation. */
interface ComputerWorkspace {
    fun owns(computer: ServerComputer): Boolean
    fun snapshot(player: ServerPlayer, computer: ServerComputer, page: Int): Snapshot
    fun execute(player: ServerPlayer, computer: ServerComputer, command: Command)

    @JvmRecord
    data class Command(val page: Int, val action: String, val selected: String, val group: String, val target: String, val text: String)

    @JvmRecord
    data class Action(val id: String, val label: String, val needsText: Boolean, val confirm: Boolean, val clientSide: Boolean) {
        fun encode(): CompoundTag = CompoundTag().also {
            it.putString("id", id); it.putString("label", label)
            it.putBoolean("text", needsText); it.putBoolean("confirm", confirm); it.putBoolean("client", clientSide)
        }
        companion object {
            @JvmStatic fun decode(t: CompoundTag) = Action(t.getString("id"), t.getString("label"), t.getBoolean("text"), t.getBoolean("confirm"), t.getBoolean("client"))
        }
    }

    @JvmRecord
    data class Entry private constructor(
        val id: String, val label: String, val detail: String, val group: String, val target: String,
        val selectable: Boolean, val collapsed: Boolean, val children: List<Entry>, val actions: List<Action>,
        val metadata: CompoundTag, private val copied: Boolean
    ) {
        constructor(id: String, label: String, detail: String, group: String, target: String,
                    selectable: Boolean, collapsed: Boolean, children: List<Entry>, actions: List<Action>, metadata: CompoundTag) :
            this(id, label, detail, group, target, selectable, collapsed,
                 java.util.List.copyOf(children), java.util.List.copyOf(actions), metadata.copy(), true)
        fun encode(): CompoundTag = CompoundTag().also { t ->
            t.putString("id", id); t.putString("label", label); t.putString("detail", detail)
            t.putString("group", group); t.putString("target", target)
            t.putBoolean("selectable", selectable); t.putBoolean("collapsed", collapsed)
            t.put("children", ListTag().also { list -> children.forEach { list.add(it.encode()) } })
            t.put("actions", ListTag().also { list -> actions.forEach { list.add(it.encode()) } })
            t.put("metadata", metadata.copy())
        }
        companion object {
            fun decode(t: CompoundTag, depth: Int, count: IntArray): Entry {
                require(depth <= 8 && ++count[0] <= 8192) { "Workspace tree too large" }
                val children = t.getList("children", Tag.TAG_COMPOUND.toInt()).map { decode(it as CompoundTag, depth + 1, count) }
                val actions = t.getList("actions", Tag.TAG_COMPOUND.toInt()).map { Action.decode(it as CompoundTag) }
                require(actions.size <= 16) { "Too many workspace actions" }
                return Entry(t.getString("id"), t.getString("label"), t.getString("detail"), t.getString("group"), t.getString("target"),
                    t.getBoolean("selectable"), t.getBoolean("collapsed"), children, actions, t.getCompound("metadata"))
            }
        }
    }

    @JvmRecord
    data class Snapshot private constructor(val title: String, val status: String, val entries: List<Entry>, val actions: List<Action>, private val copied: Boolean) {
        constructor(title: String, status: String, entries: List<Entry>, actions: List<Action>) :
            this(title, status, java.util.List.copyOf(entries), java.util.List.copyOf(actions), true)
        fun encode(): CompoundTag = CompoundTag().also { t ->
            t.putString("title", title); t.putString("status", status)
            t.put("entries", ListTag().also { list -> entries.forEach { list.add(it.encode()) } })
            t.put("actions", ListTag().also { list -> actions.forEach { list.add(it.encode()) } })
        }
        companion object {
            @JvmStatic fun decode(t: CompoundTag): Snapshot {
                val count = intArrayOf(0)
                val entries = t.getList("entries", Tag.TAG_COMPOUND.toInt()).map { Entry.decode(it as CompoundTag, 0, count) }
                val actions = t.getList("actions", Tag.TAG_COMPOUND.toInt()).map { Action.decode(it as CompoundTag) }
                require(actions.size <= 16) { "Too many workspace actions" }
                return Snapshot(t.getString("title"), t.getString("status"), entries, actions)
            }
        }
    }
}
