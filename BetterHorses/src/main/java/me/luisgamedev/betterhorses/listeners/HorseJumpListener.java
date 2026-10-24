package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.inspection.HorseInspectionManager;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.traits.TraitRegistry;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.HorseJumpEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HorseJumpListener implements Listener, ListenerLifecycle {

    private final Set<AbstractHorse> airborneHorses = new HashSet<>();
    private final Map<AbstractHorse, BukkitTask> landingTasks = new HashMap<>();
    private final NamespacedKey traitKey = new NamespacedKey(BetterHorses.getInstance(), "trait");

    @EventHandler
    public void onHorseJump(HorseJumpEvent event) {
        if (!(event.getEntity() instanceof AbstractHorse horse)) return;
        if (!SupportedMountType.isSupported(horse)) return;
        if (hasHeavenHoovesTrait(horse) && !horse.getPassengers().isEmpty()) {
            Entity rider = horse.getPassengers().get(0);
            if (rider instanceof Player player && PermissionUtils.canUseTrait(player, "heavenhooves")) {
                TraitRegistry.activateHeavenHooves(player, horse, event);
            }
        }
        if (!hasSkyburstTrait(horse)) return;
        if (horse.hasMetadata("SkyburstCandidate")) return;

        horse.setMetadata("SkyburstCandidate", new FixedMetadataValue(BetterHorses.getInstance(), true));
        airborneHorses.add(horse);

        BukkitTask delayedTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!horse.isValid()) {
                    clear(horse);
                    return;
                }

                // Wait for landing
                BukkitTask landingTask = new BukkitRunnable() {
                    int checks;
                    @Override
                    public void run() {
                        if (!horse.isValid() || !me.luisgamedev.betterhorses.utils.WorldAccessPolicy.isEnabled(horse.getWorld())
                                || horse.getPassengers().isEmpty() || ++checks > 300) {
                            clear(horse);
                            cancel();
                            return;
                        }

                        if (horse.isOnGround()) {
                            if (!horse.getPassengers().isEmpty()) {
                                Entity rider = horse.getPassengers().get(0);
                                if (rider instanceof Player player && PermissionUtils.canUseTrait(player, "skyburst")) {
                                    TraitRegistry.activateSkyburst(player, horse);
                                }
                            }
                            clear(horse);
                            cancel();
                        }
                    }
                }.runTaskTimer(BetterHorses.getInstance(), 0L, 2L);
                landingTasks.put(horse, landingTask);
            }
        }.runTaskLater(BetterHorses.getInstance(), 1L);
        landingTasks.put(horse, delayedTask);
    }

    private void clear(AbstractHorse horse) {
        horse.removeMetadata("SkyburstCandidate", BetterHorses.getInstance());
        airborneHorses.remove(horse);
        BukkitTask task = landingTasks.remove(horse);
        if (task != null && !task.isCancelled()) task.cancel();
    }

    private boolean hasSkyburstTrait(AbstractHorse horse) {
        if (!horse.getPersistentDataContainer().has(traitKey, PersistentDataType.STRING)) return false;
        String trait = horse.getPersistentDataContainer().get(traitKey, PersistentDataType.STRING);
        return "skyburst".equalsIgnoreCase(trait) && traitsEnabled("skyburst");
    }

    private boolean hasHeavenHoovesTrait(AbstractHorse horse) {
        if (!horse.getPersistentDataContainer().has(traitKey, PersistentDataType.STRING)) return false;
        String trait = horse.getPersistentDataContainer().get(traitKey, PersistentDataType.STRING);
        return "heavenhooves".equalsIgnoreCase(trait) && traitsEnabled("heavenhooves");
    }

    private boolean traitsEnabled(String trait) {
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        return config.getBoolean("traits.enabled", true)
                && config.getBoolean("traits." + trait + ".enabled", true);
    }

    @EventHandler
    public void onDismount(VehicleExitEvent event) {
        if (HorseInspectionManager.isInspectionDisplay(event.getExited())) return;
        if (event.getVehicle() instanceof AbstractHorse horse) {
            if (SupportedMountType.isSupported(horse)) {
                clear(horse);
            }
        }
    }

    @Override
    public void shutdown() {
        for (AbstractHorse horse : new HashSet<>(airborneHorses)) clear(horse);
        landingTasks.values().forEach(task -> {
            if (!task.isCancelled()) task.cancel();
        });
        landingTasks.clear();
    }
}
