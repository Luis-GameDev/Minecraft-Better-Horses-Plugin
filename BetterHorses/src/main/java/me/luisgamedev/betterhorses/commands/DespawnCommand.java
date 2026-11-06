package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.utils.HorseItemTransaction;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class DespawnCommand {
    public static boolean despawnHorseToItem(Player player) {
        BetterHorses plugin = BetterHorses.getInstance();
        if (!HorseItemTransaction.ready(plugin, player)) return true;
        LanguageManager lang = plugin.getLang();
        if (!(player.getVehicle() instanceof AbstractHorse horse) || !HorseItemTransaction.owned(horse)) {
            lang.send(player, "messages.invalid-vehicle");
            return true;
        }
        SupportedMountType mountType = SupportedMountType.fromEntity(horse)
                .filter(type -> type.isEnabled(plugin.getConfig())).orElse(null);
        if (mountType == null) {
            lang.send(player, "messages.invalid-vehicle");
            return true;
        }
        String mountName = mountType.getDisplayName(lang, player);
        if (!current(plugin, player, horse, mountType)) return true;
        if (!ownerAllowed(plugin, player, horse)) {
            lang.sendFormatted(player, "messages.not-horse-owner", "%mount%", mountName);
            return true;
        }
        if (!chestAllowed(plugin, horse)) {
            lang.sendFormatted(player, "messages.cant-despawn-chested", "%mount%", mountName);
            return true;
        }

        ItemStack item = BetterHorsesAPI.toItem(horse, player);
        if (!current(plugin, player, horse, mountType)) return true;
        if (item == null) {
            lang.send(player, "messages.cant-despawn");
            return true;
        }
        boolean wasLeashed = horse.isLeashed();
        Location location = horse.getLocation().clone();
        if (BetterHorsesAPI.callDespawnEvent(horse, item)) return true;
        if (!current(plugin, player, horse, mountType) || !ownerAllowed(plugin, player, horse)
                || !chestAllowed(plugin, horse) || !BetterHorsesAPI.isHorseItem(item)) return true;

        // ItemSpawnEvent is cancellable. Confirm delivery before removing the only live source.
        HorseItemTransaction.Delivery delivery = HorseItemTransaction.stage(plugin, player, item);
        if (delivery == null) {
            if (HorseItemTransaction.ready(plugin, player)) lang.send(player, "messages.cant-despawn");
            return true;
        }
        if (!current(plugin, player, horse, mountType) || !ownerAllowed(plugin, player, horse)
                || !chestAllowed(plugin, horse)) {
            delivery.rollback();
            return true;
        }
        try {
            horse.remove();
        } catch (RuntimeException failure) {
            delivery.rollback();
            throw failure;
        }
        if (HorseItemTransaction.owned(horse) && horse.isValid()) {
            delivery.rollback();
            if (HorseItemTransaction.ready(plugin, player)) lang.send(player, "messages.cant-despawn");
            return true;
        }
        delivery.commit();
        if (wasLeashed && HorseItemTransaction.owned(location)) {
            location.getWorld().dropItemNaturally(location, new ItemStack(Material.LEAD));
        }
        if (HorseItemTransaction.ready(plugin, player)) {
            lang.sendFormatted(player, "messages.horse-despawned", "%mount%", mountName);
        }
        plugin.debugLog("HORSE_DESPAWN", "COMPLETE", true, "Despawned " + mountName + ".");
        return true;
    }

    private static boolean current(BetterHorses plugin, Player player, AbstractHorse horse, SupportedMountType type) {
        return HorseItemTransaction.ready(plugin, player) && HorseItemTransaction.owned(horse)
                && horse.isValid() && !horse.isDead() && player.getVehicle() == horse
                && player.getWorld() == horse.getWorld() && player.hasPermission("betterhorses.despawn")
                && type.isEnabled(plugin.getConfig());
    }

    private static boolean ownerAllowed(BetterHorses plugin, Player player, AbstractHorse horse) {
        if (!plugin.getConfig().getBoolean("settings.restrict-mounting-to-owner", false)) return true;
        String owner = horse.getPersistentDataContainer().get(BetterHorseKeys.OWNER, PersistentDataType.STRING);
        if (owner == null || owner.isBlank()) {
            AnimalTamer nativeOwner = horse.getOwner();
            owner = nativeOwner == null ? null : nativeOwner.getUniqueId().toString();
        }
        return owner == null || owner.equals(player.getUniqueId().toString());
    }

    private static boolean chestAllowed(BetterHorses plugin, AbstractHorse horse) {
        return !(horse instanceof ChestedHorse chest) || !chest.isCarryingChest()
                || plugin.getConfig().getBoolean("settings.allow-chested-mount-despawn", false);
    }
}
