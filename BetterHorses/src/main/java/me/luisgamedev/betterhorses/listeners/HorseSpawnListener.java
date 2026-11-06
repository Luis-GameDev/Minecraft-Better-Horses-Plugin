package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.api.events.BetterHorseSpawnEvent;
import me.luisgamedev.betterhorses.growing.GrowthScale;
import me.luisgamedev.betterhorses.growing.HorseSize;
import me.luisgamedev.betterhorses.training.TrainingManager;
import me.luisgamedev.betterhorses.utils.MountConfig;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

public class HorseSpawnListener implements Listener {

    private static final String[] GENDERS = {"male", "female"};
    private final Random random = new Random();
    private final NamespacedKey genderKey = new NamespacedKey(JavaPlugin.getPlugin(BetterHorses.class), "gender");
    private final NamespacedKey growthKey = new NamespacedKey(JavaPlugin.getPlugin(BetterHorses.class), "growth_stage");

    @EventHandler
    public void onHorseSpawn(CreatureSpawnEvent event) {
        BetterHorses plugin = BetterHorses.getInstance();
        if (!(event.getEntity() instanceof AbstractHorse horse)) return;
        SupportedMountType mountType = SupportedMountType.fromEntity(horse).orElse(null);
        if (mountType == null) return;
        makeSkeletonHorseMountableIfEnabled(plugin, horse);
        if (!mountType.isEnabled(plugin.getConfig())) return;
        HorseSize.applyWithoutGrowth(horse, plugin.getConfig());
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        plugin.debugLog("HORSE_SPAWN", "NATURAL", true, "Natural mount spawn detected for " + horse.getUniqueId() + ".");

        // Set gender
        String gender = GENDERS[random.nextInt(GENDERS.length)];
        PersistentDataContainer data = horse.getPersistentDataContainer();
        data.set(genderKey, PersistentDataType.STRING, gender);

        int stage = 10;

        boolean growthEnabled = MountConfig.isGrowthEnabled(BetterHorses.getInstance().getConfig(), mountType);
        if (growthEnabled) {
            // Set random growth stage
            stage = 1 + random.nextInt(10);

            // Set to adult if threshold met
            int threshold = Math.max(1, Math.min(10,
                    plugin.getConfig().getInt("horse-growth-settings.ride-and-breed-threshhold", 7)));
            if (stage >= threshold && !horse.isAdult()) {
                horse.setAdult();
                horse.setAgeLock(false);
            } else if (stage < threshold) {
                horse.setBaby();
                horse.setAgeLock(true);
            } else {
                horse.setAgeLock(false);
            }

            // Apply after the age transition to account for vanilla baby scaling.
            double maxScale = plugin.getConfig().getDouble("horse-growth-settings.max-size", 1.3);
            HorseSize.apply(horse, GrowthScale.forStageUnclamped(stage, maxScale, horse.isAdult(),
                    GrowthScale.babyScale(mountType.name(), Bukkit.getBukkitVersion())), plugin.getConfig());
            data.set(growthKey, PersistentDataType.INTEGER, stage);
        }

        TrainingManager.ensureBaseStats(horse);
        TrainingManager.recalculateAndApplyBonuses(horse);
        if (BetterHorsesAPI.callCancellableSpawnEvent(horse, null, BetterHorseSpawnEvent.SpawnCause.NATURAL)) {
            event.setCancelled(true);
            return;
        }
        plugin.debugLog("HORSE_SPAWN", "COMPLETE", true, "Initialized natural mount " + horse.getUniqueId() + " stage=" + stage + ".");
    }

    private void makeSkeletonHorseMountableIfEnabled(BetterHorses plugin, AbstractHorse horse) {
        if (!(horse instanceof SkeletonHorse)) return;
        if (!plugin.getConfig().getBoolean("settings.mountable-skeleton-horses", true)) return;

        horse.setTamed(true);
        plugin.debugLog("HORSE_SPAWN", "SKELETON_MOUNTABLE", true,
                "Marked skeleton horse " + horse.getUniqueId() + " as tamed for vanilla mounting.");
    }
}
