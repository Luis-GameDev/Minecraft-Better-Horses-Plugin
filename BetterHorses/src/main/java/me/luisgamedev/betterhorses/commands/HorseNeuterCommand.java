package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.events.BetterHorseNeuterEvent;
import me.luisgamedev.betterhorses.language.LanguageManager;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class HorseNeuterCommand {

    public static boolean handle(Player player) {
        LanguageManager lang = BetterHorses.getInstance().getLang();

        if (!player.hasPermission("betterhorses.neuter")) {
            lang.sendFormatted(player, "messages.insufficient-permission", "%command%", "/horse neuter");
            BetterHorses.getInstance().debugLog("HORSE_NEUTER", "PERMISSION", false,
                    "Player " + player.getName() + " lacks betterhorses.neuter");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        Material expected = Material.getMaterial(config.getString("settings.horse-item", "SADDLE").toUpperCase());

        if (expected == null || item == null || item.getType() != expected || !item.hasItemMeta()) {
            lang.send(player, "messages.invalid-item");
            BetterHorses.getInstance().debugLog("HORSE_NEUTER", "VALIDATION", false,
                    "Player " + player.getName() + " did not hold a valid horse item.");
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        BetterHorses.getInstance().debugLog("HORSE_NEUTER", "VALIDATION", true,
                "Valid item detected for player " + player.getName());

        if (meta.getPersistentDataContainer().has(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE)) {
            lang.send(player, "messages.already-castrated");
            BetterHorses.getInstance().debugLog("HORSE_NEUTER", "ALREADY_NEUTERED", false,
                    "Horse item is already neutered for player " + player.getName());
            return true;
        }

        BetterHorseNeuterEvent neuterEvent = new BetterHorseNeuterEvent(player, item.clone());
        Bukkit.getPluginManager().callEvent(neuterEvent);
        if (neuterEvent.isCancelled()) {
            BetterHorses.getInstance().debugLog("HORSE_NEUTER", "EVENT", false,
                    "BetterHorseNeuterEvent was cancelled for player " + player.getName());
            return true;
        }

        item = neuterEvent.getHorseItem();
        meta = item.getItemMeta();

        meta.getPersistentDataContainer().set(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE, (byte) 1);

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + lang.getRaw(player, "messages.lore-neutered"));
        meta.setLore(lore);

        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(item);
        lang.send(player, "messages.successfully-castrated");
        BetterHorses.getInstance().debugLog("HORSE_NEUTER", "COMPLETE", true,
                "Horse item neutered and written back to main hand for player " + player.getName());
        return true;
    }
}
