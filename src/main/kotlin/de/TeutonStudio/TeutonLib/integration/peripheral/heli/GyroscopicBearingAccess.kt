package de.TeutonStudio.TeutonLib.integration.peripheral.heli

import de.TeutonStudio.TeutonLib.integration.peripheral.LuaTiltTarget
import org.joml.Vector3d
import org.joml.Vector3dc

interface GyroscopicBearingAccess : LuaTiltTarget {
    fun `teutonlib$currentTiltDirection`(): Vector3d
    fun `teutonlib$activeLuaTiltTarget`(): Vector3d?
    fun `teutonlib$setLuaTiltTarget`(target: Vector3dc)
}
