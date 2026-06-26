package me.luisgamedev.betterhorses.language;

import me.clip.placeholderapi.PlaceholderAPI;
import me.luisgamedev.betterhorses.BetterHorses;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;

public class LanguageManager {

    private static final Map<Character, String> LEGACY_MINIMESSAGE_TAGS = Map.ofEntries(
            Map.entry('0', "black"),
            Map.entry('1', "dark_blue"),
            Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"),
            Map.entry('4', "dark_red"),
            Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"),
            Map.entry('7', "gray"),
            Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"),
            Map.entry('a', "green"),
            Map.entry('b', "aqua"),
            Map.entry('c', "red"),
            Map.entry('d', "light_purple"),
            Map.entry('e', "yellow"),
            Map.entry('f', "white"),
            Map.entry('k', "obfuscated"),
            Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"),
            Map.entry('n', "underlined"),
            Map.entry('o', "italic")
    );

    private final BetterHorses plugin;
    private final BukkitAudiences audiences;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacySection();
    private FileConfiguration lang;

    public LanguageManager(BetterHorses plugin, BukkitAudiences audiences) {
        this.plugin = plugin;
        this.audiences = audiences;
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

    public String getRaw(OfflinePlayer player, String key) {
        return parseToString(player, getRaw(key));
    }

    public String get(String key) {
        return get(null, key);
    }

    public String get(OfflinePlayer player, String key) {
        return serialize(getComponent(player, key));
    }

    public Component getComponent(String key) {
        return getComponent(null, key);
    }

    public Component getComponent(OfflinePlayer player, String key) {
        String prefix = getRaw("prefix");
        return parse(player, prefix + getRaw(key));
    }

    public String getFormatted(String key, Object... replacements) {
        return getFormatted(null, key, replacements);
    }

    public String getFormatted(OfflinePlayer player, String key, Object... replacements) {
        return serialize(getFormattedComponent(player, key, replacements));
    }

    public Component getFormattedComponent(String key, Object... replacements) {
        return getFormattedComponent(null, key, replacements);
    }

    public Component getFormattedComponent(OfflinePlayer player, String key, Object... replacements) {
        String message = getRaw("prefix") + getRaw(key);
        return parse(player, replace(message, replacements));
    }

    public String getFormattedRaw(String key, Object... replacements) {
        return getFormattedRaw(null, key, replacements);
    }

    public String getFormattedRaw(OfflinePlayer player, String key, Object... replacements) {
        return serialize(getFormattedRawComponent(player, key, replacements));
    }

    public Component getFormattedRawComponent(String key, Object... replacements) {
        return getFormattedRawComponent(null, key, replacements);
    }

    public Component getFormattedRawComponent(OfflinePlayer player, String key, Object... replacements) {
        return parse(player, replace(getRaw(key), replacements));
    }

    public void send(CommandSender sender, String key) {
        send(sender, sender instanceof Player player ? player : null, key);
    }

    public void send(CommandSender sender, OfflinePlayer placeholderPlayer, String key) {
        audiences.sender(sender).sendMessage(getComponent(placeholderPlayer, key));
    }

    public void send(Player player, String key) {
        audiences.player(player).sendMessage(getComponent(player, key));
    }

    public void sendFormatted(CommandSender sender, String key, Object... replacements) {
        sendFormatted(sender, sender instanceof Player player ? player : null, key, replacements);
    }

    public void sendFormatted(CommandSender sender, OfflinePlayer placeholderPlayer, String key, Object... replacements) {
        audiences.sender(sender).sendMessage(getFormattedComponent(placeholderPlayer, key, replacements));
    }

    public void sendFormatted(Player player, String key, Object... replacements) {
        audiences.player(player).sendMessage(getFormattedComponent(player, key, replacements));
    }

    public void sendRaw(Player player, String message) {
        audiences.player(player).sendMessage(parse(player, message));
    }

    public Component parse(OfflinePlayer player, String message) {
        String placeholderParsed = parsePlaceholders(player, message);
        return miniMessage.deserialize(convertLegacyFormatting(placeholderParsed));
    }

    public String parseToString(OfflinePlayer player, String message) {
        return serialize(parse(player, message));
    }

    private String replace(String message, Object... replacements) {
        for (int i = 0; i < replacements.length - 1; i += 2) {
            String placeholder = String.valueOf(replacements[i]);
            String value = String.valueOf(replacements[i + 1]);
            message = message.replace(placeholder, value);
        }
        return message;
    }

    private String parsePlaceholders(OfflinePlayer player, String message) {
        if (player == null || !Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return message;
        }
        return PlaceholderAPI.setPlaceholders(player, message);
    }

    private String convertLegacyFormatting(String message) {
        StringBuilder converted = new StringBuilder(message.length());
        for (int i = 0; i < message.length(); i++) {
            char current = message.charAt(i);
            if ((current == '&' || current == '§') && i + 1 < message.length()) {
                char code = Character.toLowerCase(message.charAt(i + 1));
                if (code == 'r') {
                    converted.append("<reset>");
                    i++;
                    continue;
                }
                String tag = LEGACY_MINIMESSAGE_TAGS.get(code);
                if (tag != null) {
                    converted.append('<').append(tag).append('>');
                    i++;
                    continue;
                }
            }
            converted.append(current);
        }
        return converted.toString();
    }

    private String serialize(Component component) {
        return legacySerializer.serialize(component);
    }

    public FileConfiguration getConfig() {
        return lang;
    }

    public void reload() {
        loadLanguageFile();
    }
}
