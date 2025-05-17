package me.luisgamedev.commands;

import me.luisgamedev.BetterHorses;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class HorseCreateTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        switch (args.length) {
            case 1 -> suggestions.addAll(List.of("20", "50", "100"));
            case 2 -> suggestions.addAll(List.of("0.4", "0.5", "0.6", "0.7"));
            case 3 -> suggestions.addAll(List.of("0.6", "0.8", "1.0"));
            case 4 -> suggestions.addAll(List.of("male", "female"));
            case 5 -> suggestions.add("Name");
            case 6 -> {
                ConfigurationSection traits = BetterHorses.getInstance().getConfig().getConfigurationSection("traits");
                if (traits != null) {
                    Set<String> keys = traits.getKeys(false);
                    for (String key : keys) {
                        if (!key.equalsIgnoreCase("enabled") && traits.getBoolean(key + ".enabled", false)) {
                            suggestions.add(key.toLowerCase());
                        }
                    }
                }
                suggestions.add("none");
            }
            default -> {
                return Collections.emptyList();
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        suggestions.removeIf(s -> !s.toLowerCase().startsWith(lastArg));
        return suggestions;
    }
}
