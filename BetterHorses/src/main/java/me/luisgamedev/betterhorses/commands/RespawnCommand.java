package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.events.BetterHorseSpawnEvent;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import me.luisgamedev.betterhorses.utils.HorseArmorUtils;
import me.luisgamedev.betterhorses.utils.MountConfig;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class RespawnCommand {

    public static boolean spawnHorseFromItem(Player player) {
        BetterHorses plugin = BetterHorses.getInstance();
        LanguageManager lang = plugin.getLang();
        plugin.debugLog("HORSE_RESPAWN", "START", true, "Player " + player.getName() + " requested item spawn.");

        ItemStack item = player.getInventory().getItemInMainHand();
        String configuredItem = BetterHorses.getInstance().getConfig().getString("settings.horse-item", "SADDLE");

        Material expectedMaterial = Material.getMaterial(configuredItem.toUpperCase());
        if (expectedMaterial == null || !expectedMaterial.isItem()) expectedMaterial = Material.SADDLE;

        if (item == null || item.getType() != expectedMaterial || !item.hasItemMeta()) {
            player.sendMessage(lang.get("messages.invalid-item"));
            plugin.debugLog("HORSE_RESPAWN", "VALIDATION", false, "Invalid item used by " + player.getName() + ".");
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();

        Double health = data.get(BetterHorseKeys.HEALTH, PersistentDataType.DOUBLE);
        Double currentHealth = data.get(BetterHorseKeys.CURRENT_HEALTH, PersistentDataType.DOUBLE);
        Double speed = data.get(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE);
        Double jump = data.get(BetterHorseKeys.JUMP, PersistentDataType.DOUBLE);
        String gender = data.get(BetterHorseKeys.GENDER, PersistentDataType.STRING);
        String ownerUUID = player.getUniqueId().toString();
        String styleStr = data.get(BetterHorseKeys.STYLE, PersistentDataType.STRING);
        String colorStr = data.get(BetterHorseKeys.COLOR, PersistentDataType.STRING);
        String saddleStr = data.get(BetterHorseKeys.SADDLE, PersistentDataType.STRING);
        String armorStr = data.get(BetterHorseKeys.ARMOR, PersistentDataType.STRING);
        String customName = data.get(BetterHorseKeys.NAME, PersistentDataType.STRING);
        String trait = data.get(BetterHorseKeys.TRAIT, PersistentDataType.STRING);
        Byte neutered = data.get(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE);
        Integer storedStage = data.get(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER);
        String mountTypeName = data.get(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING);
        long brushTrainingCooldown = data.getOrDefault(BetterHorseKeys.TRAINING_BRUSH_COOLDOWN, PersistentDataType.LONG, 0L);
        long feedTrainingCooldown = data.getOrDefault(BetterHorseKeys.TRAINING_FEED_COOLDOWN, PersistentDataType.LONG, 0L);
        Long cooldown = data.has(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG)
                ? data.get(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG)
                : null;

        int growthStage = storedStage != null ? storedStage : 10;
        SupportedMountType mountType = SupportedMountType.fromNameOrDefault(mountTypeName);
        String mountName = mountType.getDisplayName(lang);

        if (health == null || speed == null || jump == null || gender == null) {
            player.sendMessage(lang.getFormatted("messages.invalid-horse-data", "%mount%", mountName));
            plugin.debugLog("HORSE_RESPAWN", "DATA", false, "Missing critical horse data for " + player.getName() + ".");
            return true;
        }

        if (!mountType.isEnabled(BetterHorses.getInstance().getConfig())) {
            player.sendMessage(lang.getFormatted("messages.invalid-horse-data", "%mount%", mountName));
            plugin.debugLog("HORSE_RESPAWN", "MOUNT_TYPE", false, "Mount type disabled: " + mountType.getEntityType());
            return true;
        }

        AbstractHorse horse;
        try {
            horse = mountType.spawn(player.getLocation());
        } catch (Exception e) {
            player.sendMessage(lang.get("messages.cant-spawn"));
            plugin.debugLog("HORSE_RESPAWN", "SPAWN", false, "Mount spawn failed with exception: " + e.getMessage());
            return true;
        }

        if (horse == null || !horse.isValid()) {
            player.sendMessage(lang.get("messages.cant-spawn"));
            plugin.debugLog("HORSE_RESPAWN", "SPAWN", false, "Spawn returned invalid entity for " + player.getName() + ".");
            return true;
        }

        // Set Growth Stage
        double maxScale = BetterHorses.getInstance().getConfig().getDouble("horse-growth-settings.max-size", 1.3);
        int threshold = BetterHorses.getInstance().getConfig().getInt("horse-growth-settings.ride-and-breed-threshhold", 7);
        float minScale = (growthStage >= threshold) ? 0.85f : 0.7f;
        double scale = minScale + ((maxScale - minScale) / 10.0) * growthStage;

        if (MountConfig.isGrowthEnabled(BetterHorses.getInstance().getConfig(), mountType)) {
            setAttribute(horse, Attribute.valueOf("SCALE"), scale);
            if (growthStage >= threshold) {
                horse.setAdult();
                horse.setAgeLock(false);
            } else {
                horse.setBaby();
                horse.setAgeLock(true);
            }
        }

        horse.getPersistentDataContainer().set(
                BetterHorseKeys.GROWTH_STAGE,
                PersistentDataType.INTEGER,
                growthStage
        );

        setAttribute(horse, AttributeResolver.generic("MAX_HEALTH"), health);
        setAttribute(horse, AttributeResolver.generic("MOVEMENT_SPEED"), speed);
        setAttribute(horse, Attribute.valueOf("HORSE_JUMP_STRENGTH"), jump);
        horse.setHealth(currentHealth != null ? currentHealth : health);
        horse.setTamed(true);
        horse.setOwner(player);

        PersistentDataContainer horseData = horse.getPersistentDataContainer();

        horseData.set(BetterHorseKeys.BASE_HEALTH, PersistentDataType.DOUBLE,
                data.getOrDefault(BetterHorseKeys.BASE_HEALTH, PersistentDataType.DOUBLE, health));
        horseData.set(BetterHorseKeys.BASE_SPEED, PersistentDataType.DOUBLE,
                data.getOrDefault(BetterHorseKeys.BASE_SPEED, PersistentDataType.DOUBLE, speed));
        horseData.set(BetterHorseKeys.BASE_JUMP, PersistentDataType.DOUBLE,
                data.getOrDefault(BetterHorseKeys.BASE_JUMP, PersistentDataType.DOUBLE, jump));
        horseData.set(BetterHorseKeys.TRAINING_RIDING_UNITS, PersistentDataType.DOUBLE,
                data.getOrDefault(BetterHorseKeys.TRAINING_RIDING_UNITS, PersistentDataType.DOUBLE, 0.0));
        horseData.set(BetterHorseKeys.TRAINING_BRUSHING_UNITS, PersistentDataType.DOUBLE,
                data.getOrDefault(BetterHorseKeys.TRAINING_BRUSHING_UNITS, PersistentDataType.DOUBLE, 0.0));
        horseData.set(BetterHorseKeys.TRAINING_FEEDING_UNITS, PersistentDataType.DOUBLE,
                data.getOrDefault(BetterHorseKeys.TRAINING_FEEDING_UNITS, PersistentDataType.DOUBLE, 0.0));
        horseData.set(BetterHorseKeys.TRAINING_BRUSH_COOLDOWN, PersistentDataType.LONG, brushTrainingCooldown);
        horseData.set(BetterHorseKeys.TRAINING_FEED_COOLDOWN, PersistentDataType.LONG, feedTrainingCooldown);

        horseData.set(BetterHorseKeys.OWNER, PersistentDataType.STRING, ownerUUID);
        horseData.set(BetterHorseKeys.GENDER, PersistentDataType.STRING, gender);
        horseData.set(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING, mountType.getEntityType().name());

        if (trait != null && !trait.isBlank()) {
            horseData.set(BetterHorseKeys.TRAIT, PersistentDataType.STRING, trait);
        }

        if (neutered != null && neutered == (byte) 1) {
            horseData.set(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE, (byte) 1);
        }

        if (cooldown != null) {
            horseData.set(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG, cooldown);
        }

        if (customName != null && !customName.isBlank()) {
            horse.setCustomName(customName);
            horse.setCustomNameVisible(true);
        }

        if (horse instanceof Horse h) {
            try {
                h.setStyle(Horse.Style.valueOf(styleStr));
                h.setColor(Horse.Color.valueOf(colorStr));
            } catch (Exception ignored) {}
        }

        if (saddleStr != null) {
            horse.getInventory().setSaddle(new ItemStack(Material.valueOf(saddleStr)));
        }
        if (armorStr != null) {
            HorseArmorUtils.setArmor(horse.getInventory(), new ItemStack(Material.valueOf(armorStr)));
        }

        item.setAmount(item.getAmount() - 1);
        BetterHorsesAPI.callSpawnEvent(horse, item, BetterHorseSpawnEvent.SpawnCause.ITEM);
        player.sendMessage(lang.getFormatted("messages.horse-respawned", "%mount%", mountName));
        plugin.debugLog("HORSE_RESPAWN", "COMPLETE", true, "Player " + player.getName() + " spawned " + mountName + ".");
        return true;
    }

    private static void setAttribute(AbstractHorse horse, Attribute attribute, double value) {
        AttributeInstance attr = horse.getAttribute(attribute);
        if (attr != null) {
            attr.setBaseValue(value);
        }
    }
}
