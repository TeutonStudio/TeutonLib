package de.TeutonStudio.TeutonLib.api.computer.channel

import java.util.UUID

data class ChannelGroupBinding(val alias: String, val targetId: String)

data class ChannelGroupDefinition(
    val id: UUID,
    val name: String,
    val bindings: List<ChannelGroupBinding>
)

/** Persistent groups of aliases to stable target ids. Missing targets intentionally remain stored. */
open class PersistentChannelGroupBank(
    private val maxGroups: Int = 32,
    private val maxBindings: Int = 64,
    private val validName: (String) -> Boolean,
    private val validTarget: (String) -> Boolean,
    private val changed: () -> Unit = {}
) {
    private val groups = mutableListOf<ChannelGroupDefinition>()

    fun definitions(): List<ChannelGroupDefinition> = groups.map { it.copy(bindings = it.bindings.toList()) }

    fun encodedDefinitions(): String = buildString {
        groups.forEach { group ->
            append("G|").append(group.id).append('|').append(group.name).append('\n')
            group.bindings.forEach { binding ->
                append("B|").append(group.id).append('|').append(binding.alias).append('|')
                    .append(binding.targetId).append('\n')
            }
        }
    }.trimEnd('\n')

    fun loadEncodedDefinitions(encoded: String?) {
        groups.clear()
        if (encoded.isNullOrBlank()) return
        val parsed = linkedMapOf<UUID, Pair<String, MutableList<ChannelGroupBinding>>>()
        encoded.lineSequence().forEach { line ->
            val parts = line.split('|')
            when (parts.firstOrNull()) {
                "G" -> {
                    if (parts.size != 3 || parsed.size >= maxGroups) return@forEach
                    val id = runCatching { UUID.fromString(parts[1]) }.getOrNull() ?: return@forEach
                    val name = parts[2]
                    if (!validName(name) || parsed.values.any { it.first == name }) return@forEach
                    parsed.putIfAbsent(id, name to arrayListOf())
                }
                "B" -> {
                    if (parts.size != 4) return@forEach
                    val id = runCatching { UUID.fromString(parts[1]) }.getOrNull() ?: return@forEach
                    val entry = parsed[id] ?: return@forEach
                    if (entry.second.size >= maxBindings) return@forEach
                    val alias = parts[2]
                    val target = parts[3]
                    if (!validName(alias) || !validTarget(target) || entry.second.any { it.alias == alias }) return@forEach
                    entry.second += ChannelGroupBinding(alias, target)
                }
            }
        }
        parsed.forEach { (id, entry) -> groups += ChannelGroupDefinition(id, entry.first, entry.second.toList()) }
    }

    fun addGroup(rawName: String): ChannelGroupDefinition {
        val name = checkedName(rawName)
        require(groups.none { it.name == name }) { "Channel group '$name' already exists" }
        check(groups.size < maxGroups) { "Channel group limit reached ($maxGroups)" }
        return ChannelGroupDefinition(UUID.randomUUID(), name, emptyList()).also { groups += it; changed() }
    }

    fun renameGroup(id: UUID, rawName: String): ChannelGroupDefinition {
        val old = group(id)
        val name = checkedName(rawName)
        require(groups.none { it.id != id && it.name == name }) { "Channel group '$name' already exists" }
        if (old.name == name) return old
        return old.copy(name = name).also { groups[groups.indexOf(old)] = it; changed() }
    }

    fun removeGroup(id: UUID): ChannelGroupDefinition = group(id).also { groups.remove(it); changed() }

    fun bind(id: UUID, rawAlias: String, targetId: String): ChannelGroupDefinition {
        val group = group(id)
        val alias = checkedName(rawAlias)
        require(validTarget(targetId)) { "Invalid channel target '$targetId'" }
        val bindings = group.bindings.toMutableList()
        val existing = bindings.indexOfFirst { it.alias == alias }
        if (existing >= 0) bindings[existing] = ChannelGroupBinding(alias, targetId)
        else {
            check(bindings.size < maxBindings) { "Channel group '${group.name}' supports at most $maxBindings bindings" }
            bindings += ChannelGroupBinding(alias, targetId)
        }
        return replace(group, group.copy(bindings = bindings))
    }

    fun renameBinding(id: UUID, rawOldAlias: String, rawNewAlias: String): ChannelGroupDefinition {
        val group = group(id)
        val oldAlias = rawOldAlias.trim()
        val newAlias = checkedName(rawNewAlias)
        val index = group.bindings.indexOfFirst { it.alias == oldAlias }
        require(index >= 0) { "Unknown binding '$oldAlias' in group '${group.name}'" }
        require(group.bindings.none { it.alias == newAlias && it.alias != oldAlias }) {
            "Binding '$newAlias' already exists in group '${group.name}'"
        }
        if (oldAlias == newAlias) return group
        val bindings = group.bindings.toMutableList().apply { this[index] = this[index].copy(alias = newAlias) }
        return replace(group, group.copy(bindings = bindings))
    }

    fun unbind(id: UUID, rawAlias: String): ChannelGroupDefinition {
        val group = group(id)
        val alias = rawAlias.trim()
        require(group.bindings.any { it.alias == alias }) { "Unknown binding '$alias' in group '${group.name}'" }
        return replace(group, group.copy(bindings = group.bindings.filterNot { it.alias == alias }))
    }

    fun group(id: UUID): ChannelGroupDefinition = groups.firstOrNull { it.id == id }
        ?: throw NoSuchElementException("Unknown channel group '$id'")

    fun group(rawName: String): ChannelGroupDefinition {
        val name = rawName.trim()
        return groups.firstOrNull { it.name == name }
            ?: throw NoSuchElementException("Unknown channel group '$name'")
    }

    private fun replace(old: ChannelGroupDefinition, next: ChannelGroupDefinition): ChannelGroupDefinition {
        groups[groups.indexOf(old)] = next
        changed()
        return next
    }

    private fun checkedName(raw: String): String = raw.trim().also { value ->
        require(validName(value)) { "Invalid channel group name '$value'" }
    }
}
