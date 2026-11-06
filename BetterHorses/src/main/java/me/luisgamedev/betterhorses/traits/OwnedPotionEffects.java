package me.luisgamedev.betterhorses.traits;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks bounded effects by their expiry rather than removing an unrelated replacement effect. */
public final class OwnedPotionEffects {
    private static final Map<UUID, Map<PotionEffectType, Long>> EXPIRIES = new ConcurrentHashMap<>();

    private OwnedPotionEffects() { }

    public static void apply(LivingEntity entity, PotionEffectType type, int ticks) {
        if (!org.bukkit.Bukkit.isOwnedByCurrentRegion(entity)) return;
        PotionEffect current = entity.getPotionEffect(type);
        if (current != null && !owns(entity, current)) return;
        int duration = Math.max(1, ticks);
        PotionEffect effect = new PotionEffect(type, duration, 0, false, false, false);
        // Adding an effect dispatches EntityPotionEffectEvent. Capture live values before
        // that callback, which may move the entity out of this Folia region.
        UUID id = entity.getUniqueId();
        long until = (long) entity.getTicksLived() + duration;
        if (entity.addPotionEffect(effect, true)) {
            EXPIRIES.computeIfAbsent(id, ignored -> new ConcurrentHashMap<>()).put(type, until);
        }
    }

    public static void clear(LivingEntity entity, PotionEffectType type) {
        if (!org.bukkit.Bukkit.isOwnedByCurrentRegion(entity)) return;
        PotionEffect current = entity.getPotionEffect(type);
        boolean owned = current != null && owns(entity, current);
        Map<PotionEffectType, Long> effects = EXPIRIES.get(entity.getUniqueId());
        if (effects != null) {
            effects.remove(type);
            if (effects.isEmpty()) EXPIRIES.remove(entity.getUniqueId(), effects);
        }
        if (owned) entity.removePotionEffect(type);
    }

    public static void forget(UUID entityId) { EXPIRIES.remove(entityId); }

    private static boolean owns(LivingEntity entity, PotionEffect effect) {
        Map<PotionEffectType, Long> effects = EXPIRIES.get(entity.getUniqueId());
        Long until = effects == null ? null : effects.get(effect.getType());
        if (until == null || effect.getAmplifier() != 0 || effect.isAmbient()
                || effect.hasParticles() || effect.hasIcon()) return false;
        long remaining = until - entity.getTicksLived();
        return remaining >= -2L && Math.abs(effect.getDuration() - remaining) <= 2L;
    }
}
