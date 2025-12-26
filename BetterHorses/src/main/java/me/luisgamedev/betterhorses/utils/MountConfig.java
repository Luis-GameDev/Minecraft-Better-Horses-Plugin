package me.luisgamedev.betterhorses.utils;

import org.bukkit.configuration.file.FileConfiguration;

public final class MountConfig {

    private MountConfig() {}

    public static double getMutationFactor(FileConfiguration config, SupportedMountType mountType, String attribute) {
        String overridePath = "mutation-factor." + mountType.getConfigKey() + "." + attribute;
        String basePath = "mutation-factor." + attribute;
        if (config.contains(overridePath)) {
            return config.getDouble(overridePath);
        }
        return config.getDouble(basePath);
    }

    public static double getMaxStat(FileConfiguration config, SupportedMountType mountType, String attribute) {
        String overridePath = "max-stats." + mountType.getConfigKey() + "." + attribute;
        String basePath = "max-stats." + attribute;
        if (config.contains(overridePath)) {
            return config.getDouble(overridePath);
        }
        return config.getDouble(basePath);
    }

    public static boolean isGrowthEnabled(FileConfiguration config, SupportedMountType mountType) {
        if (!config.getBoolean("horse-growth-settings.enabled")) {
            return false;
        }
        if (mountType == SupportedMountType.HORSE) {
            return true;
        }
        return config.getBoolean("horse-growth-settings.mount-types." + mountType.getConfigKey() + ".enabled", true);
    }
}
