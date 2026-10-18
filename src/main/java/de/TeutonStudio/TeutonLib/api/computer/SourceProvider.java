package de.TeutonStudio.TeutonLib.api.computer;
import java.util.List;
@FunctionalInterface
public interface SourceProvider { List<SourceView> sources(ComputerContext computer); }
