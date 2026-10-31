package me.luisgamedev.betterhorses.inspection;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.listeners.ListenerLifecycle;
import me.luisgamedev.betterhorses.scheduler.BetterHorsesScheduler;
import me.luisgamedev.betterhorses.scheduler.ScheduledTaskHandle;
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
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;

/** Temporary private displays: every entity read and mutation stays on its owning region. */
public final class HorseInspectionManager implements Listener, ListenerLifecycle {
    private static final NamespacedKey MARKER = new NamespacedKey("betterhorses", "inspection_display");
    private static final LegacyComponentSerializer TEXT_SERIALIZER = LegacyComponentSerializer.builder()
            .character('\u00a7').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private final Plugin plugin;
    private final Supplier<FileConfiguration> configuration;
    private final Runtime runtime;
    private final Map<UUID, Inspection> inspections = new ConcurrentHashMap<>();
    private final Map<UUID, Removal> pendingRemovals = new ConcurrentHashMap<>();
    private final Set<TaskSlot> background = ConcurrentHashMap.newKeySet();
    private final Listener removalListener = new Listener() { };
    private final AtomicLong generation = new AtomicLong();
    private volatile boolean stopped;

    public HorseInspectionManager(BetterHorses plugin) {
        this(plugin, plugin::getConfig, new FoliaRuntime(plugin.scheduler()));
    }

    HorseInspectionManager(Plugin plugin, Supplier<FileConfiguration> configuration, Runtime runtime) {
        this.plugin = Objects.requireNonNull(plugin);
        this.configuration = Objects.requireNonNull(configuration);
        this.runtime = Objects.requireNonNull(runtime);
    }

    public void start() {
        stopped = false;
        HandlerList.unregisterAll(removalListener);
        registerRemovalEvent();
        cleanLoadedDisplays();
    }

    /** Like other PDC reads, the caller must own the supplied entity. */
    public static boolean isInspectionDisplay(Entity entity) {
        return entity instanceof TextDisplay
                && entity.getPersistentDataContainer().has(MARKER, PersistentDataType.BYTE);
    }

