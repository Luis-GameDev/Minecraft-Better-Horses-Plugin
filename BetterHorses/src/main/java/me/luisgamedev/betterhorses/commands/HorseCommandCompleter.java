package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import me.luisgamedev.betterhorses.utils.NeuteringPolicy;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HorseCommandCompleter implements TabCompleter {
    private final HorseCommand executor;

    public HorseCommandCompleter() { this(null); }

    public HorseCommandCompleter(HorseCommand executor) { this.executor = executor; }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (sender instanceof org.bukkit.entity.Player player && !WorldAccessPolicy.isEnabled(player.getWorld())) return List.of();
        if (args.length == 1) {
            if (executor != null) {
                String current = args[0].toLowerCase(Locale.ROOT);
                return executor.getAvailableSubcommands(sender).stream()
                        .filter(suggestion -> suggestion.startsWith(current)).toList();
            }
            List<String> suggestions = new ArrayList<>();
            if (sender.hasPermission(PermissionUtils.SPAWN_COMMAND)) suggestions.add("spawn");
            if (sender.hasPermission(PermissionUtils.DESPAWN)) suggestions.add("despawn");
            if (NeuteringPolicy.isEnabled() && sender.hasPermission("betterhorses.neuter")) suggestions.add("neuter");
            if (sender.hasPermission("betterhorses.reload")) {
                suggestions.add("reload");
            }
            if (BetterHorses.getInstance().isDebugModeEnabled() && sender.hasPermission(PermissionUtils.INFO)) {
                suggestions.add("info");
            }
            String current = args[0].toLowerCase(Locale.ROOT);
            suggestions.removeIf(suggestion -> !suggestion.toLowerCase(Locale.ROOT).startsWith(current));
            return suggestions;
        }
        return List.of();
    }
}
