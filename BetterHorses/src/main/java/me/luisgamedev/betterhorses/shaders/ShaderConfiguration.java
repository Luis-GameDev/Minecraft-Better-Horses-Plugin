package me.luisgamedev.betterhorses.shaders;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/** Shader selectors are literal YAML keys, including dots in placeholders and permissions. */
public final class ShaderConfiguration {
    private ShaderConfiguration() { }

    static ConfigurationSection loadRules(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.options().pathSeparator('\u0001');
        config.load(file);
        ConfigurationSection feature = config.getConfigurationSection("postprocessing-shaders");
        ConfigurationSection rules = feature == null ? null : feature.getConfigurationSection("rules");
        return rules == null ? config.getConfigurationSection("postprocessing-shaders.rules") : rules;
    }

    /** Preserve literal selector keys when Bukkit's normal config updater writes new defaults. */
    public static void preserveRules(YamlConfiguration updated, File original)
            throws IOException, InvalidConfigurationException {
        ConfigurationSection rules = loadRules(original);
        if (rules != null) updated.set("postprocessing-shaders.rules", rules);
    }
}
