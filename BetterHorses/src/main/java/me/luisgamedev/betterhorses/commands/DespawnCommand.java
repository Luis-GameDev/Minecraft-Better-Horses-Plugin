package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Material;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class DespawnCommand {

    public static boolean despawnHorseToItem(Player player) {
        BetterHorses plugin = BetterHorses.getInstance();
        LanguageManager lang = plugin.getLang();
        plugin.debugLog("HORSE_DESPAWN", "START", true, "Player " + player.getName() + " requested despawn.");

        if (!(player.getVehicle() instanceof AbstractHorse horse)) {
            lang.send(player, "messages.invalid-vehicle");
            plugin.debugLog("HORSE_DESPAWN", "VALIDATION", false, "Player " + player.getName() + " is not riding a supported mount.");
            return true;
        }

        SupportedMountType mountType = SupportedMountType.fromEntity(horse)
                .filter(type -> type.isEnabled(BetterHorses.getInstance().getConfig()))
                .orElse(null);

        if (mountType == null) {
            lang.send(player, "messages.invalid-vehicle");
            plugin.debugLog("HORSE_DESPAWN", "MOUNT_TYPE", false, "Mount type disabled or unsupported for player " + player.getName() + ".");
            return true;
        }

        String mountName = mountType.getDisplayName(lang, player);

        PersistentDataContainer data = horse.getPersistentDataContainer();
        String storedOwner = data.get(BetterHorseKeys.OWNER, PersistentDataType.STRING);
        boolean ownershipRequired = mountType != SupportedMountType.CAMEL || storedOwner != null;
        boolean isOwner = storedOwner != null && storedOwner.equals(player.getUniqueId().toString());

        if (storedOwner == null) {
            AnimalTamer owner = horse.getOwner();
            isOwner = horse.isTamed() && owner != null && owner.getUniqueId().equals(player.getUniqueId());
        }

        if (ownershipRequired && !isOwner) {
            lang.sendFormatted(player, "messages.not-horse-owner", "%mount%", mountName);
            plugin.debugLog("HORSE_DESPAWN", "OWNERSHIP", false, "Player " + player.getName() + " is not owner of " + horse.getUniqueId() + ".");
            return true;
        }

        ItemStack item = BetterHorsesAPI.toItem(horse, player);
        if (item == null) {
            lang.send(player, "messages.cant-despawn");
            plugin.debugLog("HORSE_DESPAWN", "ITEM", false, "Failed converting horse to item for " + horse.getUniqueId() + ".");
            return true;
        }

        boolean wasLeashed = horse.isLeashed();

        if (BetterHorsesAPI.callDespawnEvent(horse, item)) {
            plugin.debugLog("HORSE_DESPAWN", "EVENT", false, "Despawn was cancelled for horse " + horse.getUniqueId() + ".");
            return true;
        }

        horse.remove();

        if (horse.isValid()) {
            lang.send(player, "messages.cant-despawn");
            plugin.debugLog("HORSE_DESPAWN", "REMOVE", false, "Horse remained valid after remove call: " + horse.getUniqueId());
            return true;
        }

        if (wasLeashed) {
            horse.getWorld().dropItemNaturally(horse.getLocation(), new ItemStack(Material.LEAD, 1));
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), item);
        } else {
            player.getInventory().addItem(item);
        }

        lang.sendFormatted(player, "messages.horse-despawned", "%mount%", mountName);
        plugin.debugLog("HORSE_DESPAWN", "COMPLETE", true, "Player " + player.getName() + " despawned " + mountName + ".");
        return true;
    }
}
