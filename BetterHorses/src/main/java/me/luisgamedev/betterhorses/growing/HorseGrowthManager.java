package me.luisgamedev.betterhorses.growing;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.inspection.HorseInspectionManager;
import me.luisgamedev.betterhorses.utils.MountConfig;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public class HorseGrowthManager {
    private final BetterHorses plugin;
    private final NamespacedKey growthKey;
    private final NamespacedKey growthUpdatedKey;

    public HorseGrowthManager(BetterHorses plugin) {
        this.plugin = plugin;
        growthKey = new NamespacedKey(plugin, "growth_stage");
        growthUpdatedKey = new NamespacedKey(plugin, "growth_updated_at");
    }

    public void start() {
        new BukkitRunnable() {
            @Override public void run() {
                FileConfiguration config = plugin.getConfig();
                for (World world : Bukkit.getWorlds()) {
                    if (!WorldAccessPolicy.isEnabled(world)) continue;
                    for (AbstractHorse horse : world.getEntitiesByClass(AbstractHorse.class)) {
                        SupportedMountType type = SupportedMountType.fromEntity(horse).orElse(null);
                        if (type == null || !type.isEnabled(config) || !horse.isValid()) continue;
                        processGrowth(horse, type, config);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 20L);
    }

    private void processGrowth(AbstractHorse horse, SupportedMountType type, FileConfiguration config) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        HorseSize.ensure(data, config);
        if (!MountConfig.isGrowthEnabled(config, type)) {
            if (data.has(growthKey) || data.has(growthUpdatedKey)) resetGrowthForHorse(horse);
            else HorseSize.applyWithoutGrowth(horse, config);
            return;
        }
        int minutes = Math.max(1, config.getInt("horse-growth-settings.time-until-adult", 60));
        long stageMillis = GrowthProgress.intervalMillis(minutes);
        Integer stored = data.get(growthKey, PersistentDataType.INTEGER);
        boolean occupied = horse.getPassengers().stream().anyMatch(passenger -> !HorseInspectionManager.isInspectionDisplay(passenger));
        GrowthProgress progress = GrowthProgress.advance(stored == null ? (horse.isAdult() ? GrowthScale.MAX_STAGE : 1) : stored,
                data.get(growthUpdatedKey, PersistentDataType.LONG), System.currentTimeMillis(), stageMillis,
                occupied);
        boolean stageChanged = stored == null || stored != progress.stage();
        data.set(growthKey, PersistentDataType.INTEGER, progress.stage());
        data.set(growthUpdatedKey, PersistentDataType.LONG, progress.updatedAt());
        if (occupied) {
            setScaleSafe(horse, GrowthScale.forStageUnclamped(progress.stage(), config.getDouble("horse-growth-settings.max-size", 1.3),
                    horse.isAdult(), GrowthScale.babyScale(type.name(), Bukkit.getBukkitVersion())));
            if (stageChanged) plugin.refreshMountShaders(horse);
            return;
        }
        int threshold = GrowthScale.normalize(config.getInt("horse-growth-settings.ride-and-breed-threshhold", 7));
        if (progress.stage() >= threshold && !horse.isAdult()) horse.setAdult();
        else if (progress.stage() < threshold && horse.isAdult()) {
            horse.setBaby();
            horse.setLoveModeTicks(0);
        }
        horse.setAgeLock(!horse.isAdult());
        // Normalize the version's baby model after choosing its age.
        setScaleSafe(horse, GrowthScale.forStageUnclamped(progress.stage(), config.getDouble("horse-growth-settings.max-size", 1.3),
                horse.isAdult(), GrowthScale.babyScale(type.name(), Bukkit.getBukkitVersion())));
        if (stageChanged) plugin.refreshMountShaders(horse);
    }

    private void resetGrowthForHorse(AbstractHorse horse) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        boolean hadGrowth = data.has(growthKey) || data.has(growthUpdatedKey);
        horse.setAgeLock(false);
        setScaleSafe(horse, 1.0);
        data.remove(growthKey);
        data.remove(growthUpdatedKey);
        if (hadGrowth) plugin.refreshMountShaders(horse);
    }

    private void setScaleSafe(AbstractHorse horse, double scale) {
        HorseSize.apply(horse, scale, plugin.getConfig());
    }
}
