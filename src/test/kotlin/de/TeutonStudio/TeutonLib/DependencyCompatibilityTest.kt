package de.TeutonStudio.TeutonLib

import java.util.jar.JarEntry

import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.HashMap
import java.util.jar.JarFile
import java.util.regex.Pattern
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/** These targets use reflection or Mixin strings, so Kotlin compilation cannot check them.  */
class DependencyCompatibilityTest {
    @Test
    @Throws(IOException::class)
    fun simulatedDockingApiRemainsPublicAndCompatible() {
        val methods: kotlin.collections.MutableMap<kotlin.String, kotlin.Int> =
            DependencyCompatibilityTest.Companion.methods(DependencyCompatibilityTest.Companion.DOCK)
        for (signature in kotlin.arrayOf<kotlin.String>(
            "getOtherConnector()L" + DependencyCompatibilityTest.Companion.DOCK + ";",
            "isLocked()Z",
            "tick()V",
            "unDock()V"
        )) {
            assertTrue(methods.containsKey(signature), "Missing Simulated docking method: " + signature)
            assertTrue((methods.get(signature)!! and Opcodes.ACC_PUBLIC) != 0, signature + " must be public")
            assertEquals(0, methods.get(signature)!! and Opcodes.ACC_STATIC, signature + " must be an instance method")
        }
    }

    @Test
    @Throws(IOException::class)
    fun createKineticMixinTargetsKeepTheirSignatures() {
        val methods: kotlin.collections.MutableMap<kotlin.String, kotlin.Int> =
            DependencyCompatibilityTest.Companion.methods("com/simibubi/create/content/kinetics/RotationPropagator")
        for (signature in kotlin.arrayOf<kotlin.String>(
            "getRotationSpeedModifier(" + DependencyCompatibilityTest.Companion.KINETIC + DependencyCompatibilityTest.Companion.KINETIC + ")F",
            "getConnectedNeighbours(" + DependencyCompatibilityTest.Companion.KINETIC + ")Ljava/util/List;",
            "getPotentialNeighbourLocations(" + DependencyCompatibilityTest.Companion.KINETIC + ")Ljava/util/List;"
        )) {
            assertTrue(methods.containsKey(signature), "Missing Create kinetic method: " + signature)
            assertTrue((methods.get(signature)!! and Opcodes.ACC_STATIC) != 0, signature + " must be static")
        }
    }

    @Test
    @Throws(IOException::class)
    fun createClientPhaseMixinTargetKeepsItsSignature() {
        val methods: kotlin.collections.MutableMap<kotlin.String, kotlin.Int> =
            DependencyCompatibilityTest.Companion.methods("com/simibubi/create/content/kinetics/base/KineticBlockEntityVisual")
        val signature =
            "rotationOffset(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction\$Axis;Lnet/minecraft/core/Vec3i;)F"
        assertTrue(methods.containsKey(signature), "Missing Create rendering method: " + signature)
        assertTrue((methods.get(signature)!! and Opcodes.ACC_STATIC) != 0)
    }

    @Test
    @Throws(IOException::class)
    fun gearRegistrationMixinTargetsKeepTheirSignatures() {
        val methods: kotlin.collections.MutableMap<kotlin.String, kotlin.Int> =
            DependencyCompatibilityTest.Companion.methods("com/simibubi/create/content/kinetics/base/KineticBlockEntity")
        assertTrue(methods.containsKey("initialize()V"))
        assertTrue(methods.containsKey("remove()V"))
    }

    @Test
    @Throws(IOException::class)
    fun flywheelSupportsRecreatingExistingVisuals() {
        var methods: kotlin.collections.MutableMap<kotlin.String, kotlin.Int> =
            DependencyCompatibilityTest.Companion.methods("dev/engine_room/flywheel/api/visualization/VisualizationManager")
        assertTrue(methods.containsKey("supportsVisualization(Lnet/minecraft/world/level/LevelAccessor;)Z"))
        assertTrue(methods.containsKey("get(Lnet/minecraft/world/level/LevelAccessor;)Ldev/engine_room/flywheel/api/visualization/VisualizationManager;"))
        assertTrue(methods.containsKey("blockEntities()Ldev/engine_room/flywheel/api/visualization/VisualManager;"))
        methods =
            DependencyCompatibilityTest.Companion.methods("dev/engine_room/flywheel/api/visualization/VisualManager")
        assertTrue(methods.containsKey("queueRemove(Ljava/lang/Object;)V"))
        assertTrue(methods.containsKey("queueAdd(Ljava/lang/Object;)V"))
    }

    @Test
    @Throws(IOException::class)
    fun ccAccessMixinTargetsKeepTransferAndLifecycleMethods() {
        for (target in kotlin.arrayOf<kotlin.String>(
            "dan200/computercraft/shared/computer/core/ComputerSystem",
            "dan200/computercraft/core/apis/PeripheralAPI\$PeripheralWrapper",
            "dan200/computercraft/shared/peripheral/modem/wired/WiredModemPeripheral\$RemotePeripheralWrapper"
        )) {
            val signatures: kotlin.collections.MutableMap<kotlin.String, kotlin.Int> =
                DependencyCompatibilityTest.Companion.methods(target)
            assertTrue(signatures.containsKey("getAvailablePeripherals()Ljava/util/Map;"), target)
            assertTrue(
                signatures.containsKey("getAvailablePeripheral(Ljava/lang/String;)Ldan200/computercraft/api/peripheral/IPeripheral;"),
                target
            )
        }
    }

