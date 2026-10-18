package de.TeutonStudio.TeutonLib.api.computer;

/** Nullable positions mean the peripheral does not expose a physical location. */
public record SourceView(String name, java.util.List<String> types, String connection,
                         String devicePosition, String linkPosition) {
    public SourceView { types = java.util.List.copyOf(types); }
}
