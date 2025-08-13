package me.luisgamedev.listeners;

import me.luisgamedev.BetterHorses;
import me.luisgamedev.language.LanguageManager;
import me.luisgamedev.traits.TraitRegistry;
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
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

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
        if (!hasSkyburstTrait(horse)) return;
        if (horse.hasMetadata("SkyburstCandidate")) return;

        horse.setMetadata("SkyburstCandidate", new FixedMetadataValue(BetterHorses.getInstance(), true));
        airborneHorses.add(horse);

        horse.getScheduler().runDelayed(BetterHorses.getInstance(), (ScheduledTask delayed) -> {
            if (!horse.isValid()) {
                clear(horse);
                return;
            }

            horse.getScheduler().runAtFixedRate(BetterHorses.getInstance(), (ScheduledTask repeating) -> {
                if (!horse.isValid()) {
                    clear(horse);
                    repeating.cancel();
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
                    repeating.cancel();
                }
            }, () -> {}, 0L, 2L);
        }, () -> {}, 1L);
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

    @EventHandler
    public void onDismount(VehicleExitEvent event) {
        if (event.getVehicle() instanceof Horse horse) {
            clear(horse);
        }
    }
}
