package me.luisgamedev.betterhorses.utils;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.growing.GrowthScale;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** Shared eligibility checks for optional automatic mounting after item spawns. */
public final class AutomaticMounting {
    private AutomaticMounting() { }

    public static boolean tryMount(Player player, AbstractHorse horse) {
        BetterHorses plugin = BetterHorses.getInstance();
        if (plugin == null || !HorseItemTransaction.ready(plugin, player) || !HorseItemTransaction.owned(horse)) return false;
        var config = plugin.getConfig();
        if (!config.getBoolean("settings.allow-automatic-mounting", false)
                || player.isSneaking() || !player.hasPermission(PermissionUtils.AUTOMATIC_MOUNTING)
                || !player.hasPermission(PermissionUtils.MOUNT)
                || !horse.isValid() || horse.isDead() || !horse.isAdult()
                || player.getWorld() != horse.getWorld() || !horse.getPassengers().isEmpty()) return false;
        SupportedMountType type = SupportedMountType.fromEntity(horse).orElse(null);
        if (type == null || !type.isEnabled(config)) return false;
        if (MountConfig.isGrowthEnabled(config, type)) {
            Integer stage = horse.getPersistentDataContainer().get(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER);
            int threshold = GrowthScale.normalize(config.getInt("horse-growth-settings.ride-and-breed-threshhold", 7));
            if (stage != null && GrowthScale.normalize(stage) < threshold) return false;
        }
        // Native mount events continue to enforce ownership and other plugins' cancellation.
        return horse.addPassenger(player);
    }
}
