package me.luisgamedev.betterhorses.growing;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.MountConfig;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
        if (!MountConfig.isGrowthEnabled(config, type)) {
            if (data.has(growthKey) || data.has(growthUpdatedKey)) resetGrowthForHorse(horse);
            return;
        }
        int minutes = Math.max(1, config.getInt("horse-growth-settings.time-until-adult", 60));
        long stageMillis = GrowthProgress.intervalMillis(minutes);
        Integer stored = data.get(growthKey, PersistentDataType.INTEGER);
        GrowthProgress progress = GrowthProgress.advance(stored == null ? (horse.isAdult() ? GrowthScale.MAX_STAGE : 1) : stored,
                data.get(growthUpdatedKey, PersistentDataType.LONG), System.currentTimeMillis(), stageMillis,
                !horse.getPassengers().isEmpty());
        boolean stageChanged = stored == null || stored != progress.stage();
        data.set(growthKey, PersistentDataType.INTEGER, progress.stage());
        data.set(growthUpdatedKey, PersistentDataType.LONG, progress.updatedAt());
        if (!horse.getPassengers().isEmpty()) {
            if (stageChanged) plugin.refreshMountShaders(horse);
            return;
        }
        int threshold = GrowthScale.normalize(config.getInt("horse-growth-settings.ride-and-breed-threshhold", 7));
        if (progress.stage() >= threshold && !horse.isAdult()) horse.setAdult();
        horse.setAgeLock(!horse.isAdult());
        setScaleSafe(horse, GrowthScale.forStage(progress.stage(), config.getDouble("horse-growth-settings.max-size", 1.3)));
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
        try {
            AttributeInstance attribute = horse.getAttribute(Attribute.valueOf("SCALE"));
            if (attribute != null) attribute.setBaseValue(scale);
        } catch (IllegalArgumentException | NoSuchFieldError exception) {
            plugin.debugLog("GROWTH_MANAGER", "SET_SCALE", false, exception.getMessage());
        }
    }
}
