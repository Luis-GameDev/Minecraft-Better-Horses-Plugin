package me.luisgamedev.betterhorses.growing;

import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/** Persistent individual variation, composed after custom growth and never inherited. */
public final class HorseSize {
    private static final double DEFAULT_FACTOR = 0.1D;
    private static final double MIN_SCALE = 0.0625D;
    private static final double MAX_SCALE = 16.0D;

    private HorseSize() { }

    /** Assign once, including for older mounts/items; toggling the feature never rerolls it. */
    public static double ensure(PersistentDataContainer data, ConfigurationSection configuration) {
        return ensure(data, configuration, () -> ThreadLocalRandom.current().nextDouble());
    }

    static double ensure(PersistentDataContainer data, ConfigurationSection configuration, DoubleSupplier random) {
        Double stored = data.has(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE)
                ? data.get(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE) : null;
        if (stored != null && Double.isFinite(stored) && stored > 0.0D) return stored;
        double factor = configuration.getDouble("random-size-factor.factor", DEFAULT_FACTOR);
        if (!Double.isFinite(factor)) factor = DEFAULT_FACTOR;
        factor = Math.max(0.0D, Math.min(0.99D, factor));
        double unit = random.getAsDouble();
        if (!Double.isFinite(unit)) unit = 0.5D;
        unit = Math.max(0.0D, Math.min(1.0D, unit));
        double multiplier = Math.max(Double.MIN_VALUE, 1.0D + (unit * 2.0D - 1.0D) * factor);
        data.set(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE, multiplier);
        return multiplier;
    }

    public static void copy(PersistentDataContainer source, PersistentDataContainer target,
                            ConfigurationSection configuration) {
        target.set(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE, ensure(source, configuration));
    }

    /** Final attribute value, after baby-model normalization and growth; never compound past applications. */
    public static double compose(double growthScale, double multiplier, boolean enabled) {
        double base = Double.isFinite(growthScale) && growthScale > 0.0D ? growthScale : 1.0D;
        double individual = enabled && Double.isFinite(multiplier) && multiplier > 0.0D ? multiplier : 1.0D;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, base * individual));
    }

    public static void apply(AbstractHorse horse, double growthScale, ConfigurationSection configuration) {
        double multiplier = ensure(horse.getPersistentDataContainer(), configuration);
        try {
            AttributeInstance attribute = horse.getAttribute(AttributeResolver.generic("SCALE"));
            if (attribute != null) {
                boolean enabled = configuration.getBoolean("random-size-factor.enabled", true);
                double scale = compose(growthScale, multiplier, enabled);
                attribute.setBaseValue(scale);
                if (enabled) horse.getPersistentDataContainer().set(BetterHorseKeys.RANDOM_SIZE_APPLIED_SCALE,
                        PersistentDataType.DOUBLE, scale);
                else horse.getPersistentDataContainer().remove(BetterHorseKeys.RANDOM_SIZE_APPLIED_SCALE);
            }
        } catch (IllegalArgumentException | NoSuchFieldError ignored) {
            // 1.20.4 has no SCALE attribute. Keep the data for later supported servers.
        }
    }

    /** Without custom growth, disabling removes our last size once and leaves unrelated scales alone. */
    public static void applyWithoutGrowth(AbstractHorse horse, ConfigurationSection configuration) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        ensure(data, configuration);
        if (configuration.getBoolean("random-size-factor.enabled", true)) {
            apply(horse, 1.0D, configuration);
            return;
        }
        if (!data.has(BetterHorseKeys.RANDOM_SIZE_APPLIED_SCALE, PersistentDataType.DOUBLE)) return;
        Double applied = data.get(BetterHorseKeys.RANDOM_SIZE_APPLIED_SCALE, PersistentDataType.DOUBLE);
        try {
            AttributeInstance attribute = horse.getAttribute(AttributeResolver.generic("SCALE"));
            if (attribute == null) return;
            if (applied != null && Double.compare(attribute.getBaseValue(), applied) == 0) attribute.setBaseValue(1.0D);
            data.remove(BetterHorseKeys.RANDOM_SIZE_APPLIED_SCALE);
        } catch (IllegalArgumentException | NoSuchFieldError ignored) {
            // Keep the marker until an attribute-capable server can remove the old applied size.
        }
    }

}
