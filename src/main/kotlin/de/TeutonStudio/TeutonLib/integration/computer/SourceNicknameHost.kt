package de.TeutonStudio.TeutonLib.integration.computer

/** Injected into block entities so source nicknames are saved with their owning computer. */
interface SourceNicknameHost {
    fun `teutonlib$sourceNicknames`(): SourceNicknameBank
}
