package me.luisgamedev.api;

import me.luisgamedev.BetterHorses;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;

public class BetterHorsesAPI {

    public static ItemStack createHorseItem(double health, double speed, double jump, String gender, String name, Player owner, Inventory targetInventory, boolean dropIfFull) {

        String genderSymbol = gender.equals("male") ? "♂" : gender.equals("female") ? "♀" : "?";

        ItemStack item = new ItemStack(Material.SADDLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(
                ChatColor.GRAY + "Gender: " + genderSymbol,
                ChatColor.GRAY + String.format("Health: %.2f", health),
                ChatColor.GRAY + String.format("Speed: %.4f", speed),
                ChatColor.GRAY + String.format("Jump: %.4f", jump)
        ));

        PersistentDataContainer data = meta.getPersistentDataContainer();
        NamespacedKey base = new NamespacedKey(BetterHorses.getInstance(), "");
        data.set(new NamespacedKey(BetterHorses.getInstance(), "gender"), PersistentDataType.STRING, gender);
        data.set(new NamespacedKey(BetterHorses.getInstance(), "health"), PersistentDataType.DOUBLE, health);
        data.set(new NamespacedKey(BetterHorses.getInstance(), "current_health"), PersistentDataType.DOUBLE, health);
        data.set(new NamespacedKey(BetterHorses.getInstance(), "speed"), PersistentDataType.DOUBLE, speed);
        data.set(new NamespacedKey(BetterHorses.getInstance(), "jump"), PersistentDataType.DOUBLE, jump);
        data.set(new NamespacedKey(BetterHorses.getInstance(), "owner"), PersistentDataType.STRING, owner.getUniqueId().toString());
        data.set(new NamespacedKey(BetterHorses.getInstance(), "name"), PersistentDataType.STRING, name.replace(ChatColor.GOLD.toString(), ""));
        data.set(new NamespacedKey(BetterHorses.getInstance(), "style"), PersistentDataType.STRING, Horse.Style.WHITE.name());
        data.set(new NamespacedKey(BetterHorses.getInstance(), "color"), PersistentDataType.STRING, Horse.Color.CREAMY.name());

        item.setItemMeta(meta);

        HashMap<Integer, ItemStack> leftovers = targetInventory.addItem(item);
        if (!leftovers.isEmpty() && dropIfFull) {
            owner.getWorld().dropItem(owner.getLocation(), item);
        }

        return item;
    }

}