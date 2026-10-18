package de.TeutonStudio.TeutonLib.api.computer

/** Register once during common setup; callbacks run on the server thread. */
fun interface SourceProvider { fun sources(computer: ComputerContext): List<SourceView> }
