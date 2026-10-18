package de.TeutonStudio.TeutonLib.api.computer

@JvmRecord
data class ChannelView(val name: String, val direction: String, val side: String, val published: Boolean,
                       val value: Int, val connections: Int, val provider: String, val editable: Boolean)
