package me.luisgamedev.betterhorses.growing;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.inspection.HorseInspectionManager;
import me.luisgamedev.betterhorses.scheduler.ScheduledTaskHandle;
import me.luisgamedev.betterhorses.traits.TraitParticleResolver;
import me.luisgamedev.betterhorses.utils.MountConfig;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Event-driven, entity-owned growth and trait indicator processing. */
public final class HorseGrowthManager implements Listener {
    private static final int MAX_STAGE = 10;
    private final BetterHorses plugin;
    private volatile boolean stopped;
    private final NamespacedKey growthKey;
    private final NamespacedKey growthUpdatedKey;
    private final NamespacedKey traitKey;
    private final ConcurrentMap<UUID, GrowthTask> tasks = new ConcurrentHashMap<>();

    public HorseGrowthManager(BetterHorses plugin) {
        this.plugin = plugin;
        growthKey = new NamespacedKey(plugin, "growth_stage");
        growthUpdatedKey = new NamespacedKey(plugin, "growth_updated_at");
        traitKey = new NamespacedKey(plugin, "trait");
    }

    public void start() {
        stopped = false;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Discover existing entities without reading or mutating them outside their owning region.
        plugin.scheduler().global(ignored -> Bukkit.getWorlds().forEach(world -> {
            for (Chunk chunk : world.getLoadedChunks()) {
                plugin.scheduler().region(new Location(world, chunk.getX() << 4, 0, chunk.getZ() << 4), 1L, task -> {
                    if (!chunk.isLoaded() || !chunk.isEntitiesLoaded()) return;
                    for (org.bukkit.entity.Entity entity : chunk.getEntities()) track(entity);
                });
            }
        }));
    }

