package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public class RiderInvulnerableListener implements Listener {

    @EventHandler
    public void onRiderDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();

        if (!(entity instanceof Player player)) return;

        FileConfiguration config = BetterHorses.getInstance().getConfig();
        if (!config.getBoolean("settings.rider-invulnerable", false)) return;

        if (player.getVehicle() instanceof AbstractHorse mount && SupportedMountType.isSupported(mount)) {
            event.setCancelled(true);
        }
    }
}
