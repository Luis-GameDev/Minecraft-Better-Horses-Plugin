package me.luisgamedev.betterhorses.growing;

import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;

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
    BetterHorses plugin = BetterHorses.getInstance();

    public HorseGrowthManager(BetterHorses plugin) {
        this.growthKey = new NamespacedKey(plugin, "growth_stage");
    }

    public void start() {

        FileConfiguration config = BetterHorses.getInstance().getConfig();

        if (!config.getBoolean("horse-growth-settings.enabled")) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    Bukkit.getWorlds().stream().filter(WorldAccessPolicy::isEnabled).forEach(world -> world.getEntitiesByClass(AbstractHorse.class).forEach(horse -> {
                        if (!SupportedMountType.isSupported(horse)) return;
                        resetGrowthForHorse(horse);
                    }));
                }
            }.runTask(BetterHorses.getInstance());
            return;
        }

        int minutes = config.getInt("horse-growth-settings.time-until-adult", 60);
        if (minutes <= 0) {
            plugin.getLogger().warning("horse-growth-settings.time-until-adult must be positive; using 60 minutes");
            minutes = 60;
        }
        long intervalTicks;
        try { intervalTicks = Math.max(1L, Math.multiplyExact((long) minutes, 1200L) / maxStage); }
        catch (ArithmeticException exception) { intervalTicks = 7200L; }
        float maxScale = (float) config.getDouble("horse-growth-settings.max-size", 1.3f);
        int threshold = config.getInt("horse-growth-settings.ride-and-breed-threshhold", 7);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    if (!WorldAccessPolicy.isEnabled(world)) continue;
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

                        double scaledSize = GrowthScale.forStage(stage, maxScale);
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
        horse.getPersistentDataContainer().remove(growthKey);
    }

    private void setScaleSafe(AbstractHorse horse, double scale) {
        try {
            Attribute attr = Attribute.valueOf("SCALE");
            AttributeInstance instance = horse.getAttribute(attr);
            if (instance != null) {
                instance.setBaseValue(scale);
            }
        } catch (IllegalArgumentException | NoSuchFieldError e) {
            plugin.debugLog("GROWTH_MANAGER", "SET_SCALE", false, e.getMessage());
        }
    }

}
