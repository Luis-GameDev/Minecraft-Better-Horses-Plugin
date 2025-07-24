package me.luisgamedev.betterhorses.growing;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

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
                    for (World world : Bukkit.getWorlds()) {
                        for (Horse horse : world.getEntitiesByClass(Horse.class)) {
                            if (!horse.isAdult()) {
                                horse.setAgeLock(false);
                            }
                            try {
                                Attribute attr = Attribute.valueOf("SCALE");
                                AttributeInstance instance = horse.getAttribute(attr);
                                if (instance != null) {
                                    instance.setBaseValue(1.0);
                                }
                            } catch (IllegalArgumentException | NoSuchFieldError ignored) {}
                        }
                    }
                }
            }.runTask(BetterHorses.getInstance());
            return;
        }

        if (!config.getBoolean("horse-growth-settings.enabled")) return;

        int minutes = config.getInt("horse-growth-settings.time-until-adult", 60);
        long intervalTicks = (minutes * 60L * 20L) / maxStage;
        float maxScale = (float) config.getDouble("horse-growth-settings.max-size", 1.3f);
        int threshold = 7;

        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    for (Entity entity : world.getEntitiesByClass(Horse.class)) {
                        if (!(entity instanceof Horse horse)) continue;
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

    private void setScaleSafe(Horse horse, double scale) {
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
