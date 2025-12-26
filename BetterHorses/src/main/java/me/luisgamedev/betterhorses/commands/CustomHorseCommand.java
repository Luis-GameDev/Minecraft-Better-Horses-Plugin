package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

        if (args.length < 3) {
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
            int growthStage = args.length >= 7 ? Integer.parseInt(args[6]) : 10;
            String mountTypeArg = args.length >= 8 ? args[7] : null;

            FileConfiguration config = BetterHorses.getInstance().getConfig();

            SupportedMountType mountType = mountTypeArg == null ? SupportedMountType.HORSE : SupportedMountType.fromUserInput(mountTypeArg).orElse(null);
            if (mountType == null) {
                player.sendMessage(lang.getFormatted("messages.invalid-mount-type", "%types%", getEnabledMountTypes(config)));
                return true;
            }

            if (!mountType.isEnabled(config)) {
                player.sendMessage(lang.get("messages.mount-type-disabled"));
                return true;
            }

            String validatedTrait = null;

            if (trait != null && !trait.equalsIgnoreCase("none")) {
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
            BetterHorsesAPI.createHorseItem(health, speed, jump, gender, name, player, target, true, validatedTrait, growthStage, mountType);
            return true;

        } catch (NumberFormatException e) {
            player.sendMessage(lang.get("messages.invalid-number-format"));
            return true;
        }
    }

    private String getEnabledMountTypes(FileConfiguration config) {
        List<String> enabled = Arrays.stream(SupportedMountType.values())
                .filter(type -> type.isEnabled(config))
                .map(type -> type.getEntityType().name().toLowerCase())
                .collect(Collectors.toList());
        return String.join(", ", enabled);
    }
}
