package me.luisgamedev.betterhorses.training;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class TrainingManager {

    private TrainingManager() {}

    public static boolean isTrainingEnabled(FileConfiguration config) {
        return config.getBoolean("training.enabled", false);
    }

    public static boolean isCategoryEnabled(FileConfiguration config, String category) {
        return config.getBoolean("training.categories." + category + ".enabled", true);
    }

    public static void ensureBaseStats(AbstractHorse horse) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        saveBaseIfMissing(horse, data, Attribute.GENERIC_MAX_HEALTH, BetterHorseKeys.BASE_HEALTH);
        saveBaseIfMissing(horse, data, Attribute.GENERIC_MOVEMENT_SPEED, BetterHorseKeys.BASE_SPEED);
        saveBaseIfMissing(horse, data, AttributeResolver.horseJumpStrength(), BetterHorseKeys.BASE_JUMP);
    }

    public static void addRidingUnits(AbstractHorse horse, double units) {
        if (units <= 0) return;
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        if (!isTrainingEnabled(config) || !isCategoryEnabled(config, "riding")) return;

        PersistentDataContainer data = horse.getPersistentDataContainer();
        ensureTrainingData(data);
        double current = data.getOrDefault(BetterHorseKeys.TRAINING_RIDING_UNITS, PersistentDataType.DOUBLE, 0.0);
        data.set(BetterHorseKeys.TRAINING_RIDING_UNITS, PersistentDataType.DOUBLE, current + units);
        recalculateAndApplyBonuses(horse);
    }

    public static void addBrushingUnits(AbstractHorse horse, double units) {
        if (units <= 0) return;
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        if (!isTrainingEnabled(config) || !isCategoryEnabled(config, "brushing")) return;

        PersistentDataContainer data = horse.getPersistentDataContainer();
        ensureTrainingData(data);
        double current = data.getOrDefault(BetterHorseKeys.TRAINING_BRUSHING_UNITS, PersistentDataType.DOUBLE, 0.0);
        data.set(BetterHorseKeys.TRAINING_BRUSHING_UNITS, PersistentDataType.DOUBLE, current + units);
        recalculateAndApplyBonuses(horse);
    }

    public static void addFeedingUnits(AbstractHorse horse, double units) {
        if (units <= 0) return;
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        if (!isTrainingEnabled(config) || !isCategoryEnabled(config, "feeding")) return;

        PersistentDataContainer data = horse.getPersistentDataContainer();
        ensureTrainingData(data);
        double current = data.getOrDefault(BetterHorseKeys.TRAINING_FEEDING_UNITS, PersistentDataType.DOUBLE, 0.0);
        data.set(BetterHorseKeys.TRAINING_FEEDING_UNITS, PersistentDataType.DOUBLE, current + units);
        recalculateAndApplyBonuses(horse);
    }

    public static void recalculateAndApplyBonuses(AbstractHorse horse) {
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        PersistentDataContainer data = horse.getPersistentDataContainer();

        ensureTrainingData(data);
        ensureBaseStats(horse);

        double baseHealth = data.getOrDefault(BetterHorseKeys.BASE_HEALTH, PersistentDataType.DOUBLE, readAttribute(horse, Attribute.GENERIC_MAX_HEALTH));
        double baseSpeed = data.getOrDefault(BetterHorseKeys.BASE_SPEED, PersistentDataType.DOUBLE, readAttribute(horse, Attribute.GENERIC_MOVEMENT_SPEED));
        double baseJump = data.getOrDefault(BetterHorseKeys.BASE_JUMP, PersistentDataType.DOUBLE, readAttribute(horse, AttributeResolver.horseJumpStrength()));

        double ridingPercent = getProgressPercent(config, data, "riding", BetterHorseKeys.TRAINING_RIDING_UNITS);
        double brushingPercent = getProgressPercent(config, data, "brushing", BetterHorseKeys.TRAINING_BRUSHING_UNITS);
        double feedingPercent = getProgressPercent(config, data, "feeding", BetterHorseKeys.TRAINING_FEEDING_UNITS);

        double speedBonusPerPercent = config.getDouble("training.categories.riding.bonus-percent-per-progress-percent", 0.2);
        double jumpBonusPerPercent = config.getDouble("training.categories.brushing.bonus-percent-per-progress-percent", 0.2);
        double healthBonusPerPercent = config.getDouble("training.categories.feeding.bonus-percent-per-progress-percent", 0.2);

        double boostedSpeed = baseSpeed * (1.0 + (ridingPercent * speedBonusPerPercent / 100.0));
        double boostedJump = baseJump * (1.0 + (brushingPercent * jumpBonusPerPercent / 100.0));
        double boostedHealth = baseHealth * (1.0 + (feedingPercent * healthBonusPerPercent / 100.0));

        setAttribute(horse, Attribute.GENERIC_MOVEMENT_SPEED, boostedSpeed);
        setAttribute(horse, AttributeResolver.horseJumpStrength(), boostedJump);

        double oldHealth = readAttribute(horse, Attribute.GENERIC_MAX_HEALTH);
        setAttribute(horse, Attribute.GENERIC_MAX_HEALTH, boostedHealth);
        if (oldHealth > 0) {
            double ratio = horse.getHealth() / oldHealth;
            horse.setHealth(Math.max(1.0, Math.min(boostedHealth, boostedHealth * ratio)));
        }
    }

    public static double getFoodTrainingValue(Material material) {
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        ConfigurationSection section = config.getConfigurationSection("training.categories.feeding.food-values");
        if (section == null) return 0.0;
        return section.getDouble(material.name(), 0.0);
    }

    public static List<String> getTrainingLoreLines(AbstractHorse horse) {
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        FileConfiguration language = BetterHorses.getInstance().getLang().getConfig();
        List<String> lines = new ArrayList<>();
        if (!isTrainingEnabled(config)) return lines;

        ensureTrainingData(horse.getPersistentDataContainer());

        lines.add(color(language.getString("training-lore.title", "&6Training")));
        addCategoryLore(lines, config, language, horse, "riding", BetterHorseKeys.TRAINING_RIDING_UNITS, "&7Riding Progress: %bar% &b%percent%%");
        addCategoryLore(lines, config, language, horse, "brushing", BetterHorseKeys.TRAINING_BRUSHING_UNITS, "&7Brushing Progress: %bar% &b%percent%%");
        addCategoryLore(lines, config, language, horse, "feeding", BetterHorseKeys.TRAINING_FEEDING_UNITS, "&7Feeding Progress: %bar% &b%percent%%");
        return lines;
    }

    public static double getProgressPercent(FileConfiguration config, PersistentDataContainer data, String category, NamespacedKey unitsKey) {
        if (!isTrainingEnabled(config) || !isCategoryEnabled(config, category)) return 0.0;
        ensureTrainingData(data);
        double units = data.getOrDefault(unitsKey, PersistentDataType.DOUBLE, 0.0);
        double unitsPerPercent = Math.max(0.0001, config.getDouble("training.categories." + category + ".units-per-percent", 10.0));
        return Math.max(0.0, Math.min(100.0, units / unitsPerPercent));
    }

    public static void ensureTrainingData(PersistentDataContainer data) {
        ensureTrainingUnits(data, BetterHorseKeys.TRAINING_RIDING_UNITS);
        ensureTrainingUnits(data, BetterHorseKeys.TRAINING_BRUSHING_UNITS);
        ensureTrainingUnits(data, BetterHorseKeys.TRAINING_FEEDING_UNITS);
    }

    private static void ensureTrainingUnits(PersistentDataContainer data, NamespacedKey unitsKey) {
        if (!data.has(unitsKey, PersistentDataType.DOUBLE)) {
            data.set(unitsKey, PersistentDataType.DOUBLE, 0.0);
        }
    }

    private static void addCategoryLore(List<String> lines, FileConfiguration config, FileConfiguration language, AbstractHorse horse, String category, NamespacedKey key, String formatDefault) {
        if (!isCategoryEnabled(config, category)) return;
        PersistentDataContainer data = horse.getPersistentDataContainer();
        double percent = getProgressPercent(config, data, category, key);
        int rounded = (int) Math.round(percent);
        String bar = progressBar(config, language, percent);
        String format = language.getString("training-lore.categories." + category, formatDefault);
        lines.add(color(format.replace("%bar%", bar).replace("%percent%", String.valueOf(rounded))));
    }

    private static String progressBar(FileConfiguration config, FileConfiguration language, double percent) {
        int length = Math.max(5, config.getInt("training.lore.progress-bar.length", 20));
        char filledChar = language.getString("training-lore.progress-bar.filled-char", "■").charAt(0);
        char emptyChar = language.getString("training-lore.progress-bar.empty-char", "■").charAt(0);
        String filledColor = color(language.getString("training-lore.progress-bar.filled-color", "&b"));
        String emptyColor = color(language.getString("training-lore.progress-bar.empty-color", "&8"));

        int filled = (int) Math.round((percent / 100.0) * length);
        StringBuilder builder = new StringBuilder();
        builder.append(filledColor);
        for (int i = 0; i < filled; i++) builder.append(filledChar);
        builder.append(emptyColor);
        for (int i = filled; i < length; i++) builder.append(emptyChar);
        return builder.toString();
    }

    private static void saveBaseIfMissing(AbstractHorse horse, PersistentDataContainer data, Attribute attribute, NamespacedKey key) {
        if (data.has(key, PersistentDataType.DOUBLE)) return;
        AttributeInstance attr = horse.getAttribute(attribute);
        if (attr != null) {
            data.set(key, PersistentDataType.DOUBLE, attr.getBaseValue());
        }
    }

    private static double readAttribute(AbstractHorse horse, Attribute attribute) {
        AttributeInstance attr = horse.getAttribute(attribute);
        return attr != null ? attr.getBaseValue() : 0.0;
    }

    private static void setAttribute(AbstractHorse horse, Attribute attribute, double value) {
        AttributeInstance attr = horse.getAttribute(attribute);
        if (attr != null) {
            attr.setBaseValue(value);
        }
    }

    private static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }
}
