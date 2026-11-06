package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.api.events.BetterHorseNeuterEvent;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.utils.NeuteringPolicy;
import me.luisgamedev.betterhorses.utils.HorseItemTransaction;
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
        if (!NeuteringPolicy.isEnabled()) return true;
        BetterHorses plugin = BetterHorses.getInstance();
        if (!HorseItemTransaction.ready(plugin, player)) return true;

        if (!player.hasPermission("betterhorses.neuter")) {
            lang.sendFormatted(player, "messages.insufficient-permission", "%command%", "/horse neuter");
            BetterHorses.getInstance().debugLog("HORSE_NEUTER", "PERMISSION", false,
                    "Missing betterhorses.neuter permission.");
            return true;
        }

        HorseItemTransaction transaction = HorseItemTransaction.capture(plugin, player);
        if (transaction == null) return true;
        ItemStack item = transaction.copy();
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        Material expected = Material.getMaterial(config.getString("settings.horse-item", "SADDLE").toUpperCase());

        if (expected == null || item == null || item.getType() != expected || !BetterHorsesAPI.isHorseItem(item)) {
            lang.send(player, "messages.invalid-item");
            BetterHorses.getInstance().debugLog("HORSE_NEUTER", "VALIDATION", false,
                    "No valid horse item in the main hand.");
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        BetterHorses.getInstance().debugLog("HORSE_NEUTER", "VALIDATION", true,
                "Valid item detected for player " + player.getName());

        if (meta.getPersistentDataContainer().has(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE)) {
            lang.send(player, "messages.already-castrated");
            BetterHorses.getInstance().debugLog("HORSE_NEUTER", "ALREADY_NEUTERED", false,
                    "Horse item is already neutered.");
            return true;
        }

        BetterHorseNeuterEvent neuterEvent = new BetterHorseNeuterEvent(player, item.clone());
        Bukkit.getPluginManager().callEvent(neuterEvent);
        if (neuterEvent.isCancelled() || !NeuteringPolicy.isEnabled() || !transaction.matches()) {
            BetterHorses.getInstance().debugLog("HORSE_NEUTER", "EVENT", false,
                    "BetterHorseNeuterEvent was cancelled or its source changed.");
            return true;
        }

        item = neuterEvent.getHorseItem();
        if (item == null || item.getType() != expected || !BetterHorsesAPI.isHorseItem(item)) return true;
        item = item.clone();
        meta = item.getItemMeta();
        if (meta == null) return true;

        meta.getPersistentDataContainer().set(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE, (byte) 1);

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + lang.getRaw(player, "messages.lore-neutered"));
        meta.setLore(lore);

        item.setItemMeta(meta);
        if (!NeuteringPolicy.isEnabled() || !transaction.matches()
                || !player.hasPermission("betterhorses.neuter") || !transaction.replace(item)) return true;
        lang.send(player, "messages.successfully-castrated");
        BetterHorses.getInstance().debugLog("HORSE_NEUTER", "COMPLETE", true,
                "Horse item neutered and written back to the unchanged main hand.");
        return true;
    }
}
