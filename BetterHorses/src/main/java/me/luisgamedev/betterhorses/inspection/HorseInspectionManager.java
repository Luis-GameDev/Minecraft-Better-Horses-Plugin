package me.luisgamedev.betterhorses.inspection;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.listeners.ListenerLifecycle;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

/** Owns private, temporary horse inspection displays on the normal Paper server thread. */
public final class HorseInspectionManager implements Listener, ListenerLifecycle {
    private static final NamespacedKey MARKER = new NamespacedKey("betterhorses", "inspection_display");
    private static final LegacyComponentSerializer TEXT_SERIALIZER = LegacyComponentSerializer.builder()
            .character('\u00a7').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private final Plugin plugin;
    private final Supplier<FileConfiguration> configuration;
    private final Map<UUID, Inspection> inspections = new HashMap<>();
    private final Set<TextDisplay> pendingRemovals = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Listener removalListener = new Listener() { };
    private BukkitTask monitor;
    private boolean stopped;

    public HorseInspectionManager(BetterHorses plugin) {
        this(plugin, plugin::getConfig);
    }

    HorseInspectionManager(Plugin plugin, Supplier<FileConfiguration> configuration) {
        this.plugin = Objects.requireNonNull(plugin);
        this.configuration = Objects.requireNonNull(configuration);
    }

    /** The owning plugin registers this listener; startup also removes leftovers from older sessions. */
    public void start() {
        stopped = false;
        HandlerList.unregisterAll(removalListener);
        registerRemovalEvent();
        cleanLoadedDisplays();
        updateMonitor();
    }

    public static boolean isInspectionDisplay(Entity entity) {
        return entity instanceof TextDisplay
                && entity.getPersistentDataContainer().has(MARKER, PersistentDataType.BYTE);
    }

    /** Replace this viewer's previous peek. All spawn-time privacy settings precede world insertion. */
    public boolean show(Player viewer, AbstractHorse horse, Component text) {
        if (stopped || viewer == null || horse == null || text == null) return false;
        FileConfiguration config = configuration.get();
        if (!config.getBoolean("horse-inspection.enabled", true) || !viewer.isOnline() || viewer.isDead()
                || !horse.isValid() || horse.isDead() || horse.isLoveMode() || !viewer.getWorld().equals(horse.getWorld())
                || !WorldAccessPolicy.isEnabled(horse.getWorld())) return false;
        double range = finiteRange(config.getDouble("horse-inspection.view-range", 16.0D), 16.0D, 1.0D, 64.0D);
        if (viewer.getLocation().distanceSquared(horse.getLocation()) > range * range) return false;
        double height = finiteRange(config.getDouble("horse-inspection.height-offset", 0.5D), 0.5D, 0.0D, 8.0D);
        double seconds = finiteRange(config.getDouble("horse-inspection.duration-seconds", 10.0D), 10.0D, 0.05D, 3600.0D);
        long durationTicks = Math.max(1L, (long) Math.ceil(seconds * 20.0D));
        hide(viewer);
        TextDisplay[] pending = new TextDisplay[1];
        Inspection inspection = null;
        try {
            TextDisplay display = horse.getWorld().spawn(horse.getLocation(), TextDisplay.class, candidate -> {
                pending[0] = candidate;
                candidate.setPersistent(false);
                candidate.setVisibleByDefault(false);
                candidate.getPersistentDataContainer().set(MARKER, PersistentDataType.BYTE, (byte) 1);
                candidate.setGravity(false);
                candidate.setSilent(true);
                // String API avoids calling server Adventure signatures after the plugin shades Adventure.
                candidate.setText(TEXT_SERIALIZER.serialize(text));
                candidate.setBillboard(Display.Billboard.CENTER);
                candidate.setAlignment(TextDisplay.TextAlignment.CENTER);
                candidate.setShadowed(true);
                candidate.setSeeThrough(false);
                candidate.setDefaultBackground(false);
                candidate.setBackgroundColor(Color.fromARGB(180, 18, 23, 31));
                candidate.setTextOpacity((byte) -1);
                candidate.setLineWidth(260);
                // Minecraft's display range is a multiplier of its 64-block base range.
                candidate.setViewRange((float) (range / 64.0D));
                candidate.setShadowRadius(0.0F);
                candidate.setShadowStrength(0.0F);
                candidate.setTransformation(new Transformation(new Vector3f(0.0F, (float) height, 0.0F),
                        new Quaternionf(), new Vector3f(0.65F), new Quaternionf()));
            });
            if (display == null || !display.isValid() || !horse.addPassenger(display)) {
                removeDisplay(pending[0]);
                updateMonitor();
                return false;
            }
            inspection = new Inspection(viewer, horse, display, range * range);
            inspections.put(viewer.getUniqueId(), inspection);
            Inspection expected = inspection;
            inspection.expiration = Bukkit.getScheduler().runTaskLater(plugin, () -> remove(expected), durationTicks);
            ensureMonitor();
            if (!isValid(inspection)) {
                remove(inspection);
                return false;
            }
            viewer.showEntity(plugin, display);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (inspection != null) remove(inspection);
            else {
                removeDisplay(pending[0]);
                updateMonitor();
            }
            plugin.getLogger().log(Level.FINE, "Could not show temporary horse inspection", failure);
            return false;
        }
    }

