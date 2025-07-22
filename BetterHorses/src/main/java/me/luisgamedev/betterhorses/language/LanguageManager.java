package me.luisgamedev.betterhorses.language;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class LanguageManager {

    private final BetterHorses plugin;
    private FileConfiguration lang;

    public LanguageManager(BetterHorses plugin) {
        this.plugin = plugin;
        loadLanguageFile();
    }

    private void loadLanguageFile() {
        File file = new File(plugin.getDataFolder(), "language.yml");
        if (!file.exists()) {
            plugin.saveResource("language.yml", false);
        }
        lang = YamlConfiguration.loadConfiguration(file);
    }

    public String getRaw(String key) {
        return lang.getString(key, "&cMissing lang key: " + key);
    }

    public String get(String key) {
        String prefix = getRaw("prefix");
        return ChatColor.translateAlternateColorCodes('&', prefix + getRaw(key));
    }

    public String getFormatted(String key, Object... replacements) {
        String message = get(key);
        for (int i = 0; i < replacements.length - 1; i += 2) {
            String placeholder = String.valueOf(replacements[i]);
            String value = String.valueOf(replacements[i + 1]);
            message = message.replace(placeholder, value);
        }
        return message;
    }

    public String getFormattedRaw(String key, Object... replacements) {
        String message = getRaw(key);
        for (int i = 0; i < replacements.length - 1; i += 2) {
            String placeholder = String.valueOf(replacements[i]);
            String value = String.valueOf(replacements[i + 1]);
            message = message.replace(placeholder, value);
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }


    public FileConfiguration getConfig() {
        return lang;
    }

    public void reload() {
        loadLanguageFile();
    }
}
