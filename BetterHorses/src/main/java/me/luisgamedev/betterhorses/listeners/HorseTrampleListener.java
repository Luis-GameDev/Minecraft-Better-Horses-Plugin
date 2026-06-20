package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class HorseTrampleListener implements Listener {

    private static final double DEFAULT_DAMAGE = 2.0;
    private static final double DEFAULT_COOLDOWN_SECONDS = 1.0;
    private static final double DEFAULT_KNOCKBACK_STRENGTH = 0.35;
    private static final double DEFAULT_MINIMUM_TRAMPLE_SPEED = 0.17;
    private static final double DEFAULT_TRAMPLE_RADIUS_XZ = 1.15;
    private static final double DEFAULT_TRAMPLE_RADIUS_Y = 1.25;
    private static final double DEFAULT_FRONT_DOT_THRESHOLD = 0.0;

    private final BetterHorses plugin;
    private final Map<UUID, Map<UUID, Long>> trampleCooldowns = new HashMap<>();

    public HorseTrampleListener(BetterHorses plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player rider = event.getPlayer();
        if (!(rider.getVehicle() instanceof AbstractHorse mount)) {
            return;
        }

        FileConfiguration config = plugin.getConfig();
        SupportedMountType mountType = SupportedMountType.fromEntity(mount).orElse(null);
        if (mountType == null || !mountType.isEnabled(config) || !isTrampleEnabled(config, mountType)) {
            return;
        }

        double minimumSpeed = Math.max(
                0.0,
                config.getDouble("settings.horse-trample.minimum-speed", DEFAULT_MINIMUM_TRAMPLE_SPEED)
        );
        if (mount.getVelocity().clone().setY(0).lengthSquared() < minimumSpeed * minimumSpeed) {
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownMillis = Math.max(0L, Math.round(
                config.getDouble("settings.horse-trample.cooldown-seconds", DEFAULT_COOLDOWN_SECONDS) * 1000.0
        ));
        trample(mount, rider, config, now, cooldownMillis);
        cleanupCooldowns(now, cooldownMillis);
    }

    private boolean isTrampleEnabled(FileConfiguration config, SupportedMountType mountType) {
        return config.getBoolean("settings.horse-trample.mount-types." + mountType.getConfigKey() + ".enabled", false);
    }

    private void trample(AbstractHorse mount, Player rider, FileConfiguration config, long now, long cooldownMillis) {
        double damage = Math.max(0.0, config.getDouble("settings.horse-trample.damage", DEFAULT_DAMAGE));
        if (damage <= 0.0) {
            return;
        }

        double horizontalRadius = Math.max(
                0.0,
                config.getDouble("settings.horse-trample.hitbox.horizontal-radius", DEFAULT_TRAMPLE_RADIUS_XZ)
        );
        double verticalRadius = Math.max(
                0.0,
                config.getDouble("settings.horse-trample.hitbox.vertical-radius", DEFAULT_TRAMPLE_RADIUS_Y)
        );
        if (horizontalRadius <= 0.0 || verticalRadius <= 0.0) {
            return;
        }

        for (Entity nearby : mount.getNearbyEntities(horizontalRadius, verticalRadius, horizontalRadius)) {
            if (!(nearby instanceof LivingEntity target) || target.equals(mount) || mount.getPassengers().contains(target)) {
                continue;
            }
            if (!isInTrampleHitbox(mount, rider, target, horizontalRadius, verticalRadius, config)) {
                continue;
            }
            if (isOnCooldown(mount.getUniqueId(), target.getUniqueId(), now, cooldownMillis)) {
                continue;
            }

            EntityDamageByEntityEvent damageEvent = callTrampleDamageEvent(rider, target, damage);
            if (damageEvent.isCancelled()) {
                continue;
            }

            applyTrampleDamage(target, damageEvent);
            applyKnockback(config, target, rider);
            setCooldown(mount.getUniqueId(), target.getUniqueId(), now);
        }
    }

    private boolean isInTrampleHitbox(
            AbstractHorse mount,
            Player rider,
            LivingEntity target,
            double horizontalRadius,
            double verticalRadius,
            FileConfiguration config
    ) {
        Location mountLocation = mount.getLocation();
        Location targetLocation = target.getLocation();
        if (Math.abs(targetLocation.getY() - mountLocation.getY()) > verticalRadius) {
            return false;
        }

        Vector toTarget = targetLocation.toVector().subtract(mountLocation.toVector()).setY(0);
        if (toTarget.lengthSquared() > horizontalRadius * horizontalRadius) {
            return false;
        }
        if (toTarget.lengthSquared() == 0) {
            return true;
        }

        Vector direction = rider.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() == 0) {
            return false;
        }

        double frontDotThreshold = Math.max(
                -1.0,
                Math.min(
                        1.0,
                        config.getDouble("settings.horse-trample.hitbox.front-dot-threshold", DEFAULT_FRONT_DOT_THRESHOLD)
                )
        );
        return direction.normalize().dot(toTarget.normalize()) >= frontDotThreshold;
    }

    private boolean isOnCooldown(UUID mountId, UUID targetId, long now, long cooldownMillis) {
        Map<UUID, Long> targetCooldowns = trampleCooldowns.get(mountId);
        if (targetCooldowns == null) {
            return false;
        }
        long lastTrample = targetCooldowns.getOrDefault(targetId, Long.MIN_VALUE);
        return cooldownMillis > 0L && now - lastTrample < cooldownMillis;
    }

    private void setCooldown(UUID mountId, UUID targetId, long now) {
        trampleCooldowns.computeIfAbsent(mountId, ignored -> new HashMap<>()).put(targetId, now);
    }

    private EntityDamageByEntityEvent callTrampleDamageEvent(Player rider, LivingEntity target, double damage) {
        EntityDamageByEntityEvent damageEvent = new EntityDamageByEntityEvent(
                rider,
                target,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                damage
        );
        Bukkit.getPluginManager().callEvent(damageEvent);
        return damageEvent;
    }

    private void applyTrampleDamage(LivingEntity target, EntityDamageByEntityEvent damageEvent) {
        double finalDamage = Math.max(0.0, damageEvent.getFinalDamage());
        if (finalDamage <= 0.0) {
            return;
        }

        target.setLastDamageCause(damageEvent);
        target.setHealth(Math.max(0.0, target.getHealth() - finalDamage));
    }

    private void applyKnockback(FileConfiguration config, LivingEntity target, Player rider) {
        if (!config.getBoolean("settings.horse-trample.knockback.enabled", false)) {
            return;
        }

        Vector direction = rider.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() == 0) {
            return;
        }

        double strength = Math.max(
                0.0,
                config.getDouble("settings.horse-trample.knockback.strength", DEFAULT_KNOCKBACK_STRENGTH)
        );
        if (strength <= 0.0) {
            return;
        }

        Vector knockback = direction.normalize().multiply(strength).setY(0.15);
        target.setVelocity(target.getVelocity().add(knockback));
    }

    private void cleanupCooldowns(long now, long cooldownMillis) {
        long maxAge = Math.max(cooldownMillis, 1000L) * 2L;
        Iterator<Map.Entry<UUID, Map<UUID, Long>>> mountIterator = trampleCooldowns.entrySet().iterator();
        while (mountIterator.hasNext()) {
            Map<UUID, Long> targetCooldowns = mountIterator.next().getValue();
            targetCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > maxAge);
            if (targetCooldowns.isEmpty()) {
                mountIterator.remove();
            }
        }
    }
}
