package de.TeutonStudio.TeutonLib.integration.peripheral

import java.util.UUID

/** Persisted on the target block, independently of CC's computer number.  */
interface LinkIdentity {
    fun `teutonlib$linkTarget`(): UUID?
    fun `teutonlib$linkTarget`(identity: UUID?)
}
