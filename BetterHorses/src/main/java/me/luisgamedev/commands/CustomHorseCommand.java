package me.luisgamedev.commands;

import me.luisgamedev.api.BetterHorsesAPI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class CustomHorseCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("betterhorses.create")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 4) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /horsecreate <health> <speed> <jump> [gender] [name] [trait]");
            return true;
        }

        try {
            double health = Double.parseDouble(args[0]);
            double speed = Double.parseDouble(args[1]);
            double jump = Double.parseDouble(args[2]);
            String gender = args.length >= 4 ? args[3].toLowerCase() : (Math.random() < 0.5 ? "male" : "female");
            String name = args.length >= 5 ? ChatColor.GOLD + args[4] : ChatColor.GOLD + "Horse";
            String trait = args.length >= 6 ? args[5].toLowerCase() : null;

            Inventory target = player.getInventory();
            BetterHorsesAPI.createHorseItem(health, speed, jump, gender, name, player, target, true, trait);
            return true;

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid number format.");
            return true;
        }
    }
}
