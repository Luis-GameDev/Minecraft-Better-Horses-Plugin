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
        // Trait ist an 7. Stelle → Index 6
        if (args.length == 7) {
            List<String> result = new ArrayList<>();
            ConfigurationSection traits = BetterHorses.getInstance().getConfig().getConfigurationSection("traits");
            if (traits != null) {
                Set<String> keys = traits.getKeys(false);
                for (String key : keys) {
                    if (!key.equalsIgnoreCase("enabled") && traits.getBoolean(key + ".enabled", false)) {
                        result.add(key.toLowerCase());
                    }
                }
            }
            result.add("none");
            return result;
        }

        return Collections.emptyList();
    }
}
