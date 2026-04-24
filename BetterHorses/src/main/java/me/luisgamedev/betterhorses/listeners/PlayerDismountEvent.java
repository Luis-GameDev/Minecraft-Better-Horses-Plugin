package me.luisgamedev.betterhorses.listeners;

import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.EventListener;

public class PlayerDismountEvent implements Listener {

    @EventHandler
    public void onHorseDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        if (event.getDismounted() instanceof AbstractHorse horse) {
            for (Entity passenger : horse.getPassengers()) {
                if (passenger instanceof ArmorStand armorStand) {
                    armorStand.remove();
                }
            }
        }
    }
}
