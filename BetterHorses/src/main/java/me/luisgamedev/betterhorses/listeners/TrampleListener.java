package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.events.HorseTrampleEvent;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class TrampleListener implements Listener, ListenerLifecycle {

    private final BetterHorses plugin;
    private final Map<UUID, Location> previousHorseLocations = new HashMap<>();
    private final Map<UUID, Long> targetCooldowns = new HashMap<>();
    private long currentTick = 0L;
    private BukkitTask task;

    public TrampleListener(BetterHorses plugin) {
        this.plugin = plugin;
        startTrampleTask();
    }

    private void startTrampleTask() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tickTrample();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        previousHorseLocations.clear();
        targetCooldowns.clear();
        currentTick = 0L;
    }

    private void tickTrample() {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("trample.enabled", true)) {
            return;
        }

        currentTick++;
        cleanupCooldowns(currentTick);

        for (Player rider : Bukkit.getOnlinePlayers()) {
            if (!WorldAccessPolicy.isEnabled(rider.getWorld())) continue;
            if (!(rider.getVehicle() instanceof AbstractHorse horse)) {
                continue;
            }
            if (!rider.hasPermission(PermissionUtils.TRAMPLE)) {
                continue;
            }
            SupportedMountType mountType = SupportedMountType.fromEntity(horse).orElse(null);
            if (mountType == null || !mountType.isEnabled(config) || !isTrampleEnabled(config, mountType)) {
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

    private boolean isTrampleEnabled(FileConfiguration config, SupportedMountType mountType) {
        return config.getBoolean("trample.mount-types." + mountType.getConfigKey(), true);
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

            LivingEntity target = (LivingEntity) entity;
            applyTrample(rider, horse, horseLocation, target, config, currentTick);
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

    private void applyTrample(Player rider, AbstractHorse horse, Location horseLocation, LivingEntity target,
                              FileConfiguration config, long currentTick) {
        if (!canApply(rider, horse, target, config) || isOnCooldown(target, currentTick)) return;
        double damage = nonNegative(config.getDouble("trample.damage", 4.0));
        double strength = nonNegative(config.getDouble("trample.knockback", 1.2));
        Vector knockback = new Vector();
        if (strength > 0.0) {
            knockback = target.getLocation().toVector().subtract(horseLocation.toVector());
            if (knockback.lengthSquared() > 0.0) {
                knockback.normalize().multiply(strength);
                knockback.setY(Math.max(0.2, knockback.getY() + 0.2));
            }
        }

        HorseTrampleEvent event = new HorseTrampleEvent(rider, horse, target, damage, knockback,
                Math.max(0, config.getInt("trample.cooldown-ticks", 20)));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || !canApply(rider, horse, target, plugin.getConfig())) return;

        // Cancellation never consumes a hit cooldown or applies either part of the hit.
        targetCooldowns.put(target.getUniqueId(), currentTick + event.getCooldownTicks());
        if (event.getDamage() > 0.0) target.damage(event.getDamage(), rider);
        Vector velocity = event.getKnockback();
        if (target.isValid() && !target.isDead() && velocity.lengthSquared() > 0.0) target.setVelocity(velocity);
    }

    private boolean canApply(Player rider, AbstractHorse horse, LivingEntity target, FileConfiguration config) {
        SupportedMountType type = SupportedMountType.fromEntity(horse).orElse(null);
        return task != null && config.getBoolean("trample.enabled", true) && rider.isOnline() && !rider.isDead()
                && horse.isValid() && !horse.isDead() && rider.getVehicle() == horse
                && rider.hasPermission(PermissionUtils.TRAMPLE) && WorldAccessPolicy.isEnabled(horse.getWorld())
                && target.getWorld() == horse.getWorld() && type != null && type.isEnabled(config)
                && isTrampleEnabled(config, type) && isValidTarget(target, rider, horse, config)
                && isInHitArea(horse, target, config);
    }

    private boolean isInHitArea(AbstractHorse horse, LivingEntity target, FileConfiguration config) {
        double radius = nonNegative(config.getDouble("trample.radius", 1.2));
        if (radius <= 0.0 || !horse.getBoundingBox().expand(radius).overlaps(target.getBoundingBox())) return false;
        Location location = horse.getLocation();
        Vector forward = location.getDirection();
        if (forward.lengthSquared() == 0.0) return false;
        double angle = config.getDouble("trample.hit-angle-degrees", 90.0);
        if (!Double.isFinite(angle)) angle = 90.0;
        return isInsideHitCone(location, forward.normalize(), target, angle,
                Math.cos(Math.toRadians(Math.max(0.0, Math.min(360.0, angle)) / 2.0)));
    }

    private static double nonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
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
