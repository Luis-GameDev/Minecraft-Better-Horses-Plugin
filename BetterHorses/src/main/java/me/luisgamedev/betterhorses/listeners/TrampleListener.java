package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class TrampleListener implements Listener {

    private final BetterHorses plugin;
    private final Map<UUID, Location> previousHorseLocations = new HashMap<>();
    private final Map<UUID, Long> targetCooldowns = new HashMap<>();
    private long currentTick = 0L;

    public TrampleListener(BetterHorses plugin) {
        this.plugin = plugin;
        startTrampleTask();
    }

    private void startTrampleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                tickTrample();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void tickTrample() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("trample.enabled", true)) {
            return;
        }

        currentTick++;
        cleanupCooldowns(currentTick);

        for (Player rider : Bukkit.getOnlinePlayers()) {
            if (!(rider.getVehicle() instanceof AbstractHorse horse)) {
                continue;
            }
            if (!SupportedMountType.isSupported(horse)) {
                continue;
            }

            Location currentLocation = horse.getLocation();
            Location previousLocation = previousHorseLocations.put(horse.getUniqueId(), currentLocation.clone());
            if (previousLocation == null || previousLocation.getWorld() != currentLocation.getWorld()) {
                continue;
            }

            double currentSpeed = currentLocation.distance(previousLocation);
            if (currentSpeed <= 0.0) {
                continue;
            }

            double minSpeed = config.getDouble("trample.min-speed", 0.15);
            if (minSpeed > 0.0 && currentSpeed < minSpeed) {
                continue;
            }

            trampleNearbyTargets(rider, horse, currentLocation, config, currentTick);
        }
    }

    private void trampleNearbyTargets(Player rider, AbstractHorse horse, Location horseLocation, FileConfiguration config, long currentTick) {
        double radius = Math.max(0.0, config.getDouble("trample.radius", 1.2));
        if (radius <= 0.0) {
            return;
        }

        double hitAngleDegrees = config.getDouble("trample.hit-angle-degrees", 90.0);
        double minimumDot = Math.cos(Math.toRadians(Math.max(0.0, Math.min(360.0, hitAngleDegrees)) / 2.0));
        Vector horseForward = horseLocation.getDirection();
        if (horseForward.lengthSquared() == 0.0) {
            return;
        }
        horseForward.normalize();

        for (Entity entity : horse.getNearbyEntities(radius, radius, radius)) {
            if (!isValidTarget(entity, rider, horse, config)) {
                continue;
            }
            if (isOnCooldown(entity, currentTick)) {
                continue;
            }
            if (!isInsideHitCone(horseLocation, horseForward, entity, hitAngleDegrees, minimumDot)) {
                continue;
            }

            applyTrample(rider, horseLocation, (LivingEntity) entity, config, currentTick);
        }
    }

    private boolean isValidTarget(Entity entity, Player rider, AbstractHorse horse, FileConfiguration config) {
        if (entity == rider || entity == horse) return false;
        if (!(entity instanceof LivingEntity livingEntity)) return false;
        if (entity instanceof ArmorStand) return false;
        if (entity instanceof Item) return false;
        if (entity instanceof ExperienceOrb) return false;
        if (entity instanceof Projectile) return false;
        if (entity instanceof Vehicle) return false;
        if (livingEntity.isDead() || !livingEntity.isValid() || livingEntity.isInvulnerable()) return false;

        if (livingEntity instanceof Player) {
            return config.getBoolean("trample.affect-players", false);
        }
        return config.getBoolean("trample.affect-mobs", true);
    }

    private boolean isOnCooldown(Entity entity, long currentTick) {
        Long cooldownUntil = targetCooldowns.get(entity.getUniqueId());
        return cooldownUntil != null && cooldownUntil > currentTick;
    }

    private boolean isInsideHitCone(Location horseLocation, Vector horseForward, Entity target, double hitAngleDegrees, double minimumDot) {
        if (hitAngleDegrees >= 360.0) {
            return true;
        }

        Vector toTarget = target.getLocation().toVector().subtract(horseLocation.toVector());
        if (toTarget.lengthSquared() == 0.0) {
            return true;
        }

        double dot = horseForward.dot(toTarget.normalize());
        return dot >= minimumDot;
    }

    private void applyTrample(Player rider, Location horseLocation, LivingEntity target, FileConfiguration config, long currentTick) {
        double damage = Math.max(0.0, config.getDouble("trample.damage", 4.0));
        double knockbackStrength = Math.max(0.0, config.getDouble("trample.knockback", 1.2));

        EntityDamageByEntityEvent damageEvent = new EntityDamageByEntityEvent(
                rider,
                target,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                damage
        );
        Bukkit.getPluginManager().callEvent(damageEvent);
        if (damageEvent.isCancelled()) {
            return;
        }

        int cooldownTicks = Math.max(0, config.getInt("trample.cooldown-ticks", 20));
        targetCooldowns.put(target.getUniqueId(), currentTick + cooldownTicks);

        if (damageEvent.getDamage() > 0.0) {
            target.damage(damageEvent.getDamage(), rider);
        }

        if (knockbackStrength > 0.0) {
            Vector knockback = target.getLocation().toVector().subtract(horseLocation.toVector());
            if (knockback.lengthSquared() > 0.0) {
                knockback.normalize().multiply(knockbackStrength);
                knockback.setY(Math.max(0.2, knockback.getY() + 0.2));
                target.setVelocity(knockback);
            }
        }
    }

    private void cleanupCooldowns(long currentTick) {
        Iterator<Map.Entry<UUID, Long>> iterator = targetCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= currentTick) {
                iterator.remove();
            }
        }
    }
}
