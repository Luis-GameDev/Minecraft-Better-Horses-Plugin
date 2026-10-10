package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
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
import me.luisgamedev.betterhorses.scheduler.ScheduledTaskHandle;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HorseJumpListener implements Listener, ListenerLifecycle {

    private final Set<AbstractHorse> airborneHorses = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ScheduledTaskHandle> landingTasks = new ConcurrentHashMap<>();
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

        final int[] checks = {0};
        ScheduledTaskHandle landingTask = BetterHorses.getInstance().scheduler().entityRepeating(horse, 1L, 2L, scheduled -> {
            if (!horse.isValid() || !me.luisgamedev.betterhorses.utils.WorldAccessPolicy.isEnabled(horse.getWorld())
                    || horse.getPassengers().isEmpty() || ++checks[0] > 300) {
                clear(horse); scheduled.cancel(); return;
            }
            if (horse.isOnGround()) {
                Entity rider = horse.getPassengers().get(0);
                if (rider instanceof Player player && PermissionUtils.canUseTrait(player, "skyburst")) {
                    TraitRegistry.activateSkyburst(player, horse);
                }
                clear(horse); scheduled.cancel();
            }
        }, () -> { airborneHorses.remove(horse); landingTasks.remove(horse.getUniqueId()); });
        landingTasks.put(horse.getUniqueId(), landingTask);
    }

    private void clear(AbstractHorse horse) {
        horse.removeMetadata("SkyburstCandidate", BetterHorses.getInstance());
        airborneHorses.remove(horse);
        ScheduledTaskHandle task = landingTasks.remove(horse.getUniqueId());
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
        if (event.getVehicle() instanceof AbstractHorse horse) {
            if (SupportedMountType.isSupported(horse)) {
                clear(horse);
            }
        }
    }

    @Override
    public void shutdown() {
        for (AbstractHorse horse : Set.copyOf(airborneHorses)) clear(horse);
        landingTasks.values().forEach(task -> {
            if (!task.isCancelled()) task.cancel();
        });
        landingTasks.clear();
    }
}
