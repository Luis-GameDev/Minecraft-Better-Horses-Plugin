package me.luisgamedev.betterhorses.training;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import me.luisgamedev.betterhorses.traits.TraitRegistry;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

public final class TrainingManager {

    private static final int STAT_MODEL = 1;
    private static final UUID HEALTH_BONUS = modifierId("health");
    private static final UUID SPEED_BONUS = modifierId("speed");
    private static final UUID JUMP_BONUS = modifierId("jump");

    private TrainingManager() {}

    private static UUID modifierId(String stat) {
        return UUID.nameUUIDFromBytes(("betterhorses:training:" + stat).getBytes(StandardCharsets.UTF_8));
    }

    private static NamespacedKey statModelKey() {
        return new NamespacedKey(BetterHorses.getInstance(), "training_stat_model");
    }

    public static boolean hasStatModel(AbstractHorse horse) {
        return horse.getPersistentDataContainer().getOrDefault(statModelKey(), PersistentDataType.INTEGER, 0) == STAT_MODEL;
    }

    public static boolean isTrainingEnabled(FileConfiguration config) {
        return config.getBoolean("training.enabled", false);
    }

    public static boolean isCategoryEnabled(FileConfiguration config, String category) {
        return config.getBoolean("training.categories." + category + ".enabled", true);
    }

