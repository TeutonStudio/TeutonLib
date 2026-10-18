package de.TeutonStudio.TeutonLib.integration;

import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;
import net.neoforged.fml.loading.LoadingModList;

/** LoadingModList is available before optional target classes are transformed. */
public final class CsoAvailability {
    private CsoAvailability() {}

    public static List<String> problems() {
        var mods = LoadingModList.get();
        if (mods == null) return List.of("Modliste ist noch nicht verfügbar");
        Map<String, String> versions = mods.getMods().stream().collect(Collectors.toMap(
                mod -> mod.getModId(), mod -> mod.getVersion().toString()));
        return CsoCompatibility.problems(versions);
    }

    public static boolean available() {
        return problems().isEmpty();
    }
}
