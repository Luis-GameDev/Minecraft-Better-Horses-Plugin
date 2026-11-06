package me.luisgamedev.betterhorses.utils;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Resolves renamed API members without linking fields absent on Minecraft 1.20.4. */
public final class RuntimeCompat {
    private RuntimeCompat() { }

    public static Particle particle(String... names) {
        List<String> aliases = new ArrayList<>();
        for (String name : names) {
            String key = normalize(name);
            aliases.add(key);
            if (key.equals("SMOKE")) aliases.add("SMOKE_NORMAL");
            if (key.equals("SMOKE_NORMAL")) aliases.add("SMOKE");
            if (key.equals("WITCH")) aliases.add("SPELL_WITCH");
            if (key.equals("SPELL_WITCH")) aliases.add("WITCH");
        }
        return constant(Particle.class, aliases.toArray(String[]::new));
    }

    public static PotionEffectType potion(String... names) {
        return constant(PotionEffectType.class, names);
    }

    public static Enchantment enchantment(String... names) {
        return constant(Enchantment.class, names);
    }

    private static <T> T constant(Class<T> type, String... names) {
        for (String name : names) {
            try {
                Field field = type.getField(normalize(name));
                if (!Modifier.isStatic(field.getModifiers())) continue;
                Object value = field.get(null);
                if (type.isInstance(value)) return type.cast(value);
            } catch (ReflectiveOperationException ignored) {
                // Older versions expose the same value under its legacy name.
            }
        }
        throw new IllegalArgumentException("No supported " + type.getSimpleName() + " for " + String.join(", ", names));
    }

    /** Custom maximum durability was added after 1.20.4; material durability is the older contract. */
    public static int maximumDamage(Damageable metadata, Material material) {
        try {
            if (Boolean.TRUE.equals(Damageable.class.getMethod("hasMaxDamage").invoke(metadata))) {
                Object maximum = Damageable.class.getMethod("getMaxDamage").invoke(metadata);
                if (maximum instanceof Number value) return Math.max(0, value.intValue());
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // This interface has no custom maximum on 1.20.4.
        }
        return material.getMaxDurability();
    }

    private static String normalize(String name) {
        return name.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
