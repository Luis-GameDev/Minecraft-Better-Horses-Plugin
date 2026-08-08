package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import org.bukkit.NamespacedKey;
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

        NamespacedKey ownerKey = new NamespacedKey(BetterHorses.getInstance(), "owner");
        var data = horse.getPersistentDataContainer();

        if (!data.has(ownerKey, PersistentDataType.STRING)) return;

        String ownerUUID = data.get(ownerKey, PersistentDataType.STRING);
        if (ownerUUID == null) return;

        if (!player.getUniqueId().toString().equals(ownerUUID)) {
            event.setCancelled(true);
            BetterHorses.getInstance().getLang().sendFormatted(player, "messages.not-horse-owner");
        }
    }
}
