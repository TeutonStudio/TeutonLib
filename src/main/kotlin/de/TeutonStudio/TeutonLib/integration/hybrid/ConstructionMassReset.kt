package de.TeutonStudio.TeutonLib.integration.hybrid

/** Sable normally deletes a body after its final block. Construction can reuse
 * that empty plot, so its incremental mass accumulator must be reset as well. */
interface ConstructionMassReset {
    fun `teutonlib$resetEmptyMass`()
}
