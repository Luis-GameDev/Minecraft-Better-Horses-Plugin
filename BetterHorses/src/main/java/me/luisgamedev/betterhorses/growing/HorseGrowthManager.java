package me.luisgamedev.betterhorses.growing;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.growing.HorseGrowthData.Availability;
import me.luisgamedev.betterhorses.inspection.HorseInspectionManager;
import me.luisgamedev.betterhorses.utils.MountConfig;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Owns loaded juveniles; the timer only visits mounts whose next stage is due. */
public final class HorseGrowthManager implements Listener {
    private final BetterHorses plugin;
    private final LongSupplier clock;
    private final Map<UUID, Entry> growing = new HashMap<>();
    private final NavigableSet<Entry> due = new TreeSet<>(Comparator.comparingLong(Entry::dueAt)
            .thenComparing(Entry::id));
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    private BukkitTask timer;
    private boolean running = true;
    private long intervalMillis;
    private long lastTick;

    public HorseGrowthManager(BetterHorses plugin) {
        this(plugin, System::currentTimeMillis);
    }

    HorseGrowthManager(BetterHorses plugin, LongSupplier clock) {
        this.plugin = plugin;
        this.clock = clock;
        intervalMillis = configuredInterval();
    }

    public void start() {
        if (timer != null) return;
        running = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        registerRemovalEvent();
        reload();
        timer = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 20L);
    }

    /** Checkpoint using the old interval before configuration or world policy changes. */
    public void prepareReload() {
        pending.values().forEach(BukkitTask::cancel);
        pending.clear();
        for (Entry entry : new ArrayList<>(growing.values())) suspend(entry.horse());
    }

    /** Full enumeration is limited to startup and explicit configuration reloads. */
    public void reload() {
        prepareReload();
        intervalMillis = configuredInterval();
        for (World world : Bukkit.getWorlds()) {
            if (!WorldAccessPolicy.isEnabled(world)) continue;
            for (AbstractHorse horse : world.getEntitiesByClass(AbstractHorse.class)) reconcile(horse);
        }
    }

    public void shutdown() {
        prepareReload();
        running = false;
        if (timer != null) timer.cancel();
        timer = null;
        HandlerList.unregisterAll(this);
    }

    void tick() {
        if (!running) return;
        long now = clock.getAsLong();
        if (now < lastTick) {
            // A rollback must not leave entries waiting for their former wall-clock deadlines.
            for (Entry entry : new ArrayList<>(growing.values())) reconcile(entry.horse());
        }
        lastTick = now;
        while (!due.isEmpty() && due.first().dueAt() <= now) {
            Entry entry = due.pollFirst();
            if (!entry.horse().isValid() || entry.horse().isDead()) forget(entry.horse());
            else reconcile(entry.horse());
        }
    }

    /** Re-read metadata after a load, occupancy change, or explicit API stage change. */
    public void reconcile(AbstractHorse horse) {
        if (!running) return;
        FileConfiguration config = plugin.getConfig();
        SupportedMountType type = SupportedMountType.fromEntity(horse).orElse(null);
        if (!horse.isValid() || horse.isDead()) {
            forget(horse);
            return;
        }
        if (type == null || !type.isEnabled(config) || !WorldAccessPolicy.isEnabled(horse.getWorld())) {
            if (growing.containsKey(horse.getUniqueId())) suspend(horse);
            return;
        }
        forgetEntry(horse);
        PersistentDataContainer data = horse.getPersistentDataContainer();
        HorseSize.ensure(data, config);
        if (!MountConfig.isGrowthEnabled(config, type)) {
            boolean hadGrowth = HorseGrowthData.hasProgress(data);
            if (hadGrowth) {
                horse.setAgeLock(false);
                HorseGrowthData.clear(data);
                HorseSize.apply(horse, 1.0D, config);
                plugin.refreshMountShaders(horse);
            } else HorseSize.applyWithoutGrowth(horse, config);
            return;
        }

        Integer previousStage = data.get(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER);
        long now = clock.getAsLong();
        GrowthState state = readCurrent(horse, now);
        boolean occupied = occupied(horse);
        HorseGrowthData.write(data, state, occupied ? Availability.MOUNTED : Availability.LOADED);
        applyStage(horse, type, state.stage(), occupied, config);
        if (previousStage == null || previousStage != state.stage()) plugin.refreshMountShaders(horse);
        if (state.stage() < GrowthScale.MAX_STAGE) {
            long nextDue = occupied ? Long.MAX_VALUE : saturatedAdd(now, state.remainingMillis(intervalMillis));
            Entry entry = new Entry(horse.getUniqueId(), horse, nextDue);
            growing.put(horse.getUniqueId(), entry);
            if (!occupied) due.add(entry);
        }
    }

    /** Settle before serializing an item, without pausing or removing the live source. */
    public GrowthState snapshot(AbstractHorse horse) {
        reconcile(horse);
        return HorseGrowthData.read(horse.getPersistentDataContainer(), horse.isAdult() ? 10 : 1,
                clock.getAsLong(), intervalMillis);
    }

    public void forget(AbstractHorse horse) {
        forgetEntry(horse);
        BukkitTask task = pending.remove(horse.getUniqueId());
        if (task != null) task.cancel();
    }

    private void forgetEntry(AbstractHorse horse) {
        Entry previous = growing.remove(horse.getUniqueId());
        if (previous != null) due.remove(previous);
    }

    private GrowthState readCurrent(AbstractHorse horse, long now) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        if (!data.has(BetterHorseKeys.GROWTH_PROGRESS_MILLIS, PersistentDataType.LONG) && occupied(horse)) {
            // Legacy riding reset the boundary on every pass; no past elapsed time was earned.
            return GrowthState.initial(data.getOrDefault(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER,
                    horse.isAdult() ? 10 : 1), now);
        }
        return HorseGrowthData.read(data, horse.isAdult() ? 10 : 1, now, intervalMillis)
                .advance(now, intervalMillis, HorseGrowthData.availability(data).accrues(plugin.getConfig()));
    }

    private void suspend(AbstractHorse horse) {
        if (!growing.containsKey(horse.getUniqueId())) {
            SupportedMountType type = SupportedMountType.fromEntity(horse).orElse(null);
            if (type == null || !type.isEnabled(plugin.getConfig()) || !WorldAccessPolicy.isEnabled(horse.getWorld())) {
                forget(horse);
                return;
            }
        }
        forget(horse);
        PersistentDataContainer data = horse.getPersistentDataContainer();
        if (!HorseGrowthData.hasProgress(data)) return;
        GrowthState state = readCurrent(horse, clock.getAsLong());
        HorseGrowthData.write(data, state, Availability.UNLOADED);
    }

    private void applyStage(AbstractHorse horse, SupportedMountType type, int stage, boolean occupied,
                            FileConfiguration config) {
        if (!occupied) {
            int threshold = GrowthScale.normalize(config.getInt("horse-growth-settings.ride-and-breed-threshhold", 7));
            if (stage >= threshold && !horse.isAdult()) horse.setAdult();
            else if (stage < threshold && horse.isAdult()) {
                horse.setBaby();
                horse.setLoveModeTicks(0);
            }
            if (horse.getAgeLock() != !horse.isAdult()) horse.setAgeLock(!horse.isAdult());
        }
        double scale = GrowthScale.forStageUnclamped(stage, config.getDouble("horse-growth-settings.max-size", 1.3),
                horse.isAdult(), GrowthScale.babyScale(type.name(), Bukkit.getBukkitVersion()));
        HorseSize.apply(horse, scale, config);
    }

    private boolean occupied(AbstractHorse horse) {
        return horse.getPassengers().stream().anyMatch(passenger -> !HorseInspectionManager.isInspectionDisplay(passenger));
    }

    private long configuredInterval() {
        return GrowthProgress.intervalMillis(plugin.getConfig().getInt("horse-growth-settings.time-until-adult", 60));
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private void request(Entity entity) {
        if (!running || !(entity instanceof AbstractHorse horse)) return;
        UUID id = horse.getUniqueId();
        if (pending.containsKey(id)) return;
        // Spawn/breed/API writes and passenger changes finish before this reads them.
        pending.put(id, Bukkit.getScheduler().runTask(plugin, () -> {
            pending.remove(id);
            if (running) reconcile(horse);
        }));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) { request(event.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoaded(EntitiesLoadEvent event) { event.getEntities().forEach(this::request); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnloaded(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) if (entity instanceof AbstractHorse horse) suspend(horse);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        for (Entry entry : new ArrayList<>(growing.values())) {
            if (entry.horse().getWorld().equals(event.getWorld())) suspend(entry.horse());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (!HorseInspectionManager.isInspectionDisplay(event.getEntity())) request(event.getMount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (!HorseInspectionManager.isInspectionDisplay(event.getEntity())) request(event.getDismounted());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(EntityTeleportEvent event) { request(event.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof AbstractHorse horse) forget(horse);
    }

    @SuppressWarnings("unchecked")
    private void registerRemovalEvent() {
        // Optional to retain compatibility with servers predating Bukkit's EntityRemoveEvent.
        for (String name : new String[]{"org.bukkit.event.entity.EntityRemoveEvent",
                "com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent"}) {
            try {
                Class<? extends Event> type = (Class<? extends Event>) Class.forName(name, false, plugin.getClass().getClassLoader());
                plugin.getServer().getPluginManager().registerEvent(type, this, EventPriority.MONITOR,
                        (ignored, event) -> {
                            if (event instanceof EntityEvent removal && removal.getEntity() instanceof AbstractHorse horse) forget(horse);
                        }, plugin, true);
                return;
            } catch (ClassNotFoundException ignored) {
                // EntitiesUnloadEvent and the due-entry validity check remain available.
            }
        }
    }

    private record Entry(UUID id, AbstractHorse horse, long dueAt) { }
}
