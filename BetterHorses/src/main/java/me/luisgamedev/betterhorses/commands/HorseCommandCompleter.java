package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class HorseCommandCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            if (sender.hasPermission(PermissionUtils.SPAWN_COMMAND)) suggestions.add("spawn");
            if (sender.hasPermission(PermissionUtils.DESPAWN)) suggestions.add("despawn");
            if (sender.hasPermission("betterhorses.neuter")) suggestions.add("neuter");
            if (sender.hasPermission("betterhorses.reload")) {
                suggestions.add("reload");
            }
            if (BetterHorses.getInstance().isDebugModeEnabled() && sender.hasPermission(PermissionUtils.INFO)) {
                suggestions.add("info");
            }
            String current = args[0].toLowerCase();
            suggestions.removeIf(suggestion -> !suggestion.toLowerCase().startsWith(current));
            return suggestions;
        }
        return List.of();
    }
}
