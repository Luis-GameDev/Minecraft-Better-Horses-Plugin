package me.luisgamedev.commands;

import me.luisgamedev.BetterHorses;
import me.luisgamedev.language.LanguageManager;
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
        LanguageManager lang = BetterHorses.getInstance().getLang();

        if (!player.hasPermission("betterhorses.neuter")) {
            player.sendMessage(lang.getFormatted("messages.insufficient-permission", "%command%", "/horse neuter"));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        Material expected = Material.getMaterial(config.getString("settings.horse-item", "SADDLE").toUpperCase());

        if (expected == null || item == null || item.getType() != expected || !item.hasItemMeta()) {
            player.sendMessage(lang.get("messages.invalid-item"));
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        NamespacedKey neuteredKey = new NamespacedKey(BetterHorses.getInstance(), "neutered");

        if (meta.getPersistentDataContainer().has(neuteredKey, PersistentDataType.BYTE)) {
            player.sendMessage(lang.get("messages.already-castrated"));
            return true;
        }

        meta.getPersistentDataContainer().set(neuteredKey, PersistentDataType.BYTE, (byte) 1);

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + lang.getRaw("messages.lore-neutered"));
        meta.setLore(lore);

        item.setItemMeta(meta);
        player.sendMessage(lang.get("messages.successfully-castrated"));
        return true;
    }
}
