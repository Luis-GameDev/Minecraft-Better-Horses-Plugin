package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.traits.TraitRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.HorseJumpEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;

public class HorseJumpListener implements Listener {

    private final Set<Horse> airborneHorses = new HashSet<>();
    private final FileConfiguration config = BetterHorses.getInstance().getConfig();
    private final LanguageManager lang = BetterHorses.getInstance().getLang();
    private final NamespacedKey traitKey = new NamespacedKey(BetterHorses.getInstance(), "trait");

    @EventHandler
    public void onHorseJump(HorseJumpEvent event) {
        if (!(event.getEntity() instanceof Horse horse)) return;
        if (hasHeavenHoovesTrait(horse)) {
            Entity rider = horse.getPassengers().get(0);
            if (rider instanceof Player player) {
                TraitRegistry.activateHeavenHooves(player, horse, event);
            }
        }
        if (!hasSkyburstTrait(horse)) return;
        if (horse.hasMetadata("SkyburstCandidate")) return;

        horse.setMetadata("SkyburstCandidate", new FixedMetadataValue(BetterHorses.getInstance(), true));
        airborneHorses.add(horse);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!horse.isValid()) {
                    clear(horse);
                    return;
                }

                // Wait for landing
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!horse.isValid()) {
                            clear(horse);
                            cancel();
                            return;
                        }

                        if (horse.isOnGround()) {
                            if (!horse.getPassengers().isEmpty()) {
                                Entity rider = horse.getPassengers().get(0);
                                if (rider instanceof Player player) {
                                    TraitRegistry.activateSkyburst(player, horse);
                                }
                            }
                            clear(horse);
                            cancel();
                        }
                    }
                }.runTaskTimer(BetterHorses.getInstance(), 0L, 2L);
            }
        }.runTaskLater(BetterHorses.getInstance(), 1L);
    }

    private void clear(Horse horse) {
        horse.removeMetadata("SkyburstCandidate", BetterHorses.getInstance());
        airborneHorses.remove(horse);
    }

    private boolean hasSkyburstTrait(Horse horse) {
        if (!horse.getPersistentDataContainer().has(traitKey, PersistentDataType.STRING)) return false;
        String trait = horse.getPersistentDataContainer().get(traitKey, PersistentDataType.STRING);
        return "skyburst".equalsIgnoreCase(trait) && config.getBoolean("traits.skyburst.enabled", true);
    }

    private boolean hasHeavenHoovesTrait(Horse horse) {
        if (!horse.getPersistentDataContainer().has(traitKey, PersistentDataType.STRING)) return false;
        String trait = horse.getPersistentDataContainer().get(traitKey, PersistentDataType.STRING);
        return "heavenhooves".equalsIgnoreCase(trait) && config.getBoolean("traits.heavenhooves.enabled", true);
    }

    @EventHandler
    public void onDismount(VehicleExitEvent event) {
        if (event.getVehicle() instanceof Horse horse) {
            clear(horse);
        }
    }
}
