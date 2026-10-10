package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.training.TrainingManager;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;

public class HorseTrainingRidingListener implements Listener {

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if(event.isCancelled()) return;
        Player player = event.getPlayer();
        if (!player.hasPermission(PermissionUtils.TRAINING)) return;
        if (!(player.getVehicle() instanceof AbstractHorse horse)) return;
        var config = BetterHorses.getInstance().getConfig();
        SupportedMountType type = SupportedMountType.fromEntity(horse).orElse(null);
        if (!TrainingManager.isTrainingEnabled(config) || !TrainingManager.isCategoryEnabled(config, "riding")
                || type == null || !type.isEnabled(config) || !WorldAccessPolicy.isEnabled(horse.getWorld())) return;
        if (event.getTo() == null || event.getFrom().getWorld() != event.getTo().getWorld()) return;

        double distance = event.getFrom().distance(event.getTo());
        if (distance <= 0.0D) return;

        TrainingManager.addRidingUnits(horse, distance);
    }
}
