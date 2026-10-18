package de.TeutonStudio.TeutonLib.api.computer

/** Register once during common setup; callbacks run on the server thread. */
fun interface ChannelProvider { fun channels(computer: ComputerContext): List<ChannelView> }