    @Test
    @Throws(IOException::class)
    fun wirelessAndSableGeometryTargetsRemainCompatible() {
        val wireless = methods("dan200/computercraft/shared/peripheral/modem/wireless/WirelessNetwork")
        assertTrue(wireless.containsKey(
            "tryTransmit(Ldan200/computercraft/api/network/PacketReceiver;Ldan200/computercraft/api/network/Packet;DZ)V"
        ))
        val sableWireless = methods("dev/ryanhcode/sable/mixin/compatibility/computercraft/WirelessNetworkMixin")
        assertTrue(sableWireless.containsKey(
            "getPosition(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ldan200/computercraft/api/network/PacketReceiver;)D"
        ))
        val modem = methods("dan200/computercraft/shared/peripheral/modem/wireless/WirelessModemPeripheral")
        assertTrue(modem.containsKey("getRange()D"))
        assertTrue(modem.containsKey("getPosition()Lnet/minecraft/world/phys/Vec3;") ||
            methods("dan200/computercraft/api/network/PacketSender")
                .containsKey("getPosition()Lnet/minecraft/world/phys/Vec3;"))

        val sable = methods("dev/ryanhcode/sable/ActiveSableCompanion")
        assertTrue(sable.containsKey(
            "projectOutOfSubLevel(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
        ))
        assertTrue(sable.containsKey(
            "distanceSquaredWithSubLevels(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Position;Lnet/minecraft/core/Position;)D"
        ))
    }

    @Test
    @Throws(IOException::class)
    fun helicopterPeripheralTargetsMatchInstalledAeronauticsBundle() {
        val gyro = DependencyCompatibilityTest.methods(
            "dev/eriksonn/aeronautics/content/blocks/propeller/bearing/gyroscopic_propeller_bearing/GyroscopicPropellerBearingBlockEntity"
        )
        assertTrue(gyro.containsKey("setTilt(Lorg/joml/Vector3d;Lorg/joml/Vector3d;D)V"))
        val gimbal = DependencyCompatibilityTest.methods(
            "dev/simulated_team/simulated/compat/computercraft/peripherals/GimbalSensorPeripheral"
        )
        assertTrue(gimbal.containsKey("getAngles()Ljava/util/List;"))
        assertTrue(gimbal.containsKey("getAnglesRad()Ljava/util/List;"))
        assertEquals(0, gimbal.getValue("getAngles()Ljava/util/List;") and Opcodes.ACC_FINAL)
        assertEquals(0, gimbal.getValue("getAnglesRad()Ljava/util/List;") and Opcodes.ACC_FINAL)
        val sensor = DependencyCompatibilityTest.methods(
            "dev/simulated_team/simulated/content/blocks/gimbal_sensor/GimbalSensorBlockEntity"
        )
        assertTrue(sensor.containsKey("getXAngle()D"))
        assertTrue(sensor.containsKey("getZAngle()D"))
        assertTrue(DependencyCompatibilityTest.methods("dev/ryanhcode/sable/sublevel/SubLevel")
            .containsKey("logicalPose()Ldev/ryanhcode/sable/companion/math/Pose3d;"))
    }

    @Test
    @Throws(IOException::class)
    fun opticalSensorExposesItsLocalLaserDirection() {
        val peripheral = DependencyCompatibilityTest.methods(
            "dev/simulated_team/simulated/compat/computercraft/peripherals/OpticalSensorPeripheral"
        )
        assertTrue(peripheral.containsKey("getType()Ljava/lang/String;"))
        val sensor = DependencyCompatibilityTest.methods(
            "dev/simulated_team/simulated/content/blocks/lasers/optical_sensor/OpticalSensorBlockEntity"
        )
        assertTrue(sensor.containsKey("getDirection()Lnet/minecraft/core/Direction;"))
        assertTrue(sensor.getValue("getDirection()Lnet/minecraft/core/Direction;") and Opcodes.ACC_PUBLIC != 0)
    }

    companion object {
        private const val DOCK =
            "dev/simulated_team/simulated/content/blocks/docking_connector/DockingConnectorBlockEntity"
        private const val KINETIC = "Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;"

        @Throws(IOException::class)
        @JvmStatic
        private fun methods(className: kotlin.String?): kotlin.collections.MutableMap<kotlin.String, kotlin.Int> {
            val result: kotlin.collections.MutableMap<kotlin.String, kotlin.Int> =
                HashMap<kotlin.String, kotlin.Int>()
            var matches = 0
            for (file in java.lang.System.getProperty("teutonlib.compatibilityJars")
                .split(java.util.regex.Pattern.quote(java.io.File.pathSeparator).toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()) {
                JarFile(java.nio.file.Path.of(file).toFile()).use { jar ->
                    val entry: JarEntry? = jar.getJarEntry(className + ".class")
                    if (entry == null) continue
                    matches++
                    jar.getInputStream(entry).use { stream ->
                        org.objectweb.asm.ClassReader(stream).accept(
                            object : org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
                                override fun visitMethod(
                                    access: kotlin.Int,
                                    name: kotlin.String,
                                    descriptor: kotlin.String,
                                    signature: kotlin.String?,
                                    exceptions: kotlin.Array<kotlin.String>?
                                ): org.objectweb.asm.MethodVisitor? {
                                    result.put(name + descriptor, access)
                                    return null
                                }
                            },
                            org.objectweb.asm.ClassReader.SKIP_CODE or org.objectweb.asm.ClassReader.SKIP_DEBUG or org.objectweb.asm.ClassReader.SKIP_FRAMES
                        )
                    }
                }
            }
            assertEquals(1, matches, "Expected exactly one compile dependency containing " + className)
            return result
        }
    }
}
