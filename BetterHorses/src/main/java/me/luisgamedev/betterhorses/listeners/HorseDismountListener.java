package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.commands.DespawnCommand;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class HorseDismountListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;

        if (!BetterHorses.getInstance().getConfig()
                .getBoolean("settings.allow-automatic-dismount")) return;

        Player player = event.getPlayer();

        if (!player.hasPermission(PermissionUtils.AUTOMATIC_MOUNTING)) return;
        if (!player.hasPermission(PermissionUtils.DESPAWN)) return;

        if (!(player.getVehicle() instanceof AbstractHorse)) return;

        DespawnCommand.despawnHorseToItem(player);
    }
}
