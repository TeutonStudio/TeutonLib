package de.TeutonStudio.TeutonLib.integration.peripheral;

import java.util.UUID;

/** Persisted on the target block, independently of CC's computer number. */
public interface LinkIdentity {
    UUID teutonlib$linkTarget();
    void teutonlib$linkTarget(UUID identity);
}
