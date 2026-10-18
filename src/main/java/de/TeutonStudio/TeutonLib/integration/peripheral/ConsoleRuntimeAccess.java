package de.TeutonStudio.TeutonLib.integration.peripheral;

import java.util.Map;

public interface ConsoleRuntimeAccess {
    Map<String, LinkDevice> teutonlib$devices();
    void teutonlib$activate();
    void teutonlib$refresh();
}