    /** Interaction callbacks must own both the viewer and mount; split-region attempts fail closed. */
    public boolean show(Player viewer, AbstractHorse horse, Component text) {
        if (!available() || viewer == null || horse == null || text == null
                || !runtime.owned(viewer) || !runtime.owned(horse)) return false;
        long expectedGeneration = generation.get();
        FileConfiguration config = configuration.get();
        if (!config.getBoolean("horse-inspection.enabled", true) || !viewer.isOnline() || viewer.isDead()
                || !horse.isValid() || horse.isDead() || horse.isLoveMode() || !viewer.getWorld().equals(horse.getWorld())
                || !WorldAccessPolicy.isEnabled(horse.getWorld())) return false;
        double range = finiteRange(config.getDouble("horse-inspection.view-range", 16.0D), 16.0D, 1.0D, 64.0D);
        if (viewer.getLocation().distanceSquared(horse.getLocation()) > range * range) return false;
        double height = finiteRange(config.getDouble("horse-inspection.height-offset", 0.5D), 0.5D, 0.0D, 8.0D);
        double seconds = finiteRange(config.getDouble("horse-inspection.duration-seconds", 10.0D), 10.0D, 0.05D, 3600.0D);
        long ticks = Math.max(1L, (long) Math.ceil(seconds * 20.0D));
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
                // The shaded Adventure classes must not cross native server Component signatures.
                candidate.setText(TEXT_SERIALIZER.serialize(text));
                candidate.setBillboard(Display.Billboard.CENTER);
                candidate.setAlignment(TextDisplay.TextAlignment.CENTER);
                candidate.setShadowed(true);
                candidate.setSeeThrough(false);
                candidate.setDefaultBackground(false);
                candidate.setBackgroundColor(Color.fromARGB(180, 18, 23, 31));
                candidate.setTextOpacity((byte) -1);
                candidate.setLineWidth(260);
                candidate.setViewRange((float) (range / 64.0D));
                candidate.setShadowRadius(0.0F);
                candidate.setShadowStrength(0.0F);
                candidate.setTransformation(new Transformation(new Vector3f(0.0F, (float) height, 0.0F),
                        new Quaternionf(), new Vector3f(0.65F), new Quaternionf()));
            });
            if (!available() || expectedGeneration != generation.get() || display == null
                    || !runtime.owned(viewer) || !runtime.owned(horse) || !runtime.owned(display)
                    || !display.isValid() || !horse.addPassenger(display)) {
                removeDisplay(pending[0], null);
                return false;
            }
            // addPassenger dispatches other plugins' event listeners. They can teleport
            // participants or reload this plugin before control returns here.
            if (!available() || expectedGeneration != generation.get() || !runtime.owned(viewer)
                    || !runtime.owned(horse) || !runtime.owned(display)) {
                removeDisplay(display, null);
                return false;
            }
            inspection = new Inspection(viewer, horse, display, range * range);
            Inspection previous = inspections.put(inspection.viewerId, inspection);
            close(previous, false);
            Inspection expected = inspection;
            attach(expected.expiration, runtime.later(display, ticks, () -> close(expected, false),
                    () -> close(expected, true)), expected);
            attach(expected.monitor, runtime.repeating(display, 10L, 10L, () -> monitor(expected),
                    () -> close(expected, true)), expected);
            if (expected.closed.get() || !valid(expected) || !available() || expectedGeneration != generation.get()) {
                close(expected, false);
                return false;
            }
            viewer.showEntity(plugin, display);
            if (expected.closed.get() || !available() || expectedGeneration != generation.get() || !valid(expected)) {
                close(expected, false);
                return false;
            }
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (inspection != null) close(inspection, false);
            else removeDisplay(pending[0], null);
            log("Could not show temporary horse inspection", failure);
            return false;
        }
    }

    public void hide(Player viewer) {
        if (viewer != null && runtime.owned(viewer)) close(inspections.get(viewer.getUniqueId()), false);
    }

    public void hideMount(AbstractHorse horse) {
        if (horse == null || !runtime.owned(horse)) return;
        removeRelated(horse);
        for (Entity passenger : new ArrayList<>(horse.getPassengers())) {
            if (runtime.owned(passenger) && isInspectionDisplay(passenger)) removeDisplay((TextDisplay) passenger, null);
        }
    }

    private void monitor(Inspection inspection) {
        if (inspection.closed.get()) return;
        try {
            if (!valid(inspection)) close(inspection, false);
        } catch (RuntimeException | LinkageError failure) {
            close(inspection, false);
        }
    }

    private boolean valid(Inspection inspection) {
        // Ownership checks must precede every live read. A viewer crossing into a different
        // region closes the display rather than inspecting player state from its old region.
        if (!runtime.owned(inspection.display) || !runtime.owned(inspection.horse)
                || !runtime.owned(inspection.viewer)) return false;
        Entity vehicle = inspection.display.getVehicle();
        return inspection.viewer.isOnline() && !inspection.viewer.isDead()
                && inspection.horse.isValid() && !inspection.horse.isDead() && !inspection.horse.isLoveMode()
                && inspection.display.isValid() && vehicle == inspection.horse
                && inspection.viewer.getWorld().equals(inspection.horse.getWorld())
                && WorldAccessPolicy.isEnabled(inspection.horse.getWorld())
                && configuration.get().getBoolean("horse-inspection.enabled", true)
                && inspection.viewer.getLocation().distanceSquared(inspection.horse.getLocation()) <= inspection.rangeSquared;
    }

    private void close(Inspection inspection, boolean retiredDisplay) {
        if (inspection == null || !inspection.closed.compareAndSet(false, true)) return;
        inspections.remove(inspection.viewerId, inspection);
        cancel(inspection.expiration.getAndSet(null));
        cancel(inspection.monitor.getAndSet(null));
        if (!retiredDisplay) removeDisplay(inspection.display, inspection.viewer);
    }

    private void attach(AtomicReference<ScheduledTaskHandle> slot, ScheduledTaskHandle task, Inspection inspection) {
        slot.set(task);
        if (inspection.closed.get()) cancel(slot.getAndSet(null));
        else if (task.isCancelled()) close(inspection, false);
    }

    private void cancel(ScheduledTaskHandle task) {
        if (task == null) return;
        try { task.cancel(); }
        catch (RuntimeException | LinkageError failure) { log("Could not cancel inspection cleanup task", failure); }
    }

    private void removeDisplay(TextDisplay display, Player viewer) {
        if (display == null) return;
        UUID id = display.getUniqueId(); // immutable identity is safe even when the owner changed
        Removal removal = new Removal(id, display, viewer);
        Removal existing = pendingRemovals.putIfAbsent(id, removal);
        if (existing == null) cleanup(removal);
        else if (!existing.queued.get()) cleanup(existing);
    }

    private void cleanup(Removal removal) {
        if (pendingRemovals.get(removal.id) != removal) return;
        if (runtime.owned(removal.display)) {
            if (removal.viewer != null && runtime.owned(removal.viewer)) {
                try { removal.viewer.hideEntity(plugin, removal.display); }
                catch (RuntimeException | LinkageError failure) { log("Could not hide expired inspection display", failure); }
            }
            if (runtime.owned(removal.display)) {
                try {
                    removal.display.remove();
                    pendingRemovals.remove(removal.id, removal);
                    return;
                } catch (RuntimeException | LinkageError failure) { log("Could not remove inspection display; cleanup will retry", failure); }
            }
        }
        if (!available()) {
            // Disabled plugins cannot schedule owner work. The display was never persistent;
            // its marker allows startup/entity-load recovery after the next enable.
            pendingRemovals.remove(removal.id, removal);
            return;
        }
        if (!removal.queued.compareAndSet(false, true)) return;
        TaskSlot slot = new TaskSlot();
        try {
            ScheduledTaskHandle task = runtime.later(removal.display, 10L, () -> slot.run(() -> {
                removal.queued.set(false);
                cleanup(removal);
            }), () -> {
                removal.queued.set(false);
                pendingRemovals.remove(removal.id, removal);
                slot.finish();
            });
            slot.attach(task);
            if (task.isCancelled()) removal.queued.set(false);
        } catch (RuntimeException | LinkageError failure) {
            removal.queued.set(false);
            slot.finish();
            log("Could not schedule inspection cleanup retry", failure);
        }
    }

    private void removeRelated(Entity entity) {
        UUID id = entity.getUniqueId();
        for (Inspection inspection : new ArrayList<>(inspections.values())) {
            if (inspection.horseId.equals(id) || inspection.displayId.equals(id)) close(inspection, false);
        }
    }

    private void clear() {
        generation.incrementAndGet();
        for (Inspection inspection : new ArrayList<>(inspections.values())) close(inspection, false);
    }

    @Override public void onConfigurationReload() {
        clear();
        cleanLoadedDisplays();
    }

    @Override public void shutdown() {
        stopped = true;
        clear();
        for (TaskSlot slot : new ArrayList<>(background)) slot.finish();
        for (Removal removal : new ArrayList<>(pendingRemovals.values())) cleanup(removal);
        pendingRemovals.clear();
        HandlerList.unregisterAll(removalListener);
    }

    private boolean available() { return !stopped && plugin.isEnabled(); }

    private void cleanLoadedDisplays() {
        if (!available()) return;
        TaskSlot snapshot = new TaskSlot();
        try {
            snapshot.attach(runtime.global(() -> snapshot.run(() -> {
                for (World world : Bukkit.getWorlds()) {
                    for (Chunk chunk : world.getLoadedChunks()) {
                        Location location = new Location(world, chunk.getX() << 4, 0, chunk.getZ() << 4);
                        TaskSlot region = new TaskSlot();
                        try {
                            region.attach(runtime.region(location, () -> region.run(() -> {
                                if (!runtime.owned(location) || !chunk.isLoaded() || !chunk.isEntitiesLoaded()) return;
                                for (Entity entity : chunk.getEntities()) cleanStale(entity);
                            })));
                        } catch (RuntimeException | LinkageError failure) {
                            region.finish();
                            log("Could not schedule stale inspection chunk cleanup", failure);
                        }
                    }
                }
            })));
        } catch (RuntimeException | LinkageError failure) {
            snapshot.finish();
            log("Could not scan loaded chunks for stale inspection displays", failure);
        }
    }

    private void cleanStale(Entity entity) {
        if (!runtime.owned(entity) || !isInspectionDisplay(entity)) return;
        UUID id = entity.getUniqueId();
        if (inspections.values().stream().noneMatch(inspection -> inspection.displayId.equals(id))) {
            removeDisplay((TextDisplay) entity, null);
        }
    }

    @SuppressWarnings("unchecked")
    private void registerRemovalEvent() {
        for (String prefix : new String[]{"io.papermc.paper.event.entity.", "com.destroystokyo.paper.event.entity."}) {
            try {
                Class<?> candidate = Class.forName(prefix + "EntityRemoveFromWorldEvent", false, plugin.getClass().getClassLoader());
                if (!Event.class.isAssignableFrom(candidate)) continue;
                plugin.getServer().getPluginManager().registerEvent((Class<? extends Event>) candidate, removalListener,
                        EventPriority.MONITOR, (ignored, event) -> {
                            if (event instanceof EntityEvent removal) removeRelated(removal.getEntity());
                        }, plugin, true);
                return;
            } catch (ClassNotFoundException ignored) {
                // Supported Paper/Folia versions expose either package.
            } catch (RuntimeException | LinkageError failure) { log("Could not register inspection entity removal cleanup", failure); }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR) public void onQuit(PlayerQuitEvent event) { hide(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onKick(PlayerKickEvent event) { hide(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void onWorldChange(PlayerChangedWorldEvent event) { hide(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onPlayerTeleport(PlayerTeleportEvent event) { hide(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void onPlayerDeath(PlayerDeathEvent event) { hide(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR) public void onDeath(EntityDeathEvent event) { removeRelated(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onLoveMode(EntityEnterLoveModeEvent event) { removeRelated(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onTeleport(EntityTeleportEvent event) { removeRelated(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player && event.getMount() instanceof AbstractHorse horse) hideMount(horse);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onDismount(EntityDismountEvent event) {
        if (runtime.owned(event.getEntity()) && isInspectionDisplay(event.getEntity())) removeRelated(event.getEntity());
    }
    @EventHandler(priority = EventPriority.MONITOR) public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) cleanStale(entity);
    }
    @EventHandler(priority = EventPriority.MONITOR) public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) removeRelated(entity);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        for (Inspection inspection : new ArrayList<>(inspections.values())) {
            if (!runtime.owned(inspection.horse)) continue;
            Location location = inspection.horse.getLocation();
            if (chunk.getWorld().equals(location.getWorld()) && chunk.getX() == (location.getBlockX() >> 4)
                    && chunk.getZ() == (location.getBlockZ() >> 4)) close(inspection, false);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void onWorldUnload(WorldUnloadEvent event) {
        UUID worldId = event.getWorld().getUID();
        for (Inspection inspection : new ArrayList<>(inspections.values())) {
            if (worldId.equals(inspection.worldId)) close(inspection, false);
        }
    }

    private void log(String message, Throwable failure) { plugin.getLogger().log(Level.FINE, message, failure); }
    private static double finiteRange(double value, double fallback, double minimum, double maximum) {
        return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
    }

    interface Runtime {
        boolean owned(Entity entity);
        boolean owned(Location location);
        ScheduledTaskHandle later(Entity entity, long ticks, Runnable action, Runnable retired);
        ScheduledTaskHandle repeating(Entity entity, long delay, long period, Runnable action, Runnable retired);
        ScheduledTaskHandle global(Runnable action);
        ScheduledTaskHandle region(Location location, Runnable action);
    }

    private record FoliaRuntime(BetterHorsesScheduler scheduler) implements Runtime {
        @Override public boolean owned(Entity entity) { return Bukkit.isOwnedByCurrentRegion(entity); }
        @Override public boolean owned(Location location) { return Bukkit.isOwnedByCurrentRegion(location); }
        @Override public ScheduledTaskHandle later(Entity entity, long ticks, Runnable action, Runnable retired) {
            return scheduler.entity(entity, ticks, ignored -> action.run(), retired);
        }
        @Override public ScheduledTaskHandle repeating(Entity entity, long delay, long period, Runnable action, Runnable retired) {
            return scheduler.entityRepeating(entity, delay, period, ignored -> action.run(), retired);
        }
        @Override public ScheduledTaskHandle global(Runnable action) { return scheduler.global(ignored -> action.run()); }
        @Override public ScheduledTaskHandle region(Location location, Runnable action) { return scheduler.region(location, 1L, ignored -> action.run()); }
    }

    private final class TaskSlot {
        private final AtomicReference<ScheduledTaskHandle> handle = new AtomicReference<>();
        private final AtomicBoolean finished = new AtomicBoolean();
        TaskSlot() { background.add(this); }
        void attach(ScheduledTaskHandle task) {
            handle.set(task);
            if (finished.get() || task.isCancelled()) finish();
        }
        void run(Runnable action) {
            try { if (!finished.get() && available()) action.run(); }
            finally { finish(); }
        }
        void finish() {
            finished.set(true);
            background.remove(this);
            cancel(handle.getAndSet(null));
        }
    }

    private static final class Removal {
        final UUID id;
        final TextDisplay display;
        final Player viewer;
        final AtomicBoolean queued = new AtomicBoolean();
        Removal(UUID id, TextDisplay display, Player viewer) { this.id = id; this.display = display; this.viewer = viewer; }
    }
    private static final class Inspection {
        final Player viewer;
        final AbstractHorse horse;
        final TextDisplay display;
        final UUID viewerId, horseId, displayId, worldId;
        final double rangeSquared;
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicReference<ScheduledTaskHandle> expiration = new AtomicReference<>();
        final AtomicReference<ScheduledTaskHandle> monitor = new AtomicReference<>();
        Inspection(Player viewer, AbstractHorse horse, TextDisplay display, double rangeSquared) {
            this.viewer = viewer; this.horse = horse; this.display = display; this.rangeSquared = rangeSquared;
            viewerId = viewer.getUniqueId(); horseId = horse.getUniqueId(); displayId = display.getUniqueId();
            worldId = horse.getWorld().getUID();
        }
    }
}
