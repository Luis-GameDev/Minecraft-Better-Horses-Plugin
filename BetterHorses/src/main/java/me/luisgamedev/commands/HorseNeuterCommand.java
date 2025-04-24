package me.luisgamedev.commands;

import me.luisgamedev.BetterHorses;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class HorseNeuterCommand {

    public static boolean handle(Player player) {
        if (!player.hasPermission("betterhorses.neuter")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use /horse neuter.");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        Material expected = Material.getMaterial(config.getString("settings.horse-item", "SADDLE").toUpperCase());

        if (expected == null || item == null || item.getType() != expected || !item.hasItemMeta()) {
            player.sendMessage(ChatColor.RED + "You must hold a valid horse item in your main hand.");
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        NamespacedKey neuteredKey = new NamespacedKey(BetterHorses.getInstance(), "neutered");

        if (meta.getPersistentDataContainer().has(neuteredKey, PersistentDataType.BYTE)) {
            player.sendMessage(ChatColor.YELLOW + "This horse is already castrated.");
            return true;
        }

        meta.getPersistentDataContainer().set(neuteredKey, PersistentDataType.BYTE, (byte) 1);

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "⚠ Castrated – Cannot breed");
        meta.setLore(lore);

        item.setItemMeta(meta);
        player.sendMessage(ChatColor.GRAY + "You castrated the horse. It can no longer breed.");
        return true;
    }
}

