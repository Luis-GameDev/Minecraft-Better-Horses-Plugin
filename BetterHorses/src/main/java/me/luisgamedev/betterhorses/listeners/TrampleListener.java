package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.events.HorseTrampleEvent;
import me.luisgamedev.betterhorses.scheduler.ScheduledTaskHandle;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Runs only while an eligible player is mounted; target changes execute on target schedulers. */
public final class TrampleListener implements Listener, ListenerLifecycle {
    private final BetterHorses plugin;
    private final ThreadLocal<DamageAttempt> pendingDamage = new ThreadLocal<>();
    private final ConcurrentMap<UUID, RiderState> riders = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> targetCooldowns = new ConcurrentHashMap<>();
    private volatile boolean stopped;
    private final AtomicLong lastCooldownCleanup = new AtomicLong();

    public TrampleListener(BetterHorses plugin) {
        this.plugin = plugin;
        onConfigurationReload();
    }

    @Override public void onConfigurationReload() {
        if (stopped || !plugin.isEnabled()) return;
        // Registration/reload need not produce a mount event for existing riders.
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.scheduler().entity(player, 1L, ignored -> {
                if (stopped || !Bukkit.isOwnedByCurrentRegion(player) || !player.isOnline() || player.isDead()) return;
                if (!(player.getVehicle() instanceof AbstractHorse horse) || !Bukkit.isOwnedByCurrentRegion(horse)
                        || !horse.isValid() || horse.isDead()) return;
                FileConfiguration config = plugin.getConfig();
                SupportedMountType type = SupportedMountType.fromEntity(horse).orElse(null);
                if (!config.getBoolean("trample.enabled", true) || !WorldAccessPolicy.isEnabled(horse.getWorld())
                        || !player.hasPermission(PermissionUtils.TRAMPLE) || type == null || !type.isEnabled(config)
                        || !config.getBoolean("trample.mount-types." + type.getConfigKey(), true)) return;
                RiderState current = riders.get(player.getUniqueId());
                if (current == null || !current.horse.equals(horse.getUniqueId())
                        || current.task == null || current.task.isDone()) start(player, horse);
            }, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player && event.getMount() instanceof AbstractHorse horse) start(player, horse);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) stop(player.getUniqueId());
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { stop(event.getPlayer().getUniqueId()); }

    private void start(Player rider, AbstractHorse horse) {
        stop(rider.getUniqueId());
        UUID id = rider.getUniqueId();
        RiderState state = new RiderState(horse.getUniqueId(), horse.getLocation(), null);
        riders.put(id, state);
        ScheduledTaskHandle task = plugin.scheduler().entityRepeating(horse, 1L, 1L,
                scheduled -> tick(rider, horse), () -> riders.remove(id));
        riders.computeIfPresent(id, (ignored, current) -> current.withTask(task));
    }

    private void stop(UUID rider) {
        RiderState state = riders.remove(rider);
        if (state != null && state.task != null) state.task.cancel();
    }

    private void tick(Player rider, AbstractHorse horse) {
        cleanupCooldowns(System.currentTimeMillis());
        if (stopped || !Bukkit.isOwnedByCurrentRegion(horse) || !Bukkit.isOwnedByCurrentRegion(rider)) {
            stop(rider.getUniqueId());
            return;
        }
        RiderState state = riders.get(rider.getUniqueId());
        if (state == null || rider.getVehicle() != horse || !horse.isValid()) { stop(rider.getUniqueId()); return; }
        FileConfiguration config = plugin.getConfig();
        SupportedMountType type = SupportedMountType.fromEntity(horse).orElse(null);
        if (!config.getBoolean("trample.enabled", true) || !WorldAccessPolicy.isEnabled(horse.getWorld())
                || !rider.hasPermission(PermissionUtils.TRAMPLE) || type == null || !type.isEnabled(config)
                || !config.getBoolean("trample.mount-types." + type.getConfigKey(), true)) return;
        Location now = horse.getLocation();
        Location before = state.previous;
        riders.computeIfPresent(rider.getUniqueId(), (ignored, current) -> current.withPrevious(now));
        if (before.getWorld() != now.getWorld()) return;
        double speed = now.distance(before);
        if (speed < config.getDouble("trample.min-speed", .15)) return;
        double radius = Math.max(0, config.getDouble("trample.radius", 1.2));
        Vector forward = now.getDirection().setY(0);
        if (radius == 0 || forward.lengthSquared() == 0) return;
        forward.normalize();
        for (Entity candidate : horse.getNearbyEntities(radius, radius, radius)) {
            if (!(candidate instanceof LivingEntity target) || candidate == rider || candidate == horse
                    || candidate instanceof ArmorStand || candidate instanceof Vehicle) continue;
            boolean permitted = target instanceof Player ? config.getBoolean("trample.affect-players", false)
                    : config.getBoolean("trample.affect-mobs", true);
            if (!permitted) continue;
            long nowMillis = System.currentTimeMillis();
            Long cooldownUntil = targetCooldowns.get(target.getUniqueId());
            if (cooldownUntil != null && cooldownUntil > nowMillis) continue;
            int cooldownTicks = Math.max(0, config.getInt("trample.cooldown-ticks", 20));
            Location origin = now.clone();
            double damage = nonNegative(config.getDouble("trample.damage", 4));
            double knockback = nonNegative(config.getDouble("trample.knockback", 1.2));
            double minDot = Math.cos(Math.toRadians(Math.min(360, Math.max(0,
                    config.getDouble("trample.hit-angle-degrees", 90))) / 2));
            plugin.scheduler().entity(target, 1L,
                    ignored -> apply(rider, horse, target, origin, forward.clone(), minDot, damage, knockback, cooldownTicks), null);
        }
    }

