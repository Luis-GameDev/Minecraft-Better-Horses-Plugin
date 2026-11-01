package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.training.TrainingManager;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;

public class HorseTrainingRidingListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Observe the accepted destination after protection/anticheat listeners.
        // Teleports are not distance travelled while riding.
        if (event.isCancelled() || event instanceof PlayerTeleportEvent) return;
        Player player = event.getPlayer();
        if (!org.bukkit.Bukkit.isOwnedByCurrentRegion(player)) return;
        if (!player.hasPermission(PermissionUtils.TRAINING)) return;
        if (!(player.getVehicle() instanceof AbstractHorse horse) || !org.bukkit.Bukkit.isOwnedByCurrentRegion(horse)) return;
        var config = BetterHorses.getInstance().getConfig();
        SupportedMountType type = SupportedMountType.fromEntity(horse).orElse(null);
        if (!TrainingManager.isTrainingEnabled(config) || !TrainingManager.isCategoryEnabled(config, "riding")
                || type == null || !type.isEnabled(config) || !WorldAccessPolicy.isEnabled(horse.getWorld())) return;
        if (event.getTo() == null || event.getFrom().getWorld() != event.getTo().getWorld()) return;

        double distance = event.getFrom().distance(event.getTo());
        if (!Double.isFinite(distance) || distance <= 0.0D) return;

        TrainingManager.addRidingUnits(horse, distance);
    }
}
