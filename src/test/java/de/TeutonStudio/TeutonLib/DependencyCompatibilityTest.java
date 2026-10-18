package de.TeutonStudio.TeutonLib;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** These targets use reflection or Mixin strings, so Kotlin compilation cannot check them. */
class DependencyCompatibilityTest {
    private static final String DOCK = "dev/simulated_team/simulated/content/blocks/docking_connector/DockingConnectorBlockEntity";
    private static final String KINETIC = "Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;";

    @Test
    void simulatedDockingApiRemainsPublicAndCompatible() throws IOException {
        Map<String, Integer> methods = methods(DOCK);
        for (String signature : new String[]{"getOtherConnector()L" + DOCK + ";", "isLocked()Z", "tick()V", "unDock()V"}) {
            assertTrue(methods.containsKey(signature), "Missing Simulated docking method: " + signature);
            assertTrue((methods.get(signature) & Opcodes.ACC_PUBLIC) != 0, signature + " must be public");
            assertEquals(0, methods.get(signature) & Opcodes.ACC_STATIC, signature + " must be an instance method");
        }
    }

    @Test
    void createKineticMixinTargetsKeepTheirSignatures() throws IOException {
        Map<String, Integer> methods = methods("com/simibubi/create/content/kinetics/RotationPropagator");
        for (String signature : new String[]{
                "getRotationSpeedModifier(" + KINETIC + KINETIC + ")F",
                "getConnectedNeighbours(" + KINETIC + ")Ljava/util/List;",
                "getPotentialNeighbourLocations(" + KINETIC + ")Ljava/util/List;"}) {
            assertTrue(methods.containsKey(signature), "Missing Create kinetic method: " + signature);
            assertTrue((methods.get(signature) & Opcodes.ACC_STATIC) != 0, signature + " must be static");
        }
    }

    @Test
    void createClientPhaseMixinTargetKeepsItsSignature() throws IOException {
        Map<String, Integer> methods = methods("com/simibubi/create/content/kinetics/base/KineticBlockEntityVisual");
        String signature = "rotationOffset(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction$Axis;Lnet/minecraft/core/Vec3i;)F";
        assertTrue(methods.containsKey(signature), "Missing Create rendering method: " + signature);
        assertTrue((methods.get(signature) & Opcodes.ACC_STATIC) != 0);
    }

    @Test
    void gearRegistrationMixinTargetsKeepTheirSignatures() throws IOException {
        Map<String, Integer> methods = methods("com/simibubi/create/content/kinetics/base/KineticBlockEntity");
        assertTrue(methods.containsKey("initialize()V"));
        assertTrue(methods.containsKey("remove()V"));
    }

    @Test
    void flywheelSupportsRecreatingExistingVisuals() throws IOException {
        Map<String, Integer> methods = methods("dev/engine_room/flywheel/api/visualization/VisualizationManager");
        assertTrue(methods.containsKey("supportsVisualization(Lnet/minecraft/world/level/LevelAccessor;)Z"));
        assertTrue(methods.containsKey("get(Lnet/minecraft/world/level/LevelAccessor;)Ldev/engine_room/flywheel/api/visualization/VisualizationManager;"));
        assertTrue(methods.containsKey("blockEntities()Ldev/engine_room/flywheel/api/visualization/VisualManager;"));
        methods = methods("dev/engine_room/flywheel/api/visualization/VisualManager");
        assertTrue(methods.containsKey("queueRemove(Ljava/lang/Object;)V"));
        assertTrue(methods.containsKey("queueAdd(Ljava/lang/Object;)V"));
    }

    private static Map<String, Integer> methods(String className) throws IOException {
        Map<String, Integer> result = new HashMap<>();
        int matches = 0;
        for (String file : System.getProperty("teutonlib.compatibilityJars").split(Pattern.quote(File.pathSeparator))) {
            try (JarFile jar = new JarFile(Path.of(file).toFile())) {
                var entry = jar.getJarEntry(className + ".class");
                if (entry == null) continue;
                matches++;
                try (var stream = jar.getInputStream(entry)) {
                    new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            result.put(name + descriptor, access);
                            return null;
                        }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
            }
        }
        assertEquals(1, matches, "Expected exactly one compile dependency containing " + className);
        return result;
    }

    @Test
    void ccAccessMixinTargetsKeepTransferAndLifecycleMethods() throws IOException {
        for (String target : new String[]{
                "dan200/computercraft/shared/computer/core/ComputerSystem",
                "dan200/computercraft/core/apis/PeripheralAPI$PeripheralWrapper",
                "dan200/computercraft/shared/peripheral/modem/wired/WiredModemPeripheral$RemotePeripheralWrapper"}) {
            var signatures = methods(target);
            assertTrue(signatures.containsKey("getAvailablePeripherals()Ljava/util/Map;"), target);
            assertTrue(signatures.containsKey("getAvailablePeripheral(Ljava/lang/String;)Ldan200/computercraft/api/peripheral/IPeripheral;"), target);
        }
    }

    @Test
    void consoleGraphAndBindingMixinTargetsRemainCompatible() throws IOException {
        String prefix = "de/teutonstudio/ccaeroworks/computer/";
        assertTrue(methods(prefix + "PeripheralNetworkBuilder").containsKey(
                "build(L" + prefix + "ConsoleComputer;)L" + prefix + "PeripheralNetworkGraph;"));
        var runtime = methods(prefix + "PeripheralNetworkRuntime");
        assertTrue(runtime.containsKey("graph(Z)L" + prefix + "PeripheralNetworkGraph;"));
        assertTrue(runtime.containsKey("invalidateGraph(Z)V"));
        assertTrue(runtime.containsKey("queueDetached(L" + prefix + "PeripheralNetworkNode;)V"));
        assertTrue(runtime.containsKey("describePeripheral(L" + prefix + "PeripheralNetworkNode;)Ljava/util/Map;"));
        var binding = methods(prefix + "PeripheralBinding");
        assertTrue(binding.containsKey("checkAttached()V"));
        assertTrue(binding.containsKey("checkValid()Z"));
        assertTrue(binding.containsKey("close()V"));
        assertTrue(methods(prefix + "PeripheralTreeKt").containsKey(
                "describeTree(L" + prefix + "PeripheralNetworkRuntime;)Ljava/util/Map;"));
    }
}
