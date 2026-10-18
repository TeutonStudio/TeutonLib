package de.TeutonStudio.TeutonLib.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Does not link any optional mod classes; shared by early Mixin selection and runtime. */
public final class CsoCompatibility {
    private static final Map<String, String> SUPPORTED = Map.of(
            "create", "6.0.10", "sable", "2.0.5", "simulated", "1.3.2");

    private CsoCompatibility() {}

    public static List<String> problems(Map<String, String> versions) {
        List<String> problems = new ArrayList<>();
        for (String id : List.of("create", "sable", "simulated")) {
            String actual = versions.get(id);
            if (actual == null) problems.add(id + " fehlt (benötigt " + SUPPORTED.get(id) + ")");
            else if (!SUPPORTED.get(id).equals(actual))
                problems.add(id + " " + actual + " wird nicht unterstützt (benötigt " + SUPPORTED.get(id) + ")");
        }
        return List.copyOf(problems);
    }
}
