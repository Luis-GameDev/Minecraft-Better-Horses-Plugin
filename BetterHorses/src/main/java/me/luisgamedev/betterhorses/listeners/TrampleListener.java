package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.scheduler.ScheduledTaskHandle;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs only while an eligible player is mounted; target changes execute on target schedulers. */
public final class TrampleListener implements Listener, ListenerLifecycle {
    private final BetterHorses plugin;
    private final ConcurrentMap<UUID, RiderState> riders = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> targetCooldowns = new ConcurrentHashMap<>();

    public TrampleListener(BetterHorses plugin) { this.plugin = plugin; }

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
                    || candidate instanceof ArmorStand || candidate instanceof Vehicle || target.isDead()) continue;
            boolean permitted = target instanceof Player ? config.getBoolean("trample.affect-players", false)
                    : config.getBoolean("trample.affect-mobs", true);
            if (!permitted) continue;
            long nowMillis = System.currentTimeMillis();
            long cooldown = Math.max(0, config.getInt("trample.cooldown-ticks", 20)) * 50L;
            AtomicBoolean claimed = new AtomicBoolean();
            targetCooldowns.compute(target.getUniqueId(), (key, until) -> {
                if (until != null && until > nowMillis) return until;
                claimed.set(true);
                return nowMillis + cooldown;
            });
            if (!claimed.get()) continue;
            Location origin = now.clone();
            double damage = Math.max(0, config.getDouble("trample.damage", 4));
            double knockback = Math.max(0, config.getDouble("trample.knockback", 1.2));
            double minDot = Math.cos(Math.toRadians(Math.min(360, Math.max(0,
                    config.getDouble("trample.hit-angle-degrees", 90))) / 2));
            plugin.scheduler().entity(target, 1L, ignored -> apply(rider, target, origin, forward, minDot, damage, knockback),
                    () -> targetCooldowns.remove(target.getUniqueId()));
        }
    }

    private void apply(Player rider, LivingEntity target, Location origin, Vector forward, double minDot,
                       double damage, double knockback) {
        if (!target.isValid() || target.getWorld() != origin.getWorld()) return;
        Vector delta = target.getLocation().toVector().subtract(origin.toVector());
        if (delta.lengthSquared() > 0 && forward.dot(delta.clone().normalize()) < minDot) return;
        if (damage > 0 && rider.isOnline()) target.damage(damage, rider);
        if (knockback > 0 && delta.lengthSquared() > 0) {
            delta.normalize().multiply(knockback).setY(.2);
            target.setVelocity(delta);
        }
    }

    @Override public void shutdown() { riders.keySet().forEach(this::stop); targetCooldowns.clear(); }

    private record RiderState(UUID horse, Location previous, ScheduledTaskHandle task) {
        RiderState withTask(ScheduledTaskHandle value) { return new RiderState(horse, previous, value); }
        RiderState withPrevious(Location value) { return new RiderState(horse, value, task); }
    }
}
