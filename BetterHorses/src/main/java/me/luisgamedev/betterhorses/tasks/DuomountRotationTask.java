package me.luisgamedev.betterhorses.tasks;

import org.bukkit.Location;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class DuomountRotationTask implements Runnable {

    private final List<Boat> boats = new ArrayList<>();

    public void registerBoat(Boat boat) {
        if (boat == null || !boat.isValid()) return;
        if (boats.contains(boat)) return;

        boats.add(boat);
    }

    public void unregisterBoat(Boat boat) {
        if (boat == null) return;

        boats.remove(boat);

        if (boat.isValid()) {
            boat.remove();
        }
    }

    @Override
    public void run() {
        boats.removeIf(boat -> {
            if (boat == null || !boat.isValid()) {
                return true;
            }

            Entity vehicle = boat.getVehicle();

            if (!(vehicle instanceof AbstractHorse horse) || !horse.isValid()) {
                boat.remove();
                return true;
            }

            Location horseLocation = horse.getLocation();

            float yaw = horseLocation.getYaw() + 180.0f;

            if (yaw >= 360.0f) {
                yaw -= 360.0f;
            }

            float pitch = horseLocation.getPitch();

            boat.setRotation(yaw, pitch);

            //for (Entity passenger : boat.getPassengers()) {
            //    if (passenger instanceof Player player) {
            //        Location loc = player.getLocation();
            //        loc.setYaw(yaw);
            //        loc.setPitch(pitch);
            //    }
            //}

            return false;
        });
    }
}
