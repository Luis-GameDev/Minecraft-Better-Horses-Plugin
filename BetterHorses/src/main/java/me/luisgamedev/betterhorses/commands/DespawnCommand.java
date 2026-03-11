package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.traits.TraitRegistry;
import me.luisgamedev.betterhorses.training.TrainingManager;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import me.luisgamedev.betterhorses.utils.HorseArmorUtils;
import me.luisgamedev.betterhorses.utils.MountConfig;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AnimalTamer;
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
        BetterHorses plugin = BetterHorses.getInstance();
        LanguageManager lang = plugin.getLang();
        plugin.debugLog("HORSE_DESPAWN", "START", true, "Player " + player.getName() + " requested despawn.");

        if (!(player.getVehicle() instanceof AbstractHorse horse)) {
            player.sendMessage(lang.get("messages.invalid-vehicle"));
            plugin.debugLog("HORSE_DESPAWN", "VALIDATION", false, "Player " + player.getName() + " is not riding a supported mount.");
            return true;
        }

        SupportedMountType mountType = SupportedMountType.fromEntity(horse)
                .filter(type -> type.isEnabled(BetterHorses.getInstance().getConfig()))
                .orElse(null);

        if (mountType == null) {
            player.sendMessage(lang.get("messages.invalid-vehicle"));
            plugin.debugLog("HORSE_DESPAWN", "MOUNT_TYPE", false, "Mount type disabled or unsupported for player " + player.getName() + ".");
            return true;
        }

        String mountName = mountType.getDisplayName(lang);

        PersistentDataContainer data = horse.getPersistentDataContainer();
        NamespacedKey genderKey = BetterHorseKeys.GENDER;
        NamespacedKey ownerKey = BetterHorseKeys.OWNER;
        NamespacedKey traitKey = BetterHorseKeys.TRAIT;
        NamespacedKey neuterKey = BetterHorseKeys.NEUTERED;
        NamespacedKey growthKey = BetterHorseKeys.GROWTH_STAGE;
        NamespacedKey cooldownKey = BetterHorseKeys.COOLDOWN;

        String storedOwner = data.get(ownerKey, PersistentDataType.STRING);
        boolean ownershipRequired = mountType != SupportedMountType.CAMEL || storedOwner != null;
        boolean isOwner = storedOwner != null && storedOwner.equals(player.getUniqueId().toString());

        if (storedOwner == null) {
            AnimalTamer owner = horse.getOwner();
            isOwner = horse.isTamed() && owner != null && owner.getUniqueId().equals(player.getUniqueId());
        }

        if (ownershipRequired && !isOwner) {
            player.sendMessage(lang.getFormatted("messages.not-horse-owner", "%mount%", mountName));
            plugin.debugLog("HORSE_DESPAWN", "OWNERSHIP", false, "Player " + player.getName() + " is not owner of " + horse.getUniqueId() + ".");
            return true;
        }

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
        TrainingManager.ensureBaseStats(horse);

        double maxHealth = horse.getAttribute(AttributeResolver.generic("MAX_HEALTH")).getBaseValue();
        double currentHealth = horse.getHealth();
        double speed = horse.getAttribute(AttributeResolver.generic("MOVEMENT_SPEED")).getBaseValue();
        AttributeInstance jumpAttr = horse.getAttribute(Attribute.valueOf("HORSE_JUMP_STRENGTH"));
        double jump = jumpAttr != null ? jumpAttr.getBaseValue() : 0.0;

        Horse.Style style = horse instanceof Horse ? ((Horse) horse).getStyle() : Horse.Style.WHITE;
        Horse.Color color = horse instanceof Horse ? ((Horse) horse).getColor() : Horse.Color.WHITE;
        AbstractHorseInventory inv = horse.getInventory();
        ItemStack saddle = inv.getSaddle();
        ItemStack armor = HorseArmorUtils.getArmor(inv);

        String itemMaterialName = BetterHorses.getInstance().getConfig().getString("settings.horse-item", "SADDLE");
        Material material = Material.getMaterial(itemMaterialName.toUpperCase());
        if (material == null || !material.isItem()) material = Material.SADDLE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer itemData = meta.getPersistentDataContainer();

        itemData.set(genderKey, PersistentDataType.STRING, gender);

        String name = horse.getCustomName() != null ? horse.getCustomName() : mountName;
        meta.setDisplayName(ChatColor.GOLD + name + " " + genderSymbol);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-gender", "%value%", genderSymbol));
        lore.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-health", "%value%", String.format("%.2f", currentHealth), "%max%", String.format("%.2f", maxHealth)));
        lore.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-speed", "%value%", String.format("%.4f", speed)));
        lore.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-jump", "%value%", String.format("%.4f", jump)));
        lore.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-growth", "%value%", String.format("%d", growthStage)));
        lore.addAll(TrainingManager.getTrainingLoreLines(horse));

        if (trait != null) {
            lore.add(ChatColor.GOLD + lang.getFormattedRaw("messages.trait-line", "%trait%", formatTraitName(trait)));
        }
        if (isNeutered) {
            lore.add(ChatColor.DARK_GRAY + lang.getRaw("messages.lore-neutered"));
        }

        meta.setLore(lore);

        if (horse.getCustomName() != null) {
            itemData.set(BetterHorseKeys.NAME, PersistentDataType.STRING, horse.getCustomName());
        }
        itemData.set(BetterHorseKeys.HEALTH, PersistentDataType.DOUBLE, maxHealth);
        itemData.set(BetterHorseKeys.CURRENT_HEALTH, PersistentDataType.DOUBLE, currentHealth);
        itemData.set(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE, speed);
        itemData.set(BetterHorseKeys.JUMP, PersistentDataType.DOUBLE, jump);
        itemData.set(BetterHorseKeys.BASE_HEALTH, PersistentDataType.DOUBLE, data.getOrDefault(BetterHorseKeys.BASE_HEALTH, PersistentDataType.DOUBLE, maxHealth));
        itemData.set(BetterHorseKeys.BASE_SPEED, PersistentDataType.DOUBLE, data.getOrDefault(BetterHorseKeys.BASE_SPEED, PersistentDataType.DOUBLE, speed));
        itemData.set(BetterHorseKeys.BASE_JUMP, PersistentDataType.DOUBLE, data.getOrDefault(BetterHorseKeys.BASE_JUMP, PersistentDataType.DOUBLE, jump));
        itemData.set(BetterHorseKeys.TRAINING_RIDING_UNITS, PersistentDataType.DOUBLE, data.getOrDefault(BetterHorseKeys.TRAINING_RIDING_UNITS, PersistentDataType.DOUBLE, 0.0));
        itemData.set(BetterHorseKeys.TRAINING_BRUSHING_UNITS, PersistentDataType.DOUBLE, data.getOrDefault(BetterHorseKeys.TRAINING_BRUSHING_UNITS, PersistentDataType.DOUBLE, 0.0));
        itemData.set(BetterHorseKeys.TRAINING_FEEDING_UNITS, PersistentDataType.DOUBLE, data.getOrDefault(BetterHorseKeys.TRAINING_FEEDING_UNITS, PersistentDataType.DOUBLE, 0.0));
        itemData.set(BetterHorseKeys.OWNER, PersistentDataType.STRING, player.getUniqueId().toString());
        itemData.set(BetterHorseKeys.STYLE, PersistentDataType.STRING, style.name());
        itemData.set(BetterHorseKeys.COLOR, PersistentDataType.STRING, color.name());
        itemData.set(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER, growthStage);
        itemData.set(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING, mountType.getEntityType().name());
        if (trait != null) {
            itemData.set(traitKey, PersistentDataType.STRING, trait.toLowerCase());
        }
        if (isNeutered) {
            itemData.set(neuterKey, PersistentDataType.BYTE, (byte) 1);
        }
        if (cooldown != null) {
            itemData.set(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG, cooldown);
        }
        if (saddle != null) itemData.set(BetterHorseKeys.SADDLE, PersistentDataType.STRING, saddle.getType().name());
        if (armor != null) itemData.set(BetterHorseKeys.ARMOR, PersistentDataType.STRING, armor.getType().name());

        item.setItemMeta(meta);
        boolean wasLeashed = horse.isLeashed();

        if (BetterHorsesAPI.callDespawnEvent(horse, item)) {
            plugin.debugLog("HORSE_DESPAWN", "EVENT", false, "Despawn was cancelled for horse " + horse.getUniqueId() + ".");
            return true;
        }

        horse.remove();

        if (horse.isValid()) {
            player.sendMessage(lang.get("messages.cant-despawn"));
            plugin.debugLog("HORSE_DESPAWN", "REMOVE", false, "Horse remained valid after remove call: " + horse.getUniqueId());
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

        player.sendMessage(lang.getFormatted("messages.horse-despawned", "%mount%", mountName));
        plugin.debugLog("HORSE_DESPAWN", "COMPLETE", true, "Player " + player.getName() + " despawned " + mountName + ".");
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
