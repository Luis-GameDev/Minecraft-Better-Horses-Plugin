package me.luisgamedev.commands;

import me.luisgamedev.BetterHorses;
import me.luisgamedev.api.BetterHorsesAPI;
import me.luisgamedev.language.LanguageManager;
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
        LanguageManager lang = BetterHorses.getInstance().getLang();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.get("messages.only-players"));
            return true;
        }

        if (!player.hasPermission("betterhorses.create")) {
            player.sendMessage(lang.getFormatted("messages.insufficient-permission", "%command%", "/horsecreate"));
            return true;
        }

        if (args.length < 4) {
            player.sendMessage(lang.get("messages.horsecreate-usage"));
            return true;
        }

        try {
            double health = Double.parseDouble(args[0]);
            double speed = Double.parseDouble(args[1]);
            double jump = Double.parseDouble(args[2]);
            String gender = args.length >= 4 ? args[3].toLowerCase() : (Math.random() < 0.5 ? "male" : "female");
            String name = args.length >= 5 ? "§6" + args[4] : "§6Horse";
            String trait = args.length >= 6 ? args[5].toLowerCase() : null;

            String validatedTrait = null;

            if (trait != null && !trait.equalsIgnoreCase("none")) {
                FileConfiguration config = BetterHorses.getInstance().getConfig();

                if (!config.getBoolean("traits.enabled", false)) {
                    player.sendMessage(lang.get("messages.traits-disabled"));
                    return true;
                }

                ConfigurationSection traitSection = config.getConfigurationSection("traits." + trait);
                if (traitSection == null || !traitSection.getBoolean("enabled", false)) {
                    player.sendMessage(lang.get("messages.traits-error"));
                    return true;
                }

                validatedTrait = trait;
            }

            Inventory target = player.getInventory();
            BetterHorsesAPI.createHorseItem(health, speed, jump, gender, name, player, target, true, validatedTrait);
            return true;

        } catch (NumberFormatException e) {
            player.sendMessage(lang.get("messages.invalid-number-format"));
            return true;
        }
    }
}