    private void apply(Player rider, AbstractHorse horse, LivingEntity target, Location origin, Vector forward, double minDot,
                       double damage, double knockback, int cooldownTicks) {
        if (!canApply(rider, horse, target) || target.getWorld() != origin.getWorld()) return;
        long now = System.currentTimeMillis();
        Long cooldownUntil = targetCooldowns.get(target.getUniqueId());
        if (cooldownUntil != null && cooldownUntil > now) return;
        Vector delta = target.getLocation().toVector().subtract(origin.toVector());
        if (delta.lengthSquared() > 0 && forward.dot(delta.clone().normalize()) < minDot) return;
        Vector velocity = new Vector();
        if (knockback > 0 && delta.lengthSquared() > 0) {
            velocity = delta.normalize().multiply(knockback).setY(.2);
        }

        HorseTrampleEvent event = new HorseTrampleEvent(rider, horse, target, damage, velocity, cooldownTicks);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || !canApply(rider, horse, target)) return;

        if (!acceptedNativeDamage(rider, target, event.getDamage()) || !canApply(rider, horse, target)) return;
        // Target callbacks run serially on its owning region. Only accepted hits consume cooldown.
        targetCooldowns.put(target.getUniqueId(), System.currentTimeMillis() + event.getCooldownTicks() * 50L);
        velocity = event.getKnockback();
        if (Bukkit.isOwnedByCurrentRegion(target) && target.isValid() && !target.isDead()
                && velocity.lengthSquared() > 0.0) target.setVelocity(velocity);
    }

    /** Observe the real native event, including cancellations, without dispatching a second hit. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onNativeDamage(EntityDamageByEntityEvent event) {
        DamageAttempt attempt = pendingDamage.get();
        if (attempt != null && event.getEntity() == attempt.target && event.getDamager() == attempt.rider) {
            attempt.events.add(event);
        }
    }

    private boolean acceptedNativeDamage(Player rider, LivingEntity target, double damage) {
        DamageAttempt previous = pendingDamage.get();
        DamageAttempt attempt = new DamageAttempt(rider, target);
        pendingDamage.set(attempt);
        try {
            // Zero-damage trample still asks native protection before applying its custom push.
            target.damage(damage, rider);
        } finally {
            if (previous == null) pendingDamage.remove();
            else pendingDamage.set(previous);
        }
        // Keep event references: even a later MONITOR decision is visible after damage returns.
        // Reentrant damage must not let an accepted nested hit conceal a cancelled outer hit.
        return !attempt.events.isEmpty() && attempt.events.stream().noneMatch(EntityDamageByEntityEvent::isCancelled);
    }

    private static final class DamageAttempt {
        final Player rider;
        final LivingEntity target;
        final List<EntityDamageByEntityEvent> events = new ArrayList<>();

        DamageAttempt(Player rider, LivingEntity target) {
            this.rider = rider;
            this.target = target;
        }
    }

    private boolean canApply(Player rider, AbstractHorse horse, LivingEntity target) {
        // Event handlers may inspect all three live entities, so require shared ownership before dispatch.
        if (stopped || !Bukkit.isOwnedByCurrentRegion(target) || !Bukkit.isOwnedByCurrentRegion(horse)
                || !Bukkit.isOwnedByCurrentRegion(rider)) return false;
        FileConfiguration config = plugin.getConfig();
        SupportedMountType type = SupportedMountType.fromEntity(horse).orElse(null);
        return config.getBoolean("trample.enabled", true) && rider.isOnline() && !rider.isDead()
                && rider.getVehicle() == horse && horse.isValid() && !horse.isDead()
                && target.isValid() && !target.isDead() && !target.isInvulnerable()
                && target != rider && target != horse && !(target instanceof ArmorStand) && !(target instanceof Vehicle)
                && target.getWorld() == horse.getWorld() && WorldAccessPolicy.isEnabled(horse.getWorld())
                && rider.hasPermission(PermissionUtils.TRAMPLE) && type != null && type.isEnabled(config)
                && config.getBoolean("trample.mount-types." + type.getConfigKey(), true)
                && (target instanceof Player ? config.getBoolean("trample.affect-players", false)
                : config.getBoolean("trample.affect-mobs", true)) && isInHitArea(horse, target, config);
    }

    private boolean isInHitArea(AbstractHorse horse, LivingEntity target, FileConfiguration config) {
        double radius = nonNegative(config.getDouble("trample.radius", 1.2));
        if (radius <= 0.0 || !horse.getBoundingBox().expand(radius).overlaps(target.getBoundingBox())) return false;
        double angle = config.getDouble("trample.hit-angle-degrees", 90.0);
        if (!Double.isFinite(angle)) angle = 90.0;
        if (angle >= 360.0) return true;
        Location origin = horse.getLocation();
        Vector forward = origin.getDirection().setY(0.0);
        if (forward.lengthSquared() == 0.0) return false;
        Vector delta = target.getLocation().toVector().subtract(origin.toVector());
        return delta.lengthSquared() == 0.0 || forward.normalize().dot(delta.normalize())
                >= Math.cos(Math.toRadians(Math.min(360.0, Math.max(0.0, angle))) / 2.0);
    }

    private void cleanupCooldowns(long now) {
        long previous = lastCooldownCleanup.get();
        if (now >= previous && now - previous < 1000L) return;
        if (!lastCooldownCleanup.compareAndSet(previous, now)) return;
        targetCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private static double nonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    @Override public void shutdown() { stopped = true; riders.keySet().forEach(this::stop); targetCooldowns.clear(); }

    private record RiderState(UUID horse, Location previous, ScheduledTaskHandle task) {
        RiderState withTask(ScheduledTaskHandle value) { return new RiderState(horse, previous, value); }
        RiderState withPrevious(Location value) { return new RiderState(horse, value, task); }
    }
}
