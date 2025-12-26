package me.luisgamedev.betterhorses.growing;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.MountConfig;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.World;

public class HorseGrowthManager {

    private final NamespacedKey growthKey;
    private final int maxStage = 10;

    public HorseGrowthManager(BetterHorses plugin) {
        this.growthKey = new NamespacedKey(plugin, "growth_stage");
    }

    public void start() {

        FileConfiguration config = BetterHorses.getInstance().getConfig();

        if (!config.getBoolean("horse-growth-settings.enabled")) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(AbstractHorse.class).forEach(horse -> {
                        if (!SupportedMountType.isSupported(horse)) return;
                        resetGrowthForHorse(horse);
                    }));
                }
            }.runTask(BetterHorses.getInstance());
            return;
        }

        int minutes = config.getInt("horse-growth-settings.time-until-adult", 60);
        long intervalTicks = (minutes * 60L * 20L) / maxStage;
        float maxScale = (float) config.getDouble("horse-growth-settings.max-size", 1.3f);
        int threshold = config.getInt("horse-growth-settings.ride-and-breed-threshhold", 7);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    for (Entity entity : world.getEntitiesByClass(AbstractHorse.class)) {
                        if (!(entity instanceof AbstractHorse horse)) continue;
                        SupportedMountType mountType = SupportedMountType.fromEntity(horse).orElse(null);
                        if (mountType == null || !mountType.isEnabled(config)) continue;
                        if (!MountConfig.isGrowthEnabled(config, mountType)) {
                            resetGrowthForHorse(horse);
                            continue;
                        }
                        if (!horse.isValid() || horse.getPassengers().size() > 0) continue;

                        PersistentDataContainer data = horse.getPersistentDataContainer();
                        int stage;

                        if (data.has(growthKey, PersistentDataType.INTEGER)) {
                            stage = data.get(growthKey, PersistentDataType.INTEGER);
                        } else {
                            stage = maxStage;
                            data.set(growthKey, PersistentDataType.INTEGER, stage);
                            horse.setAdult();
                        }

                        if (!horse.isAdult()) {
                            horse.setAgeLock(true);
                        } else {
                            horse.setAgeLock(false);
                        }

                        if (stage >= maxStage) {
                            setScaleSafe(horse, maxScale);
                            continue;
                        }

                        stage++;
                        data.set(growthKey, PersistentDataType.INTEGER, stage);

                        if (stage >= threshold && !horse.isAdult()) {
                            horse.setAdult();
                            horse.setAgeLock(false);
                        }

                        double scaledSize = getScaledSizeForStage(stage, horse.isAdult(), maxScale, threshold);
                        setScaleSafe(horse, scaledSize);
                    }
                }
            }
        }.runTaskTimer(BetterHorses.getInstance(), 0L, intervalTicks);
    }

    private void resetGrowthForHorse(AbstractHorse horse) {
        if (!horse.isAdult()) {
            horse.setAgeLock(false);
        }
        setScaleSafe(horse, 1.0);
    }

    private void setScaleSafe(AbstractHorse horse, double scale) {
        try {
            Attribute attr = Attribute.valueOf("SCALE");
            AttributeInstance instance = horse.getAttribute(attr);
            if (instance != null) {
                instance.setBaseValue(scale);
            }
        } catch (IllegalArgumentException | NoSuchFieldError ignored) {
        }
    }

    private double getScaledSizeForStage(int stage, boolean isAdult, float maxScale, int threshold) {
        if (!isAdult) {
            return 1.0 + ((2.0 - 1.0) / threshold) * stage; // Baby: scale 1.0 → 2.0
        } else {
            int remaining = maxStage - threshold;
            if (remaining <= 0) return maxScale;
            int progress = stage - threshold;
            return 1.0 + ((maxScale - 1.0) / remaining) * progress; // Adult: scale 1.0 → 1.3
        }
    }
}
