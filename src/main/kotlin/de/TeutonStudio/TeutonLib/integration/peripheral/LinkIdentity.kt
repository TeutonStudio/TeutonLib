package de.TeutonStudio.TeutonLib.integration.peripheral

import java.util.UUID

/** Persisted on the target block, independently of CC's computer number.  */
interface LinkIdentity {
    fun `teutonlib$linkAntennaSides`(): Int
    fun `teutonlib$linkAntennaSides`(mask: Int)
    fun `teutonlib$linkAntennaSide`(): net.minecraft.core.Direction
    fun `teutonlib$linkAntennaSide`(side: net.minecraft.core.Direction)
    fun `teutonlib$linkTarget`(): UUID?
    fun `teutonlib$linkTarget`(identity: UUID?)
}
