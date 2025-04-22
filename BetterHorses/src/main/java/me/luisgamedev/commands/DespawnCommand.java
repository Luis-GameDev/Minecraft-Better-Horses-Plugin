package me.luisgamedev.commands;

import me.luisgamedev.BetterHorses;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class DespawnCommand {

    public static boolean despawnHorseToItem(Player player) {
        if (!(player.getVehicle() instanceof Horse horse)) {
            player.sendMessage(ChatColor.RED + "You must be riding a tamed horse to despawn it.");
            return true;
        }

        if (!horse.isTamed() || !horse.getOwner().getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You must be the owner of the tamed horse.");
            return true;
        }

        PersistentDataContainer data = horse.getPersistentDataContainer();
        NamespacedKey genderKey = new NamespacedKey(JavaPlugin.getPlugin(BetterHorses.class), "gender");
        String gender = data.getOrDefault(genderKey, PersistentDataType.STRING, "unknown");
        String genderSymbol = gender.equalsIgnoreCase("male") ? "♂" : gender.equalsIgnoreCase("female") ? "♀" : "?";

        double maxHealth = horse.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
        double currentHealth = horse.getHealth();
        double speed = horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getBaseValue();
        double jump = horse.getAttribute(Attribute.HORSE_JUMP_STRENGTH).getBaseValue();

        Horse.Style style = horse.getStyle();
        Horse.Color color = horse.getColor();
        HorseInventory inv = horse.getInventory();
        ItemStack saddle = inv.getSaddle();
        ItemStack armor = inv.getArmor();

        ItemStack item = new ItemStack(Material.SADDLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Horse " + genderSymbol);
        meta.setLore(List.of(
                ChatColor.GRAY + String.format("Gender: %s", genderSymbol),
                ChatColor.GRAY + String.format("Health: %.2f / %.2f", currentHealth, maxHealth),
                ChatColor.GRAY + String.format("Speed: %.4f", speed),
                ChatColor.GRAY + String.format("Jump: %.4f", jump)
        ));

        PersistentDataContainer itemData = meta.getPersistentDataContainer();
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "gender"), PersistentDataType.STRING, gender);
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "health"), PersistentDataType.DOUBLE, maxHealth);
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "current_health"), PersistentDataType.DOUBLE, currentHealth);
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "speed"), PersistentDataType.DOUBLE, speed);
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "jump"), PersistentDataType.DOUBLE, jump);
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "owner"), PersistentDataType.STRING, player.getUniqueId().toString());
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "style"), PersistentDataType.STRING, style.name());
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "color"), PersistentDataType.STRING, color.name());
        if (saddle != null) itemData.set(new NamespacedKey(BetterHorses.getInstance(), "saddle"), PersistentDataType.STRING, saddle.getType().name());
        if (armor != null) itemData.set(new NamespacedKey(BetterHorses.getInstance(), "armor"), PersistentDataType.STRING, armor.getType().name());

        item.setItemMeta(meta);
        horse.remove();

        // if inventory is full drop item to the ground
        if(isInventoryFull(player)) {
            player.getWorld().dropItem(player.getLocation(), item);
        } else {
            player.getInventory().addItem(item);
        }

        player.sendMessage(ChatColor.GREEN + "Your horse has been successfully despawned.");
        return true;
    }

    public static boolean isInventoryFull(Player player) {
        return player.getInventory().firstEmpty() == -1;
    }

}
