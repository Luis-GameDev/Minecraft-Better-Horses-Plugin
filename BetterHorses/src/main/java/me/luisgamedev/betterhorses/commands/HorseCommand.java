package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.language.LanguageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class HorseCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        BetterHorses plugin = BetterHorses.getInstance();
        LanguageManager lang = plugin.getLang();
        OfflinePlayer audience = sender instanceof Player senderPlayer ? senderPlayer : null;

        if (args.length == 0) {
            sender.sendMessage(lang.get(audience, "messages.horse-usage"));
            return true;
        }

        String subcommand = args[0].toLowerCase();
        plugin.debugLog("HORSE_COMMAND", "RECEIVED", true,
                "Sender=" + sender.getName() + ", subcommand=" + subcommand);

        if (subcommand.equals("reload")) {
            if (!sender.hasPermission("betterhorses.reload")) {
                sender.sendMessage(lang.getFormatted(audience, "messages.insufficient-permission", "%command%", "/horse reload"));
                plugin.debugLog("HORSE_COMMAND", "RELOAD_PERMISSION", false,
                        "Sender " + sender.getName() + " lacks betterhorses.reload");
                return true;
            }

            plugin.reloadPluginConfiguration();
            sender.sendMessage(lang.get(audience, "messages.config-reloaded"));
            plugin.debugLog("HORSE_COMMAND", "RELOAD", true,
                    "Configuration reloaded by " + sender.getName());
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.get(audience, "messages.only-players"));
            plugin.debugLog("HORSE_COMMAND", "PLAYER_REQUIRED", false,
                    "Non-player sender tried subcommand " + subcommand);
            return true;
        }

        switch (subcommand) {
            case "spawn":
                if (!player.hasPermission("betterhorses.base")) {
                    player.sendMessage(lang.getFormatted(player, "messages.insufficient-permission", "%command%", "/horse spawn"));
                    plugin.debugLog("HORSE_COMMAND", "SPAWN_PERMISSION", false,
                            "Player " + player.getName() + " lacks betterhorses.base");
                    return true;
                }
                return RespawnCommand.spawnHorseFromItem(player);

            case "despawn":
                if (!player.hasPermission("betterhorses.base")) {
                    player.sendMessage(lang.getFormatted(player, "messages.insufficient-permission", "%command%", "/horse despawn"));
                    plugin.debugLog("HORSE_COMMAND", "DESPAWN_PERMISSION", false,
                            "Player " + player.getName() + " lacks betterhorses.base");
                    return true;
                }
                return DespawnCommand.despawnHorseToItem(player);

            case "neuter":
                if (!player.hasPermission("betterhorses.neuter")) {
                    player.sendMessage(lang.getFormatted(player, "messages.insufficient-permission", "%command%", "/horse neuter"));
                    plugin.debugLog("HORSE_COMMAND", "NEUTER_PERMISSION", false,
                            "Player " + player.getName() + " lacks betterhorses.neuter");
                    return true;
                }
                return HorseNeuterCommand.handle(player);

            case "info":
                if (!plugin.isDebugModeEnabled()) {
                    player.sendMessage(lang.get(player, "messages.unknown-subcommand"));
                    plugin.debugLog("HORSE_COMMAND", "INFO_DEBUG_DISABLED", false,
                            "Player " + player.getName() + " used /horse info while debug mode is disabled.");
                    return true;
                }
                return HorseInfoCommand.handle(player);

            default:
                player.sendMessage(lang.get(player, "messages.unknown-subcommand"));
                plugin.debugLog("HORSE_COMMAND", "UNKNOWN_SUBCOMMAND", false,
                        "Player " + player.getName() + " used unknown subcommand: " + subcommand);
                return true;
        }
    }
}
