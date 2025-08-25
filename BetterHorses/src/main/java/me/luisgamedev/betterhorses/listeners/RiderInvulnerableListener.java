package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
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

        if (player.getVehicle() instanceof Horse) {
            event.setCancelled(true);
        }
    }
}
