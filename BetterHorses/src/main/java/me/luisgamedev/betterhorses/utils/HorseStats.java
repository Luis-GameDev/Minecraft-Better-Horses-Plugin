package me.luisgamedev.betterhorses.utils;

import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Read-only genetic stats and health presentation, excluding training bonuses. */
public final class HorseStats {
    private HorseStats() { }

    public static double maxHealth(AbstractHorse horse) {
        return stat(horse, BetterHorseKeys.HEALTH, AttributeResolver.generic("MAX_HEALTH"));
    }

    public static double speed(AbstractHorse horse) {
        return stat(horse, BetterHorseKeys.SPEED, AttributeResolver.generic("MOVEMENT_SPEED"));
    }

    public static double jump(AbstractHorse horse) {
        return stat(horse, BetterHorseKeys.JUMP, AttributeResolver.horseJumpStrength());
    }

    public static double currentHealth(AbstractHorse horse) {
        double target = maxHealth(horse);
        AttributeInstance attribute = horse.getAttribute(AttributeResolver.generic("MAX_HEALTH"));
        double maximum = attribute == null ? target : attribute.getValue();
        return projectHealth(horse.getHealth(), maximum, target);
    }

    /** Accept public stat keys, their base aliases, and internal transformation aliases. */
    public static Double stored(PersistentDataContainer data, NamespacedKey statKey) {
        NamespacedKey key = publicKey(statKey);
        if (key == null) return null;
        NamespacedKey base = key.equals(BetterHorseKeys.HEALTH) ? BetterHorseKeys.BASE_HEALTH
                : key.equals(BetterHorseKeys.SPEED) ? BetterHorseKeys.BASE_SPEED : BetterHorseKeys.BASE_JUMP;
        Double value = positive(read(data, base));
        if (value == null) value = positive(read(data, key));
        if (value == null && !key.equals(statKey)) value = positive(read(data, statKey));
        return value;
    }

    /** Old items stored effective health; new items already store health on the base scale. */
    public static Double storedCurrentHealth(PersistentDataContainer data) {
        Double target = stored(data, BetterHorseKeys.HEALTH);
        if (target == null) return null;
        Double current = read(data, BetterHorseKeys.CURRENT_HEALTH);
        if (current == null) return target;
        Double maximum = positive(read(data, BetterHorseKeys.HEALTH));
        return projectHealth(current, maximum == null ? target : maximum, target);
    }

    /** Keep the damage ratio while presenting it on a different maximum-health scale. */
    public static double projectHealth(double current, double maximum, double target) {
        if (!HorseDataValidator.finitePositive(target) || !Double.isFinite(current) || current <= 0.0D) return 0.0D;
        if (!HorseDataValidator.finitePositive(maximum)) return Math.min(current, target);
        return target * Math.min(1.0D, current / maximum);
    }

    private static double stat(AbstractHorse horse, NamespacedKey key, Attribute attribute) {
        Double value = stored(horse.getPersistentDataContainer(), key);
        if (value != null) return value;
        AttributeInstance instance = horse.getAttribute(attribute);
        Double fallback = instance == null ? null : positive(instance.getBaseValue());
        return fallback == null ? 0.0D : fallback;
    }

    private static Double positive(Double value) {
        return value != null && HorseDataValidator.finitePositive(value) ? value : null;
    }

    private static Double read(PersistentDataContainer data, NamespacedKey key) {
        return data.has(key, PersistentDataType.DOUBLE) ? data.get(key, PersistentDataType.DOUBLE) : null;
    }

    private static NamespacedKey publicKey(NamespacedKey key) {
        if (BetterHorseKeys.HEALTH.equals(key) || BetterHorseKeys.BASE_HEALTH.equals(key)
                || BetterHorseKeys.UNDEAD_ORIGINAL_HEALTH.equals(key)) return BetterHorseKeys.HEALTH;
        if (BetterHorseKeys.SPEED.equals(key) || BetterHorseKeys.BASE_SPEED.equals(key)
                || BetterHorseKeys.UNDEAD_ORIGINAL_SPEED.equals(key)) return BetterHorseKeys.SPEED;
        if (BetterHorseKeys.JUMP.equals(key) || BetterHorseKeys.BASE_JUMP.equals(key)
                || BetterHorseKeys.UNDEAD_ORIGINAL_JUMP.equals(key)) return BetterHorseKeys.JUMP;
        return null;
    }
}
