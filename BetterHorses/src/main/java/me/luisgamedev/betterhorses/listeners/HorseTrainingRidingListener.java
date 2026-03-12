package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.training.TrainingManager;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class HorseTrainingRidingListener implements Listener {

    public void onMove(PlayerMoveEvent event) {
        if(event.isCancelled()) return;
        Player player = event.getPlayer();
        if (!(player.getVehicle() instanceof AbstractHorse horse)) return;
        if (event.getTo() == null || event.getFrom().getWorld() != event.getTo().getWorld()) return;

        double distance = event.getFrom().distance(event.getTo());
        if (distance <= 0.0D) return;

        TrainingManager.addRidingUnits(horse, distance);
    }
}
