package me.luisgamedev.betterhorses.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MountConfigTest {
    @Test void scalarMountSettingsControlCustomGrowth() {
        YamlConfiguration config = enabledConfig();
        for (SupportedMountType type : SupportedMountType.values()) {
            if (type == SupportedMountType.HORSE) continue;
            String path = "horse-growth-settings.mount-types." + type.getConfigKey();
            config.set(path, false);
            assertFalse(MountConfig.isGrowthEnabled(config, type), type.name());
            config.set(path, true);
            assertTrue(MountConfig.isGrowthEnabled(config, type), type.name());
        }
    }

    @Test void legacyNestedMountSettingsRemainSupported() {
        YamlConfiguration config = enabledConfig();
        config.set("horse-growth-settings.mount-types.camels.enabled", false);
        assertFalse(MountConfig.isGrowthEnabled(config, SupportedMountType.CAMEL));
        config.set("horse-growth-settings.mount-types.camels.enabled", true);
        assertTrue(MountConfig.isGrowthEnabled(config, SupportedMountType.CAMEL));
    }

    @Test void explicitScalarWinsOverLegacyDefaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("horse-growth-settings.mount-types.camels.enabled", true);
        YamlConfiguration config = enabledConfig();
        config.setDefaults(defaults);
        config.set("horse-growth-settings.mount-types.camels", false);
        assertFalse(MountConfig.isGrowthEnabled(config, SupportedMountType.CAMEL));
    }

    @Test void disablingGrowthOverridesEveryMountSetting() {
        YamlConfiguration config = enabledConfig();
        config.set("horse-growth-settings.mount-types.camels", true);
        config.set("horse-growth-settings.mount-types.llamas.enabled", true);
        config.set("horse-growth-settings.enabled", false);
        for (SupportedMountType type : SupportedMountType.values()) {
            assertFalse(MountConfig.isGrowthEnabled(config, type), type.name());
        }
    }

    @Test void missingMountOverrideKeepsGrowthEnabled() {
        YamlConfiguration config = enabledConfig();
        for (SupportedMountType type : SupportedMountType.values()) {
            assertTrue(MountConfig.isGrowthEnabled(config, type), type.name());
        }
    }

    private static YamlConfiguration enabledConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("horse-growth-settings.enabled", true);
        return config;
    }
}
