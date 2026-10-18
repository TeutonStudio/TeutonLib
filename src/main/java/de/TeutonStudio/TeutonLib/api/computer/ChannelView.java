package de.TeutonStudio.TeutonLib.api.computer;
public record ChannelView(String name, String direction, String side, boolean published, int value,
                          int connections, String provider, boolean editable) {}
