package de.TeutonStudio.TeutonLib.integration.computer;
/** Injected into block entities; addon owners can reuse the same persistent bank. */
public interface ChannelHost {
    ChannelBank teutonlib$channels();
}
