package de.TeutonStudio.TeutonLib.api.computer.channel

import java.util.UUID

data class SignalDefinition(val id: UUID, val name: String)
data class SignalView(val id: UUID, val name: String, val value: Int, val connections: Int)

interface SignalBackend : AutoCloseable {
    val name: String
    fun setValue(channel: String, value: Int)
    fun removeChannel(channel: String)
    fun renameChannel(oldName: String, newName: String, value: Int)
    fun connectionCount(channel: String): Int
    fun clearSignals()
    override fun close() = clearSignals()
}

/** Named, persistent signal definitions with non-persistent values and bounded server-tick pulses. */
class SignalBank(
    private val clock: () -> Long,
    private val authority: () -> Boolean,
    private val backendFactory: () -> SignalBackend,
    private val changed: () -> Unit = {},
    private val validName: (String) -> Boolean = { DEFAULT_NAME.matches(it) },
    private val maxChannels: Int = 32,
    private val maxPulseTicks: Int = 1200,
    private val subject: String = "Signal channel"
) : AutoCloseable {
    private data class State(var value: Int = 0, var pulseEndTick: Long? = null)

    private val definitions = mutableListOf<SignalDefinition>()
    private val states = hashMapOf<UUID, State>()
    private var backend: SignalBackend? = null
    private var outputEnabled = false

    fun definitions(): List<SignalDefinition> = definitions.toList()
    fun channelNames(): List<String> = definitions.map(SignalDefinition::name)
    fun encodedDefinitions(): String = definitions.joinToString("\n") { "${it.id}|${it.name}" }

    fun loadEncodedDefinitions(encoded: String?) {
        definitions.clear(); states.clear()
        if (encoded.isNullOrBlank()) return
        val names = hashSetOf<String>(); val ids = hashSetOf<UUID>()
        encoded.lineSequence().forEach { line ->
            if (definitions.size >= maxChannels) return@forEach
            val split = line.split('|', limit = 2)
            if (split.size != 2) return@forEach
            val id = runCatching { UUID.fromString(split[0]) }.getOrNull() ?: return@forEach
            val name = split[1]
            if (!validName(name) || !names.add(name) || !ids.add(id)) return@forEach
            definitions += SignalDefinition(id, name); states[id] = State()
        }
    }

    fun addChannel(rawName: String): SignalDefinition {
        val name = checkedName(rawName)
        require(definitions.none { it.name == name }) { "$subject '$name' already exists" }
        check(definitions.size < maxChannels) { "$subject limit reached ($maxChannels)" }
        return SignalDefinition(UUID.randomUUID(), name).also {
            definitions += it; states[it.id] = State(); changed()
        }
    }

    fun removeChannel(rawName: String): SignalDefinition = removeChannel(definition(rawName).id)
    fun removeChannel(id: UUID): SignalDefinition {
        val definition = definition(id)
        states[definition.id]?.apply { pulseEndTick = null; value = 0 }
        backend().setValue(definition.name, 0)
        backend().removeChannel(definition.name)
        definitions.remove(definition); states.remove(definition.id); changed()
        return definition
    }

    fun renameChannel(rawOldName: String, rawNewName: String): SignalDefinition =
        renameChannel(definition(rawOldName).id, rawNewName)

    fun renameChannel(id: UUID, rawNewName: String): SignalDefinition {
        val old = definition(id); val name = checkedName(rawNewName)
        require(definitions.none { it.id != id && it.name == name }) { "$subject '$name' already exists" }
        if (old.name == name) return old
        val state = states[id] ?: State()
        backend().renameChannel(old.name, name, state.value)
        return old.copy(name = name).also { definitions[definitions.indexOf(old)] = it; changed() }
    }

    fun exists(rawName: String): Boolean = definitions.any { it.name == rawName.trim() }
    fun value(rawName: String): Int = states[definition(rawName).id]?.value ?: 0

    fun setValue(rawName: String, value: Int) {
        requireEnabled(); require(value in 0..15) { "Signal must be between 0 and 15" }
        val definition = definition(rawName); val state = states.getOrPut(definition.id, ::State)
        state.pulseEndTick = null
        if (state.value == value) return
        state.value = value; backend().setValue(definition.name, value)
    }

    fun pulse(rawName: String, durationTicks: Int, value: Int) {
        requireEnabled()
        require(durationTicks in 1..maxPulseTicks) { "Signal pulse duration must be between 1 and $maxPulseTicks ticks" }
        require(value in 1..15) { "Signal pulse value must be between 1 and 15" }
        val definition = definition(rawName); val state = states.getOrPut(definition.id, ::State)
        state.value = value; state.pulseEndTick = clock() + durationTicks
        backend().setValue(definition.name, value)
    }

    fun reset(rawName: String) {
        val definition = definition(rawName); val state = states.getOrPut(definition.id, ::State)
        state.pulseEndTick = null
        if (state.value == 0) return
        state.value = 0; backend().setValue(definition.name, 0)
    }

    fun resetAll() = resetAllInternal()
    fun snapshot(): List<SignalView> = definitions.map { definition ->
        SignalView(definition.id, definition.name, states[definition.id]?.value ?: 0, backend().connectionCount(definition.name))
    }
    fun backendName(): String = backend().name
    fun isOutputEnabled(): Boolean = outputEnabled

    fun tick() {
        if (!authority()) {
            if (outputEnabled || states.values.any { it.value != 0 || it.pulseEndTick != null }) resetAllInternal()
            outputEnabled = false
            return
        }
        outputEnabled = true
        val now = clock()
        definitions.forEach { definition ->
            val state = states[definition.id] ?: return@forEach
            val end = state.pulseEndTick ?: return@forEach
            if (now < end) return@forEach
            state.pulseEndTick = null
            if (state.value != 0) { state.value = 0; backend().setValue(definition.name, 0) }
        }
    }

    override fun close() {
        resetAllInternal(); backend?.close(); backend = null; outputEnabled = false
    }

    private fun resetAllInternal() {
        states.values.forEach { it.value = 0; it.pulseEndTick = null }
        backend?.clearSignals()
    }
    private fun requireEnabled() = check(outputEnabled) { "Signal outputs are disabled" }
    private fun definition(rawName: String): SignalDefinition {
        val name = rawName.trim()
        return definitions.firstOrNull { it.name == name } ?: throw NoSuchElementException("Unknown signal channel '$name'")
    }
    private fun definition(id: UUID): SignalDefinition = definitions.firstOrNull { it.id == id }
        ?: throw NoSuchElementException("Unknown signal channel '$id'")
    private fun checkedName(raw: String): String = raw.trim().also { require(validName(it)) { "Invalid $subject '${it}'" } }
    private fun backend(): SignalBackend = backend ?: backendFactory().also { backend = it }

    companion object { private val DEFAULT_NAME = Regex("[a-z][a-z0-9_-]{0,31}") }
}
