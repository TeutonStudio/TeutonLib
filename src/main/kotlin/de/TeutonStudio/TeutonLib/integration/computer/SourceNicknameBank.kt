package de.TeutonStudio.TeutonLib.integration.computer

/** User-defined display names keyed by the immutable ComputerCraft peripheral address. */
class SourceNicknameBank {
    private val values = linkedMapOf<String, String>()

    fun get(source: String): String? = values[source]

    fun entries(): Map<String, String> = java.util.Map.copyOf(values)

    fun set(source: String, rawNickname: String) {
        require(source.isNotBlank() && source.length <= 480) { "Invalid peripheral address" }
        val nickname = rawNickname.trim()
        require(nickname.length <= 32) { "Nickname is too long" }
        require(nickname.none(Char::isISOControl)) { "Nickname contains control characters" }
        if (nickname.isEmpty()) values.remove(source) else values[source] = nickname
    }
}
