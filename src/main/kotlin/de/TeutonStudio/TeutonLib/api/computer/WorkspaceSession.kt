package de.TeutonStudio.TeutonLib.api.computer

import java.util.UUID

/** Response identity, independent of client classes and safe to use in protocol tests.  */
@JvmRecord
data class WorkspaceSession(val menu: Int, val session: UUID?, val backend: String?, val page: Int) {
    fun accepts(response: WorkspaceSession?): Boolean {
        return page != 0 && equals(response)
    }
}
