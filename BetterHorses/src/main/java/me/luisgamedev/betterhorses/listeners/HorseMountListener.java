package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.persistence.PersistentDataType;

public class HorseMountListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onHorseMount(EntityMountEvent event) {
        if (!(event.getMount() instanceof AbstractHorse horse)) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (!player.hasPermission(PermissionUtils.MOUNT)) {
            event.setCancelled(true);
            return;
        }

        if (player.hasPermission("betterhorses.bypass")) return;

        FileConfiguration config = BetterHorses.getInstance().getConfig();

        if (!config.getBoolean("settings.restrict-mounting-to-owner", false)) {
            return;
        }

        var data = horse.getPersistentDataContainer();

        String ownerUUID = data.get(BetterHorseKeys.OWNER, PersistentDataType.STRING);
        if (ownerUUID == null || ownerUUID.isBlank()) return;

        if (!player.getUniqueId().toString().equals(ownerUUID)) {
            event.setCancelled(true);
            BetterHorses.getInstance().getLang().sendFormatted(player, "messages.not-horse-owner");
        }
    }
}
