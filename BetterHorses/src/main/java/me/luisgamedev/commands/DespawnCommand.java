package me.luisgamedev.commands;

import me.luisgamedev.BetterHorses;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
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
        NamespacedKey genderKey = new NamespacedKey(BetterHorses.getInstance(), "gender");
        NamespacedKey traitKey = new NamespacedKey(BetterHorses.getInstance(), "trait");
        String gender = data.getOrDefault(genderKey, PersistentDataType.STRING, "unknown");
        String trait = data.has(traitKey, PersistentDataType.STRING) ? data.get(traitKey, PersistentDataType.STRING) : null;
        String genderSymbol = gender.equalsIgnoreCase("male") ? "♂" : gender.equalsIgnoreCase("female") ? "♀" : "?";
        String name = horse.getCustomName() != null ? ChatColor.stripColor(horse.getCustomName()) : "Horse";

        double maxHealth = horse.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
        double currentHealth = horse.getHealth();
        double speed = horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getBaseValue();
        double jump = horse.getAttribute(Attribute.HORSE_JUMP_STRENGTH).getBaseValue();

        Horse.Style style = horse.getStyle();
        Horse.Color color = horse.getColor();
        HorseInventory inv = horse.getInventory();
        ItemStack saddle = inv.getSaddle();
        ItemStack armor = inv.getArmor();

        String materialName = BetterHorses.getInstance().getConfig().getString("settings.horse-item", "SADDLE");
        Material material = Material.getMaterial(materialName.toUpperCase());
        if (material == null || !material.isItem()) material = Material.SADDLE;

        ItemStack item = new ItemStack(material);

        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Horse " + genderSymbol);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + String.format("Gender: %s", genderSymbol));
        lore.add(ChatColor.GRAY + String.format("Health: %.2f / %.2f", currentHealth, maxHealth));
        lore.add(ChatColor.GRAY + String.format("Speed: %.4f", speed));
        lore.add(ChatColor.GRAY + String.format("Jump: %.4f", jump));
        if (trait != null) {
            lore.add(ChatColor.GOLD + "Trait: " + ChatColor.LIGHT_PURPLE + formatTraitName(trait));
        }
        meta.setLore(lore);

        PersistentDataContainer itemData = meta.getPersistentDataContainer();
        itemData.set(genderKey, PersistentDataType.STRING, gender);
        data.set(new NamespacedKey(BetterHorses.getInstance(), "name"), PersistentDataType.STRING, name);
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "health"), PersistentDataType.DOUBLE, maxHealth);
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "current_health"), PersistentDataType.DOUBLE, currentHealth);
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "speed"), PersistentDataType.DOUBLE, speed);
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "jump"), PersistentDataType.DOUBLE, jump);
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "owner"), PersistentDataType.STRING, player.getUniqueId().toString());
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "style"), PersistentDataType.STRING, style.name());
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "color"), PersistentDataType.STRING, color.name());
        if (trait != null) {
            itemData.set(traitKey, PersistentDataType.STRING, trait.toLowerCase());
        }
        if (saddle != null) itemData.set(new NamespacedKey(BetterHorses.getInstance(), "saddle"), PersistentDataType.STRING, saddle.getType().name());
        if (armor != null) itemData.set(new NamespacedKey(BetterHorses.getInstance(), "armor"), PersistentDataType.STRING, armor.getType().name());

        item.setItemMeta(meta);
        horse.remove();

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), item);
        } else {
            player.getInventory().addItem(item);
        }

        player.sendMessage(ChatColor.GREEN + "Your horse has been successfully despawned.");
        return true;
    }

    private static String formatTraitName(String raw) {
        switch (raw.toLowerCase()) {
            case "hellmare": return "Hellmare";
            case "fireheart": return "Fireheart";
            case "dashboost": return "Dash Boost";
            case "featherhooves": return "Feather Hooves";
            case "frosthooves": return "Frost Hooves";
            case "kickback": return "Kickback";
            case "ghosthorse": return "Ghost Horse";
            default: return raw.substring(0, 1).toUpperCase() + raw.substring(1);
        }
    }
}