    public void shutdown() {
        stopped = true;
        tasks.values().forEach(ScheduledTaskHandle::cancel);
        tasks.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) { track(event.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdded(EntityAddToWorldEvent event) { track(event.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) { track(event.getMount()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemoved(EntityRemoveFromWorldEvent event) {
        ScheduledTaskHandle task = tasks.remove(event.getEntity().getUniqueId());
        if (task != null) task.cancel();
    }

    public void track(org.bukkit.entity.Entity entity) {
        if (stopped || !(entity instanceof AbstractHorse horse) || !SupportedMountType.isSupported(horse)) return;
        UUID id = horse.getUniqueId();
        GrowthTask candidate = new GrowthTask(id);
        for (;;) {
            GrowthTask existing = tasks.putIfAbsent(id, candidate);
            if (existing == null) break;
            if (!existing.isDone()) return;
            tasks.remove(id, existing);
        }
        if (stopped) { candidate.cancel(); return; }
        try {
            // Retirement can run synchronously during submission. Install an identity
            // first, outside map.compute, so rejection and late callbacks are harmless.
            candidate.attach(plugin.scheduler().entityRepeating(horse, 1L, 20L,
                    task -> { if (!candidate.isDone()) tick(horse); }, candidate::cancel));
        } catch (RuntimeException | LinkageError failure) {
            candidate.cancel();
            throw failure;
        }
    }

    private final class GrowthTask implements ScheduledTaskHandle {
        private final UUID id;
        private final AtomicBoolean done = new AtomicBoolean();
        private final AtomicReference<ScheduledTaskHandle> handle = new AtomicReference<>();

        private GrowthTask(UUID id) { this.id = id; }

        private void attach(ScheduledTaskHandle submitted) {
            handle.set(submitted);
            if (done.get() || stopped || tasks.get(id) != this || submitted.isDone()) cancel();
        }

        @Override public void cancel() {
            done.set(true);
            tasks.remove(id, this);
            ScheduledTaskHandle submitted = handle.getAndSet(null);
            if (submitted != null) submitted.cancel();
        }

        @Override public boolean isCancelled() {
            ScheduledTaskHandle submitted = handle.get();
            return done.get() || (submitted != null && submitted.isCancelled());
        }

        @Override public boolean isDone() {
            ScheduledTaskHandle submitted = handle.get();
            return done.get() || (submitted != null && submitted.isDone());
        }
    }

    private void tick(AbstractHorse horse) {
        if (!horse.isValid() || !WorldAccessPolicy.isEnabled(horse.getWorld())) return;
        FileConfiguration config = plugin.getConfig();
        SupportedMountType type = SupportedMountType.fromEntity(horse).orElse(null);
        if (type == null || !type.isEnabled(config)) return;
        processGrowth(horse, type, config);
        processParticle(horse, config);
    }

    private void processGrowth(AbstractHorse horse, SupportedMountType type, FileConfiguration config) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        HorseSize.ensure(data, config);
        if (!config.getBoolean("horse-growth-settings.enabled") || !MountConfig.isGrowthEnabled(config, type)) {
            if (data.has(growthKey) || data.has(growthUpdatedKey)) reset(horse);
            else HorseSize.applyWithoutGrowth(horse, config);
            return;
        }
        int minutes = Math.max(1, config.getInt("horse-growth-settings.time-until-adult", 60));
        long stageMillis = GrowthProgress.intervalMillis(minutes);
        long now = System.currentTimeMillis();
        Integer stored = data.get(growthKey, PersistentDataType.INTEGER);
        boolean occupied = horse.getPassengers().stream().anyMatch(passenger -> !HorseInspectionManager.isInspectionDisplay(passenger));
        GrowthProgress progress = GrowthProgress.advance(stored == null ? (horse.isAdult() ? MAX_STAGE : 1) : stored,
                data.get(growthUpdatedKey, PersistentDataType.LONG), now, stageMillis, occupied);
        int stage = progress.stage();
        boolean stageChanged = stored == null || stored != stage;
        data.set(growthKey, PersistentDataType.INTEGER, stage);
        data.set(growthUpdatedKey, PersistentDataType.LONG, progress.updatedAt());
        if (occupied) {
            setScale(horse, GrowthScale.forStageUnclamped(stage, config.getDouble("horse-growth-settings.max-size", 1.3),
                    horse.isAdult(), GrowthScale.babyScale(type.name(), Bukkit.getBukkitVersion())));
            if (stageChanged) plugin.refreshMountShaders(horse);
            return;
        }
        int threshold = GrowthScale.normalize(config.getInt("horse-growth-settings.ride-and-breed-threshhold", 7));
        if (stage >= threshold && !horse.isAdult()) horse.setAdult();
        else if (stage < threshold && horse.isAdult()) {
            horse.setBaby();
            horse.setLoveModeTicks(0);
        }
        horse.setAgeLock(!horse.isAdult());
        // Normalize the version's baby model after choosing its age, so the
        // riding threshold contributes only the next overall size increment.
        setScale(horse, GrowthScale.forStageUnclamped(stage, config.getDouble("horse-growth-settings.max-size", 1.3),
                horse.isAdult(), GrowthScale.babyScale(type.name(), Bukkit.getBukkitVersion())));
        if (stageChanged) plugin.refreshMountShaders(horse);
    }

    private void reset(AbstractHorse horse) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        boolean hadGrowth = data.has(growthKey) || data.has(growthUpdatedKey);
        horse.setAgeLock(false);
        setScale(horse, 1.0);
        data.remove(growthKey);
        data.remove(growthUpdatedKey);
        if (hadGrowth) plugin.refreshMountShaders(horse);
    }

    private void setScale(AbstractHorse horse, double scale) {
        HorseSize.apply(horse, scale, plugin.getConfig());
    }

    private void processParticle(AbstractHorse horse, FileConfiguration config) {
        if (!config.getBoolean("settings.trait-particle-indicator", false)
                || !config.getBoolean("traits.enabled", true)) return;
        String trait = horse.getPersistentDataContainer().get(traitKey, PersistentDataType.STRING);
        if (trait == null || !config.getBoolean("traits." + trait.toLowerCase() + ".enabled", false)) return;
        Particle fallback = switch (trait.toLowerCase()) {
            case "hellmare" -> Particle.FLAME; case "ghosthorse" -> me.luisgamedev.betterhorses.utils.RuntimeCompat.particle("SMOKE", "SMOKE_NORMAL");
            case "dashboost" -> Particle.SWEEP_ATTACK; case "revenantcurse" -> Particle.SOUL;
            case "frosthooves" -> Particle.SNOWFLAKE; case "kickback" -> Particle.CRIT;
            case "fireheart" -> Particle.LAVA; case "heavenhooves" -> Particle.GLOW;
            default -> Particle.CLOUD;
        };
        horse.getWorld().spawnParticle(TraitParticleResolver.getTraitParticle(trait.toLowerCase(), fallback),
                horse.getLocation().add(0, 1.2, 0), 2, .3, .2, .3, .01);
    }
}
