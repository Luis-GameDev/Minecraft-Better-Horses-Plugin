package me.luisgamedev.betterhorses.api;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.events.BetterHorseDespawnEvent;
import me.luisgamedev.betterhorses.api.events.BetterHorseSpawnEvent;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class BetterHorsesAPI {

    public static ItemStack createHorseItem(double health, double speed, double jump, String gender, String name, Player owner, Inventory targetInventory, boolean dropIfFull, String traitOverride, Integer growthStage, SupportedMountType mountType) {

        BetterHorses plugin = BetterHorses.getInstance();
        LanguageManager lang = plugin.getLang();
        SupportedMountType targetMountType = mountType == null ? SupportedMountType.HORSE : mountType;

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
        data.set(BetterHorseKeys.GENDER, PersistentDataType.STRING, gender);
        data.set(BetterHorseKeys.HEALTH, PersistentDataType.DOUBLE, health);
        data.set(BetterHorseKeys.CURRENT_HEALTH, PersistentDataType.DOUBLE, health);
        data.set(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE, speed);
        data.set(BetterHorseKeys.JUMP, PersistentDataType.DOUBLE, jump);
        data.set(BetterHorseKeys.OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());
        data.set(BetterHorseKeys.NAME, PersistentDataType.STRING, name.replace(ChatColor.GOLD.toString(), ""));
        data.set(BetterHorseKeys.STYLE, PersistentDataType.STRING, Horse.Style.WHITE.name());
        data.set(BetterHorseKeys.COLOR, PersistentDataType.STRING, Horse.Color.CREAMY.name());
        data.set(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER, growth);
        data.set(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING, targetMountType.getEntityType().name());

        FileConfiguration config = plugin.getConfig();
        if (config.getBoolean("traits.enabled")) {
            ConfigurationSection traitsSection = config.getConfigurationSection("traits");

            if (traitOverride != null) {
                if (!traitOverride.equalsIgnoreCase("none") && traitsSection != null && traitsSection.isConfigurationSection(traitOverride)) {
                    ConfigurationSection traitConfig = traitsSection.getConfigurationSection(traitOverride);
                    if (traitConfig.getBoolean("enabled", false)) {
                        data.set(BetterHorseKeys.TRAIT, PersistentDataType.STRING, traitOverride.toLowerCase());
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
                        data.set(BetterHorseKeys.TRAIT, PersistentDataType.STRING, trait.toLowerCase());
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

    public static Optional<BetterHorse> getBetterHorse(AbstractHorse horse) {
        if (!isBetterHorse(horse)) return Optional.empty();
        return Optional.of(new BetterHorse(horse));
    }

    public static Optional<BetterHorseItem> getBetterHorse(ItemStack item) {
        if (!isHorseItem(item)) return Optional.empty();
        return Optional.of(new BetterHorseItem(item));
    }

    public static boolean isBetterHorse(Entity entity) {
        if (!(entity instanceof AbstractHorse horse)) return false;
        PersistentDataContainer data = horse.getPersistentDataContainer();
        return data.has(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING)
                || (data.has(BetterHorseKeys.HEALTH, PersistentDataType.DOUBLE)
                && data.has(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE)
                && data.has(BetterHorseKeys.JUMP, PersistentDataType.DOUBLE));
    }

    public static boolean isHorseItem(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) return false;
        PersistentDataContainer data = itemStack.getItemMeta().getPersistentDataContainer();
        return data.has(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING)
                || (data.has(BetterHorseKeys.HEALTH, PersistentDataType.DOUBLE)
                && data.has(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE)
                && data.has(BetterHorseKeys.JUMP, PersistentDataType.DOUBLE));
    }

    public static void callSpawnEvent(AbstractHorse horse, ItemStack sourceItem, BetterHorseSpawnEvent.SpawnCause cause) {
        Bukkit.getPluginManager().callEvent(new BetterHorseSpawnEvent(horse, sourceItem, cause));
    }

    public static boolean callDespawnEvent(AbstractHorse horse, ItemStack resultItem) {
        BetterHorseDespawnEvent event = new BetterHorseDespawnEvent(horse, resultItem);
        Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled();
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
