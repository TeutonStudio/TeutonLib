package de.TeutonStudio.TeutonLib.integration;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CsoCompatibilityTest {
    private static Map<String, String> supported() {
        return new HashMap<>(Map.of("create", "6.0.10", "sable", "2.0.5", "simulated", "1.3.2"));
    }

    @Test void completeSupportedSetIsAvailable() {
        assertTrue(CsoCompatibility.problems(supported()).isEmpty());
    }
    @Test void noOptionalModsReportsAllMissingDependencies() {
        assertEquals(3, CsoCompatibility.problems(Map.of()).size());
    }
    @Test void eachMissingDependencyDisablesTheWholeIntegration() {
        for (String id : supported().keySet()) {
            var versions = supported(); versions.remove(id);
            var problems = CsoCompatibility.problems(versions);
            assertEquals(1, problems.size());
            assertTrue(problems.getFirst().contains(id + " fehlt"));
        }
    }
    @Test void untestedVersionsAreRejectedWithoutLinkingTheirClasses() {
        for (String id : supported().keySet()) {
            var versions = supported(); versions.put(id, "999.0.0");
            assertEquals(1, CsoCompatibility.problems(versions).size());
            assertTrue(CsoCompatibility.problems(versions).getFirst().contains("999.0.0"));
        }
    }
}
