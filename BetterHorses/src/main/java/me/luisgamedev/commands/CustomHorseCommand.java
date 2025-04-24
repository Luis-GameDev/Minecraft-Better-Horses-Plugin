package me.luisgamedev.commands;

import me.luisgamedev.BetterHorses;
import me.luisgamedev.api.BetterHorsesAPI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class CustomHorseCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
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

            String validatedTrait = null;

            if (trait != null && !trait.equalsIgnoreCase("none")) {
                FileConfiguration config = BetterHorses.getInstance().getConfig();

                if (!config.getBoolean("traits.enabled", false)) {
                    player.sendMessage(ChatColor.RED + "Traits are disabled in the config.");
                    return true;
                }

                ConfigurationSection traitSection = config.getConfigurationSection("traits." + trait);
                if (traitSection == null || !traitSection.getBoolean("enabled", false)) {
                    player.sendMessage(ChatColor.RED + "This trait is not enabled or doesn't exist in the config.");
                    return true;
                }

                validatedTrait = trait;
            }

            Inventory target = player.getInventory();
            BetterHorsesAPI.createHorseItem(health, speed, jump, gender, name, player, target, true, validatedTrait);
            return true;

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid number format.");
            return true;
        }
    }
}
