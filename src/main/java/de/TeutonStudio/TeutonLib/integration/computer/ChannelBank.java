package de.TeutonStudio.TeutonLib.integration.computer;

import java.util.*;

/** Persistent definitions and output values. All mutations run on the server thread. */
public final class ChannelBank {
    public static final int LIMIT = 64;
    public record Entry(String name, String direction, String side, boolean published, int value) {}
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    public List<Entry> entries() { return List.copyOf(entries.values()); }
    public static String name(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.-]{1,32}") || value.equals("world"))
            throw new IllegalArgumentException("Channel name must contain 1-32 letters, digits, _, . or -; 'world' is reserved");
        return value;
    }
    public Entry find(String name) { return entries.get(name); }
    public Entry get(String name) {
        Entry entry = entries.get(name);
        if (entry == null) throw new IllegalArgumentException("Unknown channel '" + name + "'");
        return entry;
    }
    public void create(String name, String direction, String side) {
        name(name);
        if (entries.size() >= LIMIT) throw new IllegalArgumentException("Channel limit reached (64)");
        if (entries.containsKey(name)) throw new IllegalArgumentException("Channel already exists");
        if (!direction.equals("input") && !direction.equals("output")) throw new IllegalArgumentException("Direction must be input or output");
        if (direction.equals("input") && !Set.of("front", "back", "left", "right", "top", "bottom").contains(side))
            throw new IllegalArgumentException("Input requires a computer side");
        entries.put(name, new Entry(name, direction, direction.equals("input") ? side : "", false, 0));
    }
    public Entry remove(String name) { Entry previous = get(name); entries.remove(name); return previous; }
    public void rename(String oldName, String newName) {
        name(newName); Entry previous = get(oldName);
        if (oldName.equals(newName)) return;
        if (entries.containsKey(newName)) throw new IllegalArgumentException("Channel already exists");
        entries.remove(oldName); entries.put(newName, new Entry(newName, previous.direction(), previous.side(), previous.published(), previous.value()));
    }
    public void published(String name, boolean published) {
        Entry e = get(name); entries.put(name, new Entry(name, e.direction(), e.side(), published, e.value()));
    }
    public void set(String name, int value) {
        Entry e = get(name);
        if (!e.direction().equals("output")) throw new IllegalArgumentException("Input channels are read-only");
        if (value < 0 || value > 15) throw new IllegalArgumentException("Signal must be an integer from 0 to 15");
        entries.put(name, new Entry(name, e.direction(), e.side(), e.published(), value));
    }
    public void restore(Entry e) {
        if (e.direction().equals("output") && (e.value() < 0 || e.value() > 15)) throw new IllegalArgumentException("Invalid saved output");
        create(e.name(), e.direction(), e.side()); published(e.name(), e.published());
        if (e.direction().equals("output")) set(e.name(), e.value());
    }
}
