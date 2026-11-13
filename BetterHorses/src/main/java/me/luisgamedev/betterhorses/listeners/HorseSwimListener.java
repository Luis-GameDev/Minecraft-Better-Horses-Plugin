package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Temporary buoyancy for ridden mounts; never changes attributes or saved horse data. */
public final class HorseSwimListener implements Listener, ListenerLifecycle {
    private final BetterHorses plugin;
    private final Map<UUID, SwimState> horses = new ConcurrentHashMap<>();
    private volatile boolean stopped;

    public HorseSwimListener(BetterHorses plugin) {
        this.plugin = plugin;
        onConfigurationReload();
    }

    @Override public void onConfigurationReload() {
        if (stopped || !plugin.isEnabled()) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getVehicle() instanceof AbstractHorse horse) start(horse);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player && event.getMount() instanceof AbstractHorse horse) start(horse);
    }

    private boolean eligible(AbstractHorse horse) {
        return !stopped && plugin.isEnabled() && plugin.getConfig().getBoolean("settings.horse-can-swim", false)
                && horse.isValid() && !horse.isDead() && WorldAccessPolicy.isEnabled(horse.getWorld())
                && SupportedMountType.fromEntity(horse).filter(type -> type.isEnabled(plugin.getConfig())).isPresent();
    }

    private void start(AbstractHorse horse) {
        if (!eligible(horse)) return;
        UUID id = horse.getUniqueId();
        SwimState state = new SwimState(horse.getLocation());
        if (horses.putIfAbsent(id, state) != null) return;
        state.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(horse, state), 1L, 1L);
        if (stopped) stop(id, state);
    }

    private void tick(AbstractHorse horse, SwimState state) {
        UUID id = horse.getUniqueId();
        if (horses.get(id) != state || !eligible(horse)) { stop(id, state); return; }
        // Only the controlling passenger supplies movement; a camel's second rider shares the same task.
        Entity controller = horse.getPassengers().stream().findFirst().orElse(null);
        if (!(controller instanceof Player rider) || !rider.isOnline() || rider.isDead() || rider.getVehicle() != horse) {
            stop(id, state);
            return;
        }
        Location now = horse.getLocation();
        Location previous = state.previous;
        state.previous = now;
        // Do not turn a teleport or a world change into a movement impulse.
        if (previous.getWorld() != now.getWorld() || now.distanceSquared(previous) > 64.0 || !horse.isInWater()) return;
        Vector movement = now.toVector().subtract(previous.toVector());
        if (!Double.isFinite(movement.getX()) || !Double.isFinite(movement.getY()) || !Double.isFinite(movement.getZ())) return;
        double immersion = Math.min(0.75, horse.getHeight() * 0.5);
        double error = surfaceError(now, immersion);
        if (Double.isFinite(error)) horse.setVelocity(HorseSwimPhysics.velocity(movement, error));
    }

    /** Sample only the horse's column; never loads or reads an adjacent chunk on Folia. */
    private static double surfaceError(Location location, double immersion) {
        World world = location.getWorld();
        int bottom = Math.max(world.getMinHeight(), location.getBlockY());
        int top = Math.min(world.getMaxHeight() - 1, (int) Math.floor(location.getY() + immersion + 1.0));
        double surface = Double.NaN;
        for (int y = bottom; y <= top; y++) {
            double height = waterHeight(world.getBlockAt(location.getBlockX(), y, location.getBlockZ()));
            if (height > 0.0) surface = y + height;
        }
        return surface - location.getY() - immersion;
    }

    private static double waterHeight(Block block) {
        Material material = block.getType();
        if (material == Material.WATER) {
            if (block.getBlockData() instanceof Levelled levelled && levelled.getLevel() < 8) {
                return (8.0 - levelled.getLevel()) / 9.0;
            }
            return 1.0;
        }
        if (material == Material.BUBBLE_COLUMN || material == Material.KELP || material == Material.KELP_PLANT
                || material == Material.SEAGRASS || material == Material.TALL_SEAGRASS) return 1.0;
        return block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged() ? 1.0 : 0.0;
    }

    private void stop(UUID id, SwimState state) {
        horses.remove(id, state);
        if (state.task != null) state.task.cancel();
    }

    @Override public void shutdown() {
        stopped = true;
        horses.forEach(this::stop);
    }

    private static final class SwimState {
        Location previous;
        volatile BukkitTask task;
        SwimState(Location previous) { this.previous = previous; }
    }
}
