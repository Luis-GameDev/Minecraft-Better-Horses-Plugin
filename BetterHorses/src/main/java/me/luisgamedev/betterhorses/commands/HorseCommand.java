package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.language.LanguageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HorseCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        LanguageManager lang = BetterHorses.getInstance().getLang();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.get("messages.only-players"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(lang.get("messages.horse-usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn":
                if (!player.hasPermission("betterhorses.base")) {
                    player.sendMessage(lang.getFormatted("messages.insufficient-permission", "%command%", "/horse spawn"));
                    return true;
                }
                return RespawnCommand.spawnHorseFromItem(player);

            case "despawn":
                if (!player.hasPermission("betterhorses.base")) {
                    player.sendMessage(lang.getFormatted("messages.insufficient-permission", "%command%", "/horse despawn"));
                    return true;
                }
                return DespawnCommand.despawnHorseToItem(player);

            case "neuter":
                if (!player.hasPermission("betterhorses.neuter")) {
                    player.sendMessage(lang.getFormatted("messages.insufficient-permission", "%command%", "/horse neuter"));
                    return true;
                }
                return HorseNeuterCommand.handle(player);

            default:
                player.sendMessage(lang.get("messages.unknown-subcommand"));
                return true;
        }
    }
}
