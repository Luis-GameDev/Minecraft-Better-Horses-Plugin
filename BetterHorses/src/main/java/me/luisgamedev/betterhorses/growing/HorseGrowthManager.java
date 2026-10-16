package me.luisgamedev.betterhorses.growing;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.scheduler.ScheduledTaskHandle;
import me.luisgamedev.betterhorses.traits.TraitParticleResolver;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import me.luisgamedev.betterhorses.utils.MountConfig;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.AttributeInstance;
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

/** Event-driven, entity-owned growth and trait indicator processing. */
public final class HorseGrowthManager implements Listener {
    private static final int MAX_STAGE = 10;
    private final BetterHorses plugin;
    private final NamespacedKey growthKey;
    private final NamespacedKey growthUpdatedKey;
    private final NamespacedKey traitKey;
    private final ConcurrentMap<UUID, ScheduledTaskHandle> tasks = new ConcurrentHashMap<>();

    public HorseGrowthManager(BetterHorses plugin) {
        this.plugin = plugin;
        growthKey = new NamespacedKey(plugin, "growth_stage");
        growthUpdatedKey = new NamespacedKey(plugin, "growth_updated_at");
        traitKey = new NamespacedKey(plugin, "trait");
    }

    public void start() {
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
        if (!(entity instanceof AbstractHorse horse) || !SupportedMountType.isSupported(horse)) return;
        UUID id = horse.getUniqueId();
        tasks.compute(id, (ignored, existing) -> {
            if (existing != null && !existing.isCancelled()) return existing;
            return plugin.scheduler().entityRepeating(horse, 1L, 20L, task -> tick(horse), () -> tasks.remove(id));
        });
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
        if (!config.getBoolean("horse-growth-settings.enabled") || !MountConfig.isGrowthEnabled(config, type)) {
            if (data.has(growthKey) || data.has(growthUpdatedKey)) reset(horse);
            return;
        }
        int minutes = Math.max(1, config.getInt("horse-growth-settings.time-until-adult", 60));
        long stageMillis = GrowthProgress.intervalMillis(minutes);
        long now = System.currentTimeMillis();
        Integer stored = data.get(growthKey, PersistentDataType.INTEGER);
        GrowthProgress progress = GrowthProgress.advance(stored == null ? (horse.isAdult() ? MAX_STAGE : 1) : stored,
                data.get(growthUpdatedKey, PersistentDataType.LONG), now, stageMillis, !horse.getPassengers().isEmpty());
        int stage = progress.stage();
        boolean stageChanged = stored == null || stored != stage;
        data.set(growthKey, PersistentDataType.INTEGER, stage);
        data.set(growthUpdatedKey, PersistentDataType.LONG, progress.updatedAt());
        if (!horse.getPassengers().isEmpty()) {
            if (stageChanged) plugin.refreshMountShaders(horse);
            return;
        }
        int threshold = GrowthScale.normalize(config.getInt("horse-growth-settings.ride-and-breed-threshhold", 7));
        if (stage >= threshold && !horse.isAdult()) horse.setAdult();
        horse.setAgeLock(!horse.isAdult());
        // Vanilla also halves a baby's size. Compensate after changing its age,
        // so reaching the riding threshold adds only the next visual growth step.
        setScale(horse, GrowthScale.forStage(stage, config.getDouble("horse-growth-settings.max-size", 1.3), horse.isAdult()));
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
        AttributeInstance attribute = horse.getAttribute(AttributeResolver.generic("SCALE"));
        if (attribute != null) attribute.setBaseValue(scale);
    }

    private void processParticle(AbstractHorse horse, FileConfiguration config) {
        if (!config.getBoolean("settings.trait-particle-indicator", false)
                || !config.getBoolean("traits.enabled", true)) return;
        String trait = horse.getPersistentDataContainer().get(traitKey, PersistentDataType.STRING);
        if (trait == null || !config.getBoolean("traits." + trait.toLowerCase() + ".enabled", false)) return;
        Particle fallback = switch (trait.toLowerCase()) {
            case "hellmare" -> Particle.FLAME; case "ghosthorse" -> Particle.SMOKE;
            case "dashboost" -> Particle.SWEEP_ATTACK; case "revenantcurse" -> Particle.SOUL;
            case "frosthooves" -> Particle.SNOWFLAKE; case "kickback" -> Particle.CRIT;
            case "fireheart" -> Particle.LAVA; case "heavenhooves" -> Particle.GLOW;
            default -> Particle.CLOUD;
        };
        horse.getWorld().spawnParticle(TraitParticleResolver.getTraitParticle(trait.toLowerCase(), fallback),
                horse.getLocation().add(0, 1.2, 0), 2, .3, .2, .3, .01);
    }
}
