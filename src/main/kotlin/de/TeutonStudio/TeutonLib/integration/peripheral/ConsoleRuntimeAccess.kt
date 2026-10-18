package de.TeutonStudio.TeutonLib.integration.peripheral


interface ConsoleRuntimeAccess {
    fun `teutonlib$devices`(): MutableMap<String, LinkDevice>
    fun `teutonlib$activate`()
    fun `teutonlib$refresh`()
}
