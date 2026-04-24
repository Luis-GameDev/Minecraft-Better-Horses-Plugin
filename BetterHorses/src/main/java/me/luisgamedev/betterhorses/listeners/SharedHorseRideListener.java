package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SharedHorseRideListener implements Listener {

    private final BetterHorses plugin;
    private final Map<UUID, SharedSeat> sharedSeats = new HashMap<>();
    private final BukkitTask seatUpdateTask;

    public SharedHorseRideListener(BetterHorses plugin) {
        this.plugin = plugin;
        this.seatUpdateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateSeats, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSecondPlayerMount(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof AbstractHorse horse)) {
            return;
        }

        Player secondRider = event.getPlayer();
        if (!horse.isValid() || horse.isDead()) {
            return;
        }

        if (isMountedOnSharedSeat(secondRider)) {
            return;
        }

        if (sharedSeats.containsKey(horse.getUniqueId())) {
            return;
        }

        Player primaryRider = getPrimaryRider(horse);
        if (primaryRider == null || primaryRider.equals(secondRider)) {
            return;
        }

        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("settings.shared-riding.enabled", false)) {
            return;
        }

        event.setCancelled(true);

        Location spawnLocation = horse.getLocation().clone();
        ArmorStand seat = (ArmorStand) horse.getWorld().spawnEntity(spawnLocation, EntityType.ARMOR_STAND);
        seat.setInvisible(true);
        seat.setMarker(true);
        seat.setSmall(true);
        seat.setGravity(false);
        seat.setInvulnerable(true);
        seat.setPersistent(false);
        seat.setCollidable(false);

        horse.addPassenger(seat);

        SharedSeat sharedSeat = new SharedSeat(horse.getUniqueId(), seat.getUniqueId(), secondRider.getUniqueId());
        sharedSeats.put(horse.getUniqueId(), sharedSeat);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!seat.isValid() || !horse.isValid() || !secondRider.isOnline()) {
                clearSharedSeat(horse.getUniqueId());
                return;
            }
            if (!seat.addPassenger(secondRider)) {
                clearSharedSeat(horse.getUniqueId());
            }
        });
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        Entity vehicle = event.getDismounted();
        Entity entity = event.getEntity();

        if (vehicle instanceof ArmorStand seat) {
            UUID horseId = getHorseIdBySeat(seat.getUniqueId());
            if (horseId != null) {
                clearSharedSeat(horseId);
            }
            return;
        }

        if (vehicle instanceof AbstractHorse horse && entity instanceof Player) {
            if (sharedSeats.containsKey(horse.getUniqueId())) {
                clearSharedSeat(horse.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof AbstractHorse horse) {
            clearSharedSeat(horse.getUniqueId());
        }
    }

    @EventHandler
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof AbstractHorse horse) {
            clearSharedSeat(horse.getUniqueId());
        }
        if (event.getEntity() instanceof ArmorStand seat) {
            UUID horseId = getHorseIdBySeat(seat.getUniqueId());
            if (horseId != null) {
                clearSharedSeat(horseId);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        for (SharedSeat sharedSeat : new ArrayList<>(sharedSeats.values())) {
            if (sharedSeat.secondaryPassengerId().equals(player.getUniqueId())) {
                clearSharedSeat(sharedSeat.horseId());
                continue;
            }

            Entity horseEntity = Bukkit.getEntity(sharedSeat.horseId());
            if (!(horseEntity instanceof AbstractHorse horse)) {
                clearSharedSeat(sharedSeat.horseId());
                continue;
            }

            for (Entity passenger : horse.getPassengers()) {
                if (passenger.getUniqueId().equals(player.getUniqueId())) {
                    clearSharedSeat(sharedSeat.horseId());
                    break;
                }
            }
        }
    }

    private void updateSeats() {
        FileConfiguration config = plugin.getConfig();
        double backwardOffset = config.getDouble("settings.shared-riding.backward-offset", 0.7D);
        double verticalOffset = config.getDouble("settings.shared-riding.vertical-offset", 0.35D);

        List<UUID> toClear = new ArrayList<>();

        for (SharedSeat seatData : sharedSeats.values()) {
            Entity horseEntity = Bukkit.getEntity(seatData.horseId());
            Entity seatEntity = Bukkit.getEntity(seatData.seatEntityId());
            Entity secondPassengerEntity = Bukkit.getEntity(seatData.secondaryPassengerId());

            if (!(horseEntity instanceof AbstractHorse horse)
                    || !(seatEntity instanceof ArmorStand seat)
                    || !(secondPassengerEntity instanceof Player secondPassenger)
                    || !horse.isValid() || horse.isDead()
                    || !seat.isValid()
                    || !secondPassenger.isOnline() || secondPassenger.isDead()) {
                toClear.add(seatData.horseId());
                continue;
            }

            Player primaryRider = getPrimaryRider(horse);
            if (primaryRider == null) {
                toClear.add(seatData.horseId());
                continue;
            }

            if (!horse.getPassengers().contains(seat)) {
                horse.addPassenger(seat);
            }

            Vector backward = horse.getLocation().getDirection();
            backward.setY(0.0D);
            if (backward.lengthSquared() < 0.001D) {
                backward = new Vector(0, 0, 1);
            }
            backward.normalize().multiply(-backwardOffset);

            Location target = horse.getLocation().clone().add(backward).add(0.0D, verticalOffset, 0.0D);
            seat.teleport(target);

            if (!seat.getPassengers().contains(secondPassenger)) {
                seat.addPassenger(secondPassenger);
            }
        }

        for (UUID horseId : toClear) {
            clearSharedSeat(horseId);
        }
    }

    private Player getPrimaryRider(AbstractHorse horse) {
        for (Entity passenger : horse.getPassengers()) {
            if (passenger instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private boolean isMountedOnSharedSeat(Player player) {
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof ArmorStand armorStand)) {
            return false;
        }
        return getHorseIdBySeat(armorStand.getUniqueId()) != null;
    }

    private UUID getHorseIdBySeat(UUID seatId) {
        for (SharedSeat seatData : sharedSeats.values()) {
            if (seatData.seatEntityId().equals(seatId)) {
                return seatData.horseId();
            }
        }
        return null;
    }

    private void clearSharedSeat(UUID horseId) {
        SharedSeat seatData = sharedSeats.remove(horseId);
        if (seatData == null) {
            return;
        }

        Entity seatEntity = Bukkit.getEntity(seatData.seatEntityId());
        if (seatEntity != null && seatEntity.isValid()) {
            seatEntity.remove();
        }
    }

    public void shutdown() {
        for (UUID horseId : new ArrayList<>(sharedSeats.keySet())) {
            clearSharedSeat(horseId);
        }
        seatUpdateTask.cancel();
    }

    private record SharedSeat(UUID horseId, UUID seatEntityId, UUID secondaryPassengerId) {}
}
