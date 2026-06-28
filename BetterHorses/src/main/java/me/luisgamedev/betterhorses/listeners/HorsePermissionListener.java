package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.utils.PermissionUtils;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerLeashEntityEvent;

public class HorsePermissionListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!(event.getEntity() instanceof AbstractHorse horse)) return;
        if (!SupportedMountType.isSupported(horse)) return;
        if (!(event.getOwner() instanceof Player player)) return;

        if (!player.hasPermission(PermissionUtils.TAME)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof AbstractHorse horse)) return;
        if (!SupportedMountType.isSupported(horse)) return;

        Player player = event.getPlayer();
        if (!player.hasPermission(PermissionUtils.LEASH)) {
            event.setCancelled(true);
        }
    }
}
