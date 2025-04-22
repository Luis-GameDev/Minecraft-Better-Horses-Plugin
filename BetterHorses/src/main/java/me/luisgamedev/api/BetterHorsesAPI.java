package me.luisgamedev.api;

import me.luisgamedev.BetterHorses;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class BetterHorsesAPI {

    public static ItemStack createHorseItem(double health, double speed, double jump, String gender, String name, Player owner, Inventory targetInventory, boolean dropIfFull, String traitOverride) {

        String genderSymbol = gender.equals("male") ? "♂" : gender.equals("female") ? "♀" : "?";

        ItemStack item = new ItemStack(Material.SADDLE);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();

        lore.add(ChatColor.GRAY + "Gender: " + genderSymbol);
        lore.add(ChatColor.GRAY + String.format("Health: %.2f", health));
        lore.add(ChatColor.GRAY + String.format("Speed: %.4f", speed));
        lore.add(ChatColor.GRAY + String.format("Jump: %.4f", jump));

        PersistentDataContainer data = meta.getPersistentDataContainer();
        BetterHorses plugin = BetterHorses.getInstance();

        data.set(new NamespacedKey(plugin, "gender"), PersistentDataType.STRING, gender);
        data.set(new NamespacedKey(plugin, "health"), PersistentDataType.DOUBLE, health);
        data.set(new NamespacedKey(plugin, "current_health"), PersistentDataType.DOUBLE, health);
        data.set(new NamespacedKey(plugin, "speed"), PersistentDataType.DOUBLE, speed);
        data.set(new NamespacedKey(plugin, "jump"), PersistentDataType.DOUBLE, jump);
        data.set(new NamespacedKey(plugin, "owner"), PersistentDataType.STRING, owner.getUniqueId().toString());
        data.set(new NamespacedKey(plugin, "name"), PersistentDataType.STRING, name.replace(ChatColor.GOLD.toString(), ""));
        data.set(new NamespacedKey(plugin, "style"), PersistentDataType.STRING, Horse.Style.WHITE.name());
        data.set(new NamespacedKey(plugin, "color"), PersistentDataType.STRING, Horse.Color.CREAMY.name());

        FileConfiguration config = plugin.getConfig();
        if (config.getBoolean("traits.enabled")) {
            ConfigurationSection traitsSection = config.getConfigurationSection("traits");

            if (traitOverride != null) {
                if (!traitOverride.equalsIgnoreCase("none") && traitsSection != null && traitsSection.isConfigurationSection(traitOverride)) {
                    ConfigurationSection traitConfig = traitsSection.getConfigurationSection(traitOverride);
                    if (traitConfig.getBoolean("enabled", false)) {
                        data.set(new NamespacedKey(plugin, "trait"), PersistentDataType.STRING, traitOverride.toLowerCase());
                        lore.add(ChatColor.GOLD + "Trait: " + ChatColor.LIGHT_PURPLE + formatTraitName(traitOverride));
                    }
                }
            } else if (traitsSection != null) {
                for (String trait : traitsSection.getKeys(false)) {
                    if (trait.equals("enabled")) continue;
                    ConfigurationSection tSec = traitsSection.getConfigurationSection(trait);
                    if (tSec == null || !tSec.getBoolean("enabled", false)) continue;

                    double chance = tSec.getDouble("chance", 0);
                    if (Math.random() < chance) {
                        data.set(new NamespacedKey(plugin, "trait"), PersistentDataType.STRING, trait.toLowerCase());
                        lore.add(ChatColor.GOLD + "Trait: " + ChatColor.LIGHT_PURPLE + formatTraitName(trait));
                        break;
                    }
                }
            }
        }

        meta.setLore(lore);
        meta.setDisplayName(name);
        item.setItemMeta(meta);

        HashMap<Integer, ItemStack> leftovers = targetInventory.addItem(item);
        if (!leftovers.isEmpty() && dropIfFull) {
            owner.getWorld().dropItem(owner.getLocation(), item);
        }

        return item;
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
