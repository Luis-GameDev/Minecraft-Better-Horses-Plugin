package me.luisgamedev.betterhorses.utils;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.configuration.ConfigurationSection;

/** Global gate for creating newly neutered mounts or items; existing data is retained. */
public final class NeuteringPolicy {
    private NeuteringPolicy() { }

    public static boolean isEnabled(ConfigurationSection configuration) {
        return configuration.getBoolean("horse-neutering.enabled", true);
    }

    public static boolean isEnabled() {
        return isEnabled(BetterHorses.getInstance().getConfig());
    }
}
