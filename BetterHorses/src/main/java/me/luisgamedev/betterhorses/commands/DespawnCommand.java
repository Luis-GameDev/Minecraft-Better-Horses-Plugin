package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.traits.TraitRegistry;
import me.luisgamedev.betterhorses.utils.MountConfig;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.AbstractHorseInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

public class DespawnCommand {

    public static boolean despawnHorseToItem(Player player) {
        LanguageManager lang = BetterHorses.getInstance().getLang();

        if (!(player.getVehicle() instanceof AbstractHorse horse)) {
            player.sendMessage(lang.get("messages.invalid-vehicle"));
            return true;
        }

        SupportedMountType mountType = SupportedMountType.fromEntity(horse)
                .filter(type -> type.isEnabled(BetterHorses.getInstance().getConfig()))
                .orElse(null);

        if (mountType == null) {
            player.sendMessage(lang.get("messages.invalid-vehicle"));
            return true;
        }

        if (!horse.isTamed() || !horse.getOwner().getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(lang.get("messages.not-horse-owner"));
            return true;
        }

        PersistentDataContainer data = horse.getPersistentDataContainer();
        NamespacedKey genderKey = new NamespacedKey(BetterHorses.getInstance(), "gender");
        NamespacedKey traitKey = new NamespacedKey(BetterHorses.getInstance(), "trait");
        NamespacedKey neuterKey = new NamespacedKey(BetterHorses.getInstance(), "neutered");
        NamespacedKey growthKey = new NamespacedKey(BetterHorses.getInstance(), "growth_stage");
        NamespacedKey cooldownKey = new NamespacedKey(BetterHorses.getInstance(), "cooldown");

        // Assign gender if missing
        String gender;
        if (!data.has(genderKey, PersistentDataType.STRING)) {
            gender = Math.random() < 0.5 ? "male" : "female";
            data.set(genderKey, PersistentDataType.STRING, gender);
        } else {
            gender = data.getOrDefault(genderKey, PersistentDataType.STRING, "unknown");
        }

        String trait = data.has(traitKey, PersistentDataType.STRING) ? data.get(traitKey, PersistentDataType.STRING) : null;
        boolean isNeutered = data.has(neuterKey, PersistentDataType.BYTE) && data.get(neuterKey, PersistentDataType.BYTE) == (byte) 1;
        Long cooldown = data.has(cooldownKey, PersistentDataType.LONG) ? data.get(cooldownKey, PersistentDataType.LONG) : null;

        int growthStage;
        if (MountConfig.isGrowthEnabled(BetterHorses.getInstance().getConfig(), mountType)) {
            growthStage = data.has(growthKey, PersistentDataType.INTEGER) ? data.get(growthKey, PersistentDataType.INTEGER) : 10;
        } else {
            growthStage = 10;
        }

        String genderSymbol = gender.equalsIgnoreCase("male") ? lang.getRaw("messages.gender-male") : gender.equalsIgnoreCase("female") ? lang.getRaw("messages.gender-female") : "?";

        TraitRegistry.revertDashBoostIfActive(horse);

        double maxHealth = horse.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
        double currentHealth = horse.getHealth();
        double speed = horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getBaseValue();
        AttributeInstance jumpAttr = horse.getAttribute(Attribute.valueOf("HORSE_JUMP_STRENGTH"));
        double jump = jumpAttr != null ? jumpAttr.getBaseValue() : 0.0;

        Horse.Style style = horse instanceof Horse ? ((Horse) horse).getStyle() : Horse.Style.WHITE;
        Horse.Color color = horse instanceof Horse ? ((Horse) horse).getColor() : Horse.Color.WHITE;
        AbstractHorseInventory inv = horse.getInventory();
        ItemStack saddle = inv.getSaddle();
        ItemStack armor = inv.getArmor();

        String itemMaterialName = BetterHorses.getInstance().getConfig().getString("settings.horse-item", "SADDLE");
        Material material = Material.getMaterial(itemMaterialName.toUpperCase());
        if (material == null || !material.isItem()) material = Material.SADDLE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer itemData = meta.getPersistentDataContainer();

        itemData.set(genderKey, PersistentDataType.STRING, gender);

        String name = horse.getCustomName() != null ? horse.getCustomName() : lang.getRaw("messages.horse");
        meta.setDisplayName(ChatColor.GOLD + name + " " + genderSymbol);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-gender", "%value%", genderSymbol));
        lore.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-health", "%value%", String.format("%.2f", currentHealth), "%max%", String.format("%.2f", maxHealth)));
        lore.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-speed", "%value%", String.format("%.4f", speed)));
        lore.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-jump", "%value%", String.format("%.4f", jump)));
        lore.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-growth", "%value%", String.format("%d", growthStage)));

        if (trait != null) {
            lore.add(ChatColor.GOLD + lang.getFormattedRaw("messages.trait-line", "%trait%", formatTraitName(trait)));
        }
        if (isNeutered) {
            lore.add(ChatColor.DARK_GRAY + lang.getRaw("messages.lore-neutered"));
        }

        meta.setLore(lore);

        if (horse.getCustomName() != null) {
            itemData.set(new NamespacedKey(BetterHorses.getInstance(), "name"), PersistentDataType.STRING, horse.getCustomName());
        }
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "health"), PersistentDataType.DOUBLE, maxHealth);
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "current_health"), PersistentDataType.DOUBLE, currentHealth);
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "speed"), PersistentDataType.DOUBLE, speed);
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "jump"), PersistentDataType.DOUBLE, jump);
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "owner"), PersistentDataType.STRING, player.getUniqueId().toString());
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "style"), PersistentDataType.STRING, style.name());
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "color"), PersistentDataType.STRING, color.name());
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "growth_stage"), PersistentDataType.INTEGER, growthStage);
        itemData.set(new NamespacedKey(BetterHorses.getInstance(), "mount_type"), PersistentDataType.STRING, mountType.getEntityType().name());
        if (trait != null) {
            itemData.set(traitKey, PersistentDataType.STRING, trait.toLowerCase());
        }
        if (isNeutered) {
            itemData.set(neuterKey, PersistentDataType.BYTE, (byte) 1);
        }
        if (cooldown != null) {
            itemData.set(cooldownKey, PersistentDataType.LONG, cooldown);
        }
        if (saddle != null) itemData.set(new NamespacedKey(BetterHorses.getInstance(), "saddle"), PersistentDataType.STRING, saddle.getType().name());
        if (armor != null) itemData.set(new NamespacedKey(BetterHorses.getInstance(), "armor"), PersistentDataType.STRING, armor.getType().name());

        item.setItemMeta(meta);
        boolean wasLeashed = horse.isLeashed();

        horse.remove();

        if (horse.isValid()) {
            player.sendMessage(lang.get("messages.cant-despawn"));
            return true;
        }

        if(wasLeashed) {
            horse.getWorld().dropItemNaturally(horse.getLocation(), new ItemStack(Material.LEAD, 1));
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), item);
        } else {
            player.getInventory().addItem(item);
        }

        player.sendMessage(lang.get("messages.horse-despawned"));
        return true;
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
