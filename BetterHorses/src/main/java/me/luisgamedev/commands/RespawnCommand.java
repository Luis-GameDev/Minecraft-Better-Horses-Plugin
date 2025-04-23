package me.luisgamedev.commands;

import me.luisgamedev.BetterHorses;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class RespawnCommand {

    public static boolean spawnHorseFromItem(Player player) {

        if (!player.hasPermission("betterhorses.base")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        String configuredItem = BetterHorses.getInstance().getConfig().getString("settings.horse-item", "SADDLE");
        Material expectedMaterial = Material.getMaterial(configuredItem.toUpperCase());
        if (expectedMaterial == null || !expectedMaterial.isItem()) expectedMaterial = Material.SADDLE;

        if (item == null || item.getType() != expectedMaterial || !item.hasItemMeta()) {
            player.sendMessage(ChatColor.RED + "You must hold a valid horse item in your main hand.");
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();

        Double health = data.get(new NamespacedKey(BetterHorses.getInstance(), "health"), PersistentDataType.DOUBLE);
        Double currentHealth = data.get(new NamespacedKey(BetterHorses.getInstance(), "current_health"), PersistentDataType.DOUBLE);
        Double speed = data.get(new NamespacedKey(BetterHorses.getInstance(), "speed"), PersistentDataType.DOUBLE);
        Double jump = data.get(new NamespacedKey(BetterHorses.getInstance(), "jump"), PersistentDataType.DOUBLE);
        String gender = data.get(new NamespacedKey(BetterHorses.getInstance(), "gender"), PersistentDataType.STRING);
        String ownerUUID = data.get(new NamespacedKey(BetterHorses.getInstance(), "owner"), PersistentDataType.STRING);
        String styleStr = data.get(new NamespacedKey(BetterHorses.getInstance(), "style"), PersistentDataType.STRING);
        String colorStr = data.get(new NamespacedKey(BetterHorses.getInstance(), "color"), PersistentDataType.STRING);
        String saddleStr = data.get(new NamespacedKey(BetterHorses.getInstance(), "saddle"), PersistentDataType.STRING);
        String armorStr = data.get(new NamespacedKey(BetterHorses.getInstance(), "armor"), PersistentDataType.STRING);
        String customName = data.get(new NamespacedKey(BetterHorses.getInstance(), "name"), PersistentDataType.STRING);
        String trait = data.get(new NamespacedKey(BetterHorses.getInstance(), "trait"), PersistentDataType.STRING);

        if (health == null || speed == null || jump == null || gender == null || ownerUUID == null) {
            player.sendMessage(ChatColor.RED + "This item does not contain valid horse data.");
            return true;
        }

        Horse horse = player.getWorld().spawn(player.getLocation(), Horse.class);
        setAttribute(horse, Attribute.GENERIC_MAX_HEALTH, health);
        setAttribute(horse, Attribute.GENERIC_MOVEMENT_SPEED, speed);
        setAttribute(horse, Attribute.HORSE_JUMP_STRENGTH, jump);
        horse.setHealth(currentHealth != null ? currentHealth : health);
        horse.setTamed(true);
        horse.setOwner(player);

        if (gender != null) {
            horse.getPersistentDataContainer().set(new NamespacedKey(BetterHorses.getInstance(), "gender"), PersistentDataType.STRING, gender);
        }

        if (trait != null && !trait.isBlank()) {
            horse.getPersistentDataContainer().set(new NamespacedKey(BetterHorses.getInstance(), "trait"), PersistentDataType.STRING, trait);
        }

        if (customName != null && !customName.isBlank()) {
            horse.setCustomName(ChatColor.GOLD + customName);
            horse.setCustomNameVisible(true);
        }

        try {
            horse.setStyle(Horse.Style.valueOf(styleStr));
            horse.setColor(Horse.Color.valueOf(colorStr));
        } catch (Exception ignored) {}

        if (saddleStr != null) {
            horse.getInventory().setSaddle(new ItemStack(Material.valueOf(saddleStr)));
        }
        if (armorStr != null) {
            horse.getInventory().setArmor(new ItemStack(Material.valueOf(armorStr)));
        }

        item.setAmount(item.getAmount() - 1);
        player.sendMessage(ChatColor.GREEN + "Horse respawned with stored stats.");
        return true;
    }

    private static void setAttribute(Horse horse, Attribute attribute, double value) {
        AttributeInstance attr = horse.getAttribute(attribute);
        if (attr != null) {
            attr.setBaseValue(value);
        }
    }
}
