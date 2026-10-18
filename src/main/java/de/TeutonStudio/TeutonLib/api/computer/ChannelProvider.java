package de.TeutonStudio.TeutonLib.api.computer;
import java.util.List;
/** Additional provider channels must have unique names; foreign channels are read-only in the common editor. */
@FunctionalInterface
public interface ChannelProvider { List<ChannelView> channels(ComputerContext computer); }
