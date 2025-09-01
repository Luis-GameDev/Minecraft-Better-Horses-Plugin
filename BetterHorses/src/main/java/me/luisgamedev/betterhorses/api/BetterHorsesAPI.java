package me.luisgamedev.betterhorses.api;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.language.LanguageManager;
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

    public static ItemStack createHorseItem(double health, double speed, double jump, String gender, String name, Player owner, Inventory targetInventory, boolean dropIfFull, String traitOverride, Integer growthStage) {

        BetterHorses plugin = BetterHorses.getInstance();
        LanguageManager lang = plugin.getLang();

        String genderSymbol = gender.equals("male") ? lang.getRaw("messages.gender-male") : gender.equals("female") ? lang.getRaw("messages.gender-female") : "?";

        String materialName = plugin.getConfig().getString("settings.horse-item", "SADDLE");
        Material material = Material.getMaterial(materialName.toUpperCase());
        if (material == null || !material.isItem()) material = Material.SADDLE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();

        int growth = growthStage > 10 || growthStage < 1 ? 10 : growthStage;

        lore.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-gender", "%value%", genderSymbol));
        lore.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-health", "%value%", String.format("%.2f", health), "%max%", String.format("%.2f", health)));
        lore.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-speed", "%value%", String.format("%.4f", speed)));
        lore.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-jump", "%value%", String.format("%.4f", jump)));
        lore.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-growth", "%value%", String.format("%d", growth)));

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(new NamespacedKey(plugin, "gender"), PersistentDataType.STRING, gender);
        data.set(new NamespacedKey(plugin, "health"), PersistentDataType.DOUBLE, health);
        data.set(new NamespacedKey(plugin, "current_health"), PersistentDataType.DOUBLE, health);
        data.set(new NamespacedKey(plugin, "speed"), PersistentDataType.DOUBLE, speed);
        data.set(new NamespacedKey(plugin, "jump"), PersistentDataType.DOUBLE, jump);
        data.set(new NamespacedKey(plugin, "owner"), PersistentDataType.STRING, owner.getUniqueId().toString());
        data.set(new NamespacedKey(plugin, "name"), PersistentDataType.STRING, name.replace(ChatColor.GOLD.toString(), ""));
        data.set(new NamespacedKey(plugin, "style"), PersistentDataType.STRING, Horse.Style.WHITE.name());
        data.set(new NamespacedKey(plugin, "color"), PersistentDataType.STRING, Horse.Color.CREAMY.name());
        data.set(new NamespacedKey(BetterHorses.getInstance(), "growth_stage"), PersistentDataType.INTEGER, growth);

        FileConfiguration config = plugin.getConfig();
        if (config.getBoolean("traits.enabled")) {
            ConfigurationSection traitsSection = config.getConfigurationSection("traits");

            if (traitOverride != null) {
                if (!traitOverride.equalsIgnoreCase("none") && traitsSection != null && traitsSection.isConfigurationSection(traitOverride)) {
                    ConfigurationSection traitConfig = traitsSection.getConfigurationSection(traitOverride);
                    if (traitConfig.getBoolean("enabled", false)) {
                        data.set(new NamespacedKey(plugin, "trait"), PersistentDataType.STRING, traitOverride.toLowerCase());
                        lore.add(ChatColor.GOLD + lang.getFormattedRaw("messages.trait-line", "%trait%", formatTraitName(traitOverride)));
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
                        lore.add(ChatColor.GOLD + lang.getFormattedRaw("messages.trait-line", "%trait%", formatTraitName(trait)));
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
        LanguageManager lang = BetterHorses.getInstance().getLang();
        String path = "traits." + raw.toLowerCase();

        if (lang.getConfig().contains(path)) {
            return ChatColor.translateAlternateColorCodes('&', lang.getConfig().getString(path));
        }

        return raw.substring(0, 1).toUpperCase() + raw.substring(1);
    }
}
