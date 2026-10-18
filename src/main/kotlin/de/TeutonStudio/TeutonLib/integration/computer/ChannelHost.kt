package de.TeutonStudio.TeutonLib.integration.computer


/** Injected into block entities; addon owners can reuse the same persistent bank.  */
interface ChannelHost {
    fun `teutonlib$channels`(): ChannelBank
}
