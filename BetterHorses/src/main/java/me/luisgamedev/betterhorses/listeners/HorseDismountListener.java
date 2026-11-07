package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.commands.DespawnCommand;
import me.luisgamedev.betterhorses.utils.HorseItemTransaction;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class HorseDismountListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (event.isCancelled() || !event.isSneaking()) return;
        BetterHorses plugin = BetterHorses.getInstance();
        Player player = event.getPlayer();
        if (!HorseItemTransaction.ready(plugin, player)
                || !plugin.getConfig().getBoolean("settings.allow-automatic-dismount", false)
                || !player.hasPermission(PermissionUtils.AUTOMATIC_DISMOUNTING)
                || !player.hasPermission(PermissionUtils.DESPAWN)) return;
        if (!(player.getVehicle() instanceof AbstractHorse horse) || !HorseItemTransaction.owned(horse)) return;
        if (SupportedMountType.fromEntity(horse).filter(type -> type.isEnabled(plugin.getConfig())).isEmpty()) return;
        DespawnCommand.despawnHorseToItem(player);
    }
}