    public static void ensureBaseStats(AbstractHorse horse) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        saveBaseIfMissing(horse, data, AttributeResolver.generic("MAX_HEALTH"), BetterHorseKeys.HEALTH, BetterHorseKeys.BASE_HEALTH);
        saveBaseIfMissing(horse, data, AttributeResolver.generic("MOVEMENT_SPEED"), BetterHorseKeys.SPEED, BetterHorseKeys.BASE_SPEED);
        saveBaseIfMissing(horse, data, AttributeResolver.horseJumpStrength(), BetterHorseKeys.JUMP, BetterHorseKeys.BASE_JUMP);
    }

    public static void addRidingUnits(AbstractHorse horse, double units) {
        if (!Double.isFinite(units) || units <= 0) return;
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        if (!isTrainingEnabled(config) || !isCategoryEnabled(config, "riding")) return;

        PersistentDataContainer data = horse.getPersistentDataContainer();
        ensureTrainingData(data);
        double current = data.getOrDefault(BetterHorseKeys.TRAINING_RIDING_UNITS, PersistentDataType.DOUBLE, 0.0);
        data.set(BetterHorseKeys.TRAINING_RIDING_UNITS, PersistentDataType.DOUBLE, finiteSum(current, units));
        recalculateAndApplyBonuses(horse);
    }

    public static void addBrushingUnits(AbstractHorse horse, double units) {
        if (!Double.isFinite(units) || units <= 0) return;
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        if (!isTrainingEnabled(config) || !isCategoryEnabled(config, "brushing")) return;

        PersistentDataContainer data = horse.getPersistentDataContainer();
        ensureTrainingData(data);
        double current = data.getOrDefault(BetterHorseKeys.TRAINING_BRUSHING_UNITS, PersistentDataType.DOUBLE, 0.0);
        data.set(BetterHorseKeys.TRAINING_BRUSHING_UNITS, PersistentDataType.DOUBLE, finiteSum(current, units));
        recalculateAndApplyBonuses(horse);
    }

    public static void addFeedingUnits(AbstractHorse horse, double units) {
        if (!Double.isFinite(units) || units <= 0) return;
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        if (!isTrainingEnabled(config) || !isCategoryEnabled(config, "feeding")) return;

        PersistentDataContainer data = horse.getPersistentDataContainer();
        ensureTrainingData(data);
        double current = data.getOrDefault(BetterHorseKeys.TRAINING_FEEDING_UNITS, PersistentDataType.DOUBLE, 0.0);
        data.set(BetterHorseKeys.TRAINING_FEEDING_UNITS, PersistentDataType.DOUBLE, finiteSum(current, units));
        recalculateAndApplyBonuses(horse);
    }

    public static void recalculateAndApplyBonuses(AbstractHorse horse) {
        applyBonuses(horse, captureHealth(horse));
    }

    /** Set a genetic stat, keeping training as a separate gameplay-only modifier. */
    public static void setBaseStat(AbstractHorse horse, Attribute attribute, NamespacedKey statKey,
                                   NamespacedKey baseKey, double value) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Stat must be finite and positive");
        HealthSnapshot health = captureHealth(horse);
        if (!hasStatModel(horse)) applyBonuses(horse, health);
        if (attribute == AttributeResolver.generic("MOVEMENT_SPEED")) TraitRegistry.revertDashBoostIfActive(horse);
        AttributeInstance instance = horse.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
        PersistentDataContainer data = horse.getPersistentDataContainer();
        data.set(statKey, PersistentDataType.DOUBLE, value);
        data.set(baseKey, PersistentDataType.DOUBLE, value);
        applyBonuses(horse, health);
    }

    private static void applyBonuses(AbstractHorse horse, HealthSnapshot health) {
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        PersistentDataContainer data = horse.getPersistentDataContainer();
        boolean legacy = !hasStatModel(horse);
        // A legacy dash task otherwise restores its saved, training-boosted base later.
        if (legacy) TraitRegistry.revertDashBoostIfActive(horse);
        ensureTrainingData(data);
        ensureBaseStats(horse);
        synchronizeStat(horse, data, AttributeResolver.generic("MAX_HEALTH"), BetterHorseKeys.HEALTH, BetterHorseKeys.BASE_HEALTH, legacy);
        synchronizeStat(horse, data, AttributeResolver.generic("MOVEMENT_SPEED"), BetterHorseKeys.SPEED, BetterHorseKeys.BASE_SPEED, legacy);
        synchronizeStat(horse, data, AttributeResolver.horseJumpStrength(), BetterHorseKeys.JUMP, BetterHorseKeys.BASE_JUMP, legacy);
        data.set(statModelKey(), PersistentDataType.INTEGER, STAT_MODEL);

        replaceBonus(horse.getAttribute(AttributeResolver.generic("MOVEMENT_SPEED")), SPEED_BONUS,
                bonusAmount(config, data, "riding", BetterHorseKeys.TRAINING_RIDING_UNITS));
        replaceBonus(horse.getAttribute(AttributeResolver.horseJumpStrength()), JUMP_BONUS,
                bonusAmount(config, data, "brushing", BetterHorseKeys.TRAINING_BRUSHING_UNITS));
        replaceBonus(horse.getAttribute(AttributeResolver.generic("MAX_HEALTH")), HEALTH_BONUS,
                bonusAmount(config, data, "feeding", BetterHorseKeys.TRAINING_FEEDING_UNITS));
        restoreHealth(horse, health);
    }

    private static void synchronizeStat(AbstractHorse horse, PersistentDataContainer data, Attribute attribute,
                                        NamespacedKey statKey, NamespacedKey baseKey, boolean legacy) {
        Double genetic = data.get(baseKey, PersistentDataType.DOUBLE);
        if (genetic == null || !Double.isFinite(genetic) || genetic <= 0) return;
        AttributeInstance instance = horse.getAttribute(attribute);
        if (legacy && instance != null && instance.getBaseValue() != genetic) instance.setBaseValue(genetic);
        data.set(statKey, PersistentDataType.DOUBLE, genetic);
    }

    private static double bonusAmount(FileConfiguration config, PersistentDataContainer data,
                                      String category, NamespacedKey unitsKey) {
        double progress = getProgressPercent(config, data, category, unitsKey);
        double bonus = config.getDouble("training.categories." + category + ".bonus-percent-per-progress-percent", 0.2);
        // Preserve configured decreases too, while keeping the effective factor above zero.
        return Double.isFinite(bonus) ? Math.max(-0.999999, (progress / 100.0) * bonus) : 0.0;
    }

    private static void replaceBonus(AttributeInstance attribute, UUID id, double amount) {
        if (attribute == null) return;
        for (AttributeModifier modifier : new ArrayList<>(attribute.getModifiers())) {
            if (modifier.getUniqueId().equals(id)) attribute.removeModifier(modifier);
        }
        if (Double.isFinite(amount) && amount != 0) {
            // Persist the modifier so native entity loading restores max health before Health.
            // Genetic bases and serialized horse stats remain unchanged.
            attribute.addModifier(new AttributeModifier(id, "betterhorses-training", amount,
                    AttributeModifier.Operation.ADD_SCALAR));
        }
    }

    private record HealthSnapshot(double current, double maximum) { }

    private static HealthSnapshot captureHealth(AbstractHorse horse) {
        AttributeInstance maximum = horse.getAttribute(AttributeResolver.generic("MAX_HEALTH"));
        return new HealthSnapshot(horse.getHealth(), maximum == null ? 0.0 : maximum.getValue());
    }

    private static void restoreHealth(AbstractHorse horse, HealthSnapshot before) {
        AttributeInstance attribute = horse.getAttribute(AttributeResolver.generic("MAX_HEALTH"));
        if (attribute == null || !Double.isFinite(before.current) || before.current <= 0) return;
        double maximum = attribute.getValue();
        if (!Double.isFinite(maximum) || maximum <= 0) return;
        boolean validMaximum = Double.isFinite(before.maximum) && before.maximum > 0;
        double ratio = validMaximum ? Math.max(0.0, Math.min(1.0, before.current / before.maximum)) : 0.0;
        double restored = validMaximum ? maximum * ratio : Math.min(before.current, maximum);
        horse.setHealth(Math.max(0.0, Math.min(maximum, restored)));
    }

    private static double finiteSum(double current, double added) {
        double sum = current + added;
        return Double.isFinite(sum) ? sum : Double.MAX_VALUE;
    }

    public static double getFoodTrainingValue(Material material) {
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        ConfigurationSection section = config.getConfigurationSection("training.categories.feeding.food-values");
        if (section == null) return 0.0;
        return section.getDouble(material.name(), 0.0);
    }

    public static String getTrainingTitleLine(PersistentDataContainer data) {
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        FileConfiguration language = BetterHorses.getInstance().getLang().getConfig();
        if (!isTrainingLoreEnabled(config)) return "";

        String title = color(language.getString("training-lore.title", "&6Training"));
        if (!shouldCollapseToTitleComplete(config, data)) {
            return title;
        }

        return title + " " + completeText(language);
    }

    public static boolean isTrainingLoreEnabled(FileConfiguration config) {
        return isTrainingEnabled(config) && config.getBoolean("training.lore.enabled", true);
    }

    public static String getTrainingCategoryLine(PersistentDataContainer data, String category) {
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        FileConfiguration language = BetterHorses.getInstance().getLang().getConfig();
        if (!isTrainingLoreEnabled(config) || !isCategoryEnabled(config, category)) return "";
        if (shouldCollapseToTitleComplete(config, data)) return "";

        ensureTrainingData(data);
        NamespacedKey key = switch (category.toLowerCase()) {
            case "riding" -> BetterHorseKeys.TRAINING_RIDING_UNITS;
            case "brushing" -> BetterHorseKeys.TRAINING_BRUSHING_UNITS;
            case "feeding" -> BetterHorseKeys.TRAINING_FEEDING_UNITS;
            default -> null;
        };

        if (key == null) return "";

        double percent = getProgressPercent(config, data, category, key);
        int rounded = (int) Math.round(percent);
        if (shouldReplaceWithComplete(config, percent)) {
            return color(language.getString("training-lore.categories." + category + "-name")) + " " + completeText(language);
        }

        String bar = progressBar(config, language, percent);
        String defaultFormat = switch (category.toLowerCase()) {
            case "riding" -> "&7Riding: %bar% &b%percent%%";
            case "brushing" -> "&7Brushing: %bar% &b%percent%%";
            case "feeding" -> "&7Feeding: %bar% &b%percent%%";
            default -> "";
        };

        String format = language.getString("training-lore.categories." + category.toLowerCase(), defaultFormat);
        return color(format.replace("%bar%", bar).replace("%percent%", String.valueOf(rounded)));
    }

    public static double getProgressPercent(FileConfiguration config, PersistentDataContainer data, String category, NamespacedKey unitsKey) {
        if (!isTrainingEnabled(config) || !isCategoryEnabled(config, category)) return 0.0;
        ensureTrainingData(data);
        double units = data.getOrDefault(unitsKey, PersistentDataType.DOUBLE, 0.0);
        double configuredUnits = config.getDouble("training.categories." + category + ".units-per-percent", 10.0);
        double unitsPerPercent = Double.isFinite(configuredUnits) ? Math.max(0.0001, configuredUnits) : 10.0;
        return Math.max(0.0, Math.min(100.0, units / unitsPerPercent));
    }

    public static void ensureTrainingData(PersistentDataContainer data) {
        ensureTrainingUnits(data, BetterHorseKeys.TRAINING_RIDING_UNITS);
        ensureTrainingUnits(data, BetterHorseKeys.TRAINING_BRUSHING_UNITS);
        ensureTrainingUnits(data, BetterHorseKeys.TRAINING_FEEDING_UNITS);
    }

    private static void ensureTrainingUnits(PersistentDataContainer data, NamespacedKey unitsKey) {
        Double value = data.get(unitsKey, PersistentDataType.DOUBLE);
        if (value == null || !Double.isFinite(value) || value < 0) data.set(unitsKey, PersistentDataType.DOUBLE, 0.0);
    }

    private static boolean shouldReplaceWithComplete(FileConfiguration config, double percent) {
        return config.getBoolean("training.lore.progress-bar.hide-on-complete", true) && percent >= 100.0;
    }

    private static boolean shouldCollapseToTitleComplete(FileConfiguration config, PersistentDataContainer data) {
        if (!config.getBoolean("training.lore.progress-bar.hide-on-complete", true) || !config.getBoolean("training.lore.progress-bar.combine-all-complete", true)) return false;

        ensureTrainingData(data);
        String[] categories = {"riding", "brushing", "feeding"};
        boolean hasEnabledCategory = false;

        for (String category : categories) {
            if (!isCategoryEnabled(config, category)) {
                continue;
            }

            hasEnabledCategory = true;
            NamespacedKey key = switch (category) {
                case "riding" -> BetterHorseKeys.TRAINING_RIDING_UNITS;
                case "brushing" -> BetterHorseKeys.TRAINING_BRUSHING_UNITS;
                case "feeding" -> BetterHorseKeys.TRAINING_FEEDING_UNITS;
                default -> null;
            };

            if (key == null || getProgressPercent(config, data, category, key) < 100.0) {
                return false;
            }
        }

        return hasEnabledCategory;
    }

    private static String completeText(FileConfiguration language) {
        String filledColor = color(language.getString("training-lore.progress-bar.filled-color", "&b"));
        String complete = color(language.getString("training-lore.complete", "completed"));
        return filledColor + ChatColor.stripColor(complete);
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

    private static void saveBaseIfMissing(AbstractHorse horse, PersistentDataContainer data, Attribute attribute,
                                          NamespacedKey statKey, NamespacedKey baseKey) {
        Double existing = data.get(baseKey, PersistentDataType.DOUBLE);
        if (existing != null && Double.isFinite(existing) && existing > 0) return;
        Double value = data.get(statKey, PersistentDataType.DOUBLE);
        AttributeInstance instance = horse.getAttribute(attribute);
        if ((value == null || !Double.isFinite(value) || value <= 0) && instance != null) value = instance.getBaseValue();
        if (value != null && Double.isFinite(value) && value > 0) data.set(baseKey, PersistentDataType.DOUBLE, value);
    }

    private static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }
}
