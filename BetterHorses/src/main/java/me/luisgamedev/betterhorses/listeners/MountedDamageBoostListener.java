package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class MountedDamageBoostListener implements Listener {

    @EventHandler
    public void onMountedDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Player player)) return;

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof Horse)) return;

        FileConfiguration config = BetterHorses.getInstance().getConfig();
        double percentage = config.getDouble("settings.mounted-damage-boost.percentage", 0.0);
        if (percentage == 0) {
            return;
        }

        double multiplier = 1 + (percentage / 100.0);
        event.setDamage(event.getDamage() * multiplier);
    }
}
