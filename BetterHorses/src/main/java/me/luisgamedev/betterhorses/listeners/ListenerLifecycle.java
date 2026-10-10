package me.luisgamedev.betterhorses.listeners;

/** Optional lifecycle hooks for listeners that own tasks or cached configuration. */
public interface ListenerLifecycle {
    default void onConfigurationReload() {
    }

    default void shutdown() {
    }
}