    public void hide(Player viewer) {
        if (viewer != null) remove(inspections.get(viewer.getUniqueId()));
    }

    public void hideMount(AbstractHorse horse) {
        if (horse == null) return;
        removeRelated(horse);
        for (Entity passenger : new ArrayList<>(horse.getPassengers())) {
            if (isInspectionDisplay(passenger)) removeDisplay((TextDisplay) passenger);
        }
        updateMonitor();
    }

    private void ensureMonitor() {
        if (monitor == null) monitor = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Inspection inspection : new ArrayList<>(inspections.values())) {
                try {
                    if (!isValid(inspection)) remove(inspection);
                } catch (RuntimeException | LinkageError failure) {
                    remove(inspection);
                }
            }
            retryRemovals();
            updateMonitor();
        }, 10L, 10L);
    }

    private void updateMonitor() {
        if (stopped || (inspections.isEmpty() && pendingRemovals.isEmpty())) {
            cancel(monitor);
            monitor = null;
        } else {
            try {
                ensureMonitor();
            } catch (RuntimeException | LinkageError failure) {
                plugin.getLogger().log(Level.FINE, "Could not schedule inspection cleanup retry", failure);
            }
        }
    }

    private boolean isValid(Inspection inspection) {
        Player viewer = inspection.viewer;
        AbstractHorse horse = inspection.horse;
        return viewer.isOnline() && !viewer.isDead() && horse.isValid() && !horse.isDead() && !horse.isLoveMode()
                && inspection.display.isValid() && horse.getUniqueId().equals(vehicleId(inspection.display))
                && viewer.getWorld().equals(horse.getWorld()) && WorldAccessPolicy.isEnabled(horse.getWorld())
                && configuration.get().getBoolean("horse-inspection.enabled", true)
                && viewer.getLocation().distanceSquared(horse.getLocation()) <= inspection.rangeSquared;
    }

    private static UUID vehicleId(Entity entity) {
        Entity vehicle = entity.getVehicle();
        return vehicle == null ? null : vehicle.getUniqueId();
    }

    private void remove(Inspection expected) {
        if (expected == null || !inspections.remove(expected.viewerId, expected)) return;
        cancel(expected.expiration);
        try {
            expected.viewer.hideEntity(plugin, expected.display);
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().log(Level.FINE, "Could not hide expired inspection display", failure);
        }
        removeDisplay(expected.display);
        updateMonitor();
    }

    private void cancel(BukkitTask task) {
        if (task == null) return;
        try {
            task.cancel();
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().log(Level.FINE, "Could not cancel inspection cleanup task", failure);
            try {
                Bukkit.getScheduler().cancelTask(task.getTaskId());
            } catch (RuntimeException | LinkageError fallbackFailure) {
                plugin.getLogger().log(Level.FINE, "Inspection task cancellation fallback failed", fallbackFailure);
            }
        }
    }

    private void removeDisplay(TextDisplay display) {
        if (display == null) return;
        try {
            display.remove();
            pendingRemovals.remove(display);
        } catch (RuntimeException | LinkageError failure) {
            pendingRemovals.add(display);
            plugin.getLogger().log(Level.FINE, "Could not remove inspection display; cleanup will retry", failure);
        }
    }

    private void retryRemovals() {
        for (TextDisplay display : new ArrayList<>(pendingRemovals)) removeDisplay(display);
    }

    private void removeRelated(Entity entity) {
        UUID id = entity.getUniqueId();
        for (Inspection inspection : new ArrayList<>(inspections.values())) {
            if (inspection.horseId.equals(id) || inspection.displayId.equals(id)) remove(inspection);
        }
    }

    private void clear() {
        for (Inspection inspection : new ArrayList<>(inspections.values())) remove(inspection);
    }

    @Override public void onConfigurationReload() {
        clear();
        retryRemovals();
        cleanLoadedDisplays();
        updateMonitor();
    }

    @Override public void shutdown() {
        stopped = true;
        clear();
        cancel(monitor);
        monitor = null;
        HandlerList.unregisterAll(removalListener);
        cleanLoadedDisplays();
        retryRemovals();
    }

    private void cleanLoadedDisplays() {
        for (World world : Bukkit.getWorlds()) {
            try {
                for (TextDisplay display : new ArrayList<>(world.getEntitiesByClass(TextDisplay.class))) cleanStale(display);
            } catch (RuntimeException | LinkageError failure) {
                plugin.getLogger().log(Level.FINE, "Could not scan a world for stale inspection displays", failure);
            }
        }
    }

    private void cleanStale(Entity entity) {
        if (!isInspectionDisplay(entity)) return;
        boolean active = inspections.values().stream().anyMatch(inspection -> inspection.displayId.equals(entity.getUniqueId()));
        if (!active) removeDisplay((TextDisplay) entity);
    }

    @SuppressWarnings("unchecked")
    private void registerRemovalEvent() {
        for (String prefix : new String[]{"io.papermc.paper.event.entity.", "com.destroystokyo.paper.event.entity."}) {
            try {
                Class<?> candidate = Class.forName(prefix + "EntityRemoveFromWorldEvent", false, plugin.getClass().getClassLoader());
                if (!Event.class.isAssignableFrom(candidate)) continue;
                Class<? extends Event> type = (Class<? extends Event>) candidate;
                plugin.getServer().getPluginManager().registerEvent(type, removalListener, EventPriority.MONITOR,
                        (ignored, event) -> {
                            if (event instanceof EntityEvent removal) removeRelated(removal.getEntity());
                        }, plugin, true);
                return;
            } catch (ClassNotFoundException ignored) {
                // Supported Paper releases use either package name.
            } catch (RuntimeException | LinkageError failure) {
                plugin.getLogger().log(Level.FINE, "Could not register optional inspection entity removal cleanup", failure);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR) public void onQuit(PlayerQuitEvent event) { hide(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onKick(PlayerKickEvent event) { hide(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void onWorldChange(PlayerChangedWorldEvent event) { hide(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void onPlayerDeath(PlayerDeathEvent event) { hide(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR) public void onDeath(EntityDeathEvent event) { removeRelated(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onLoveMode(EntityEnterLoveModeEvent event) { removeRelated(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onTeleport(EntityTeleportEvent event) { removeRelated(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player && event.getMount() instanceof AbstractHorse horse) hideMount(horse);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onDismount(EntityDismountEvent event) {
        if (isInspectionDisplay(event.getEntity())) removeRelated(event.getEntity());
    }
    @EventHandler(priority = EventPriority.MONITOR) public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) cleanStale(entity);
        updateMonitor();
    }
    @EventHandler(priority = EventPriority.MONITOR) public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) removeRelated(entity);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        for (Inspection inspection : new ArrayList<>(inspections.values())) {
            Location location = inspection.horse.getLocation();
            if (chunk.getWorld().equals(location.getWorld()) && chunk.getX() == (location.getBlockX() >> 4)
                    && chunk.getZ() == (location.getBlockZ() >> 4)) remove(inspection);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onWorldUnload(WorldUnloadEvent event) {
        for (Inspection inspection : new ArrayList<>(inspections.values())) {
            if (event.getWorld().equals(inspection.horse.getWorld())) remove(inspection);
        }
    }

    private static double finiteRange(double value, double fallback, double minimum, double maximum) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Inspection {
        private final Player viewer;
        private final AbstractHorse horse;
        private final TextDisplay display;
        private final UUID viewerId;
        private final UUID horseId;
        private final UUID displayId;
        private final double rangeSquared;
        private BukkitTask expiration;

        private Inspection(Player viewer, AbstractHorse horse, TextDisplay display, double rangeSquared) {
            this.viewer = viewer;
            this.horse = horse;
            this.display = display;
            this.viewerId = viewer.getUniqueId();
            this.horseId = horse.getUniqueId();
            this.displayId = display.getUniqueId();
            this.rangeSquared = rangeSquared;
        }
    }
}
