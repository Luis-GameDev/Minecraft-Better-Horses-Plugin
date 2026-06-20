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
        BetterHorses plugin = BetterHorses.getInstance();
        LanguageManager lang = plugin.getLang();
        plugin.debugLog("HORSE_CREATE", "RECEIVED", true, "Sender=" + sender.getName() + ", args=" + args.length + ".");

        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.get("messages.only-players"));
            plugin.debugLog("HORSE_CREATE", "PLAYER_REQUIRED", false, "Non-player sender attempted /horsecreate.");
            return true;
        }

        if (!player.hasPermission("betterhorses.create")) {
            player.sendMessage(lang.getFormatted(player, "messages.insufficient-permission", "%command%", "/horsecreate"));
            plugin.debugLog("HORSE_CREATE", "PERMISSION", false, "Player " + player.getName() + " lacks betterhorses.create.");
            return true;
        }

        if (args.length < 3) {
            player.sendMessage(lang.get(player, "messages.horsecreate-usage"));
            plugin.debugLog("HORSE_CREATE", "USAGE", false, "Player " + player.getName() + " provided too few arguments.");
            return true;
        }

        try {
            double health = Double.parseDouble(args[0]);
            double speed = Double.parseDouble(args[1]);
            double jump = Double.parseDouble(args[2]);
            String gender = args.length >= 4 ? args[3].toLowerCase() : (Math.random() < 0.5 ? "male" : "female");
            String name = args.length >= 5 ? args[4] : lang.getRaw(player, "messages.horse");
            String trait = args.length >= 6 ? args[5].toLowerCase() : null;
            int growthStage = args.length >= 7 ? Integer.parseInt(args[6]) : 10;
            String mountTypeArg = args.length >= 8 ? args[7] : null;
            boolean isNeutered = args.length >= 9 ? Boolean.parseBoolean(args[8]) : false;

            FileConfiguration config = BetterHorses.getInstance().getConfig();

            SupportedMountType mountType = mountTypeArg == null ? SupportedMountType.HORSE : SupportedMountType.fromUserInput(mountTypeArg).orElse(null);
            if (mountType == null) {
                player.sendMessage(lang.getFormatted(player, "messages.invalid-mount-type", "%types%", getEnabledMountTypes(config)));
                plugin.debugLog("HORSE_CREATE", "MOUNT_TYPE", false, "Invalid mount type input: " + mountTypeArg);
                return true;
            }

            if (!mountType.isEnabled(config)) {
                player.sendMessage(lang.get(player, "messages.mount-type-disabled"));
                plugin.debugLog("HORSE_CREATE", "MOUNT_TYPE", false, "Disabled mount type requested: " + mountType.getEntityType());
                return true;
            }

            String validatedTrait = null;

            if (trait != null && !trait.equalsIgnoreCase("none")) {
                if (!config.getBoolean("traits.enabled", false)) {
                    player.sendMessage(lang.get(player, "messages.traits-disabled"));
                    plugin.debugLog("HORSE_CREATE", "TRAIT", false, "Trait requested while traits are disabled.");
                    return true;
                }

                ConfigurationSection traitSection = config.getConfigurationSection("traits." + trait);
                if (traitSection == null || !traitSection.getBoolean("enabled", false)) {
                    player.sendMessage(lang.get(player, "messages.traits-error"));
                    plugin.debugLog("HORSE_CREATE", "TRAIT", false, "Invalid or disabled trait requested: " + trait);
                    return true;
                }

                validatedTrait = trait;
            }

            Inventory target = player.getInventory();
            BetterHorsesAPI.createHorseItem(health, speed, jump, gender, name, player, target, true, validatedTrait, isNeutered, growthStage, mountType);
            plugin.debugLog("HORSE_CREATE", "COMPLETE", true, "Created item for " + player.getName() + " with mount=" + mountType.getEntityType() + ", trait=" + validatedTrait + ".");
            return true;

        } catch (NumberFormatException e) {
            player.sendMessage(lang.get(player, "messages.invalid-number-format"));
            plugin.debugLog("HORSE_CREATE", "PARSE", false, "Invalid numeric argument from " + player.getName() + ": " + e.getMessage());
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
