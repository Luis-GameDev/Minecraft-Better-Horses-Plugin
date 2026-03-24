package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.api.events.BetterHorseSpawnEvent;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Material;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class RespawnCommand {

    public static boolean spawnHorseFromItem(Player player) {
        BetterHorses plugin = BetterHorses.getInstance();
        LanguageManager lang = plugin.getLang();
        plugin.debugLog("HORSE_RESPAWN", "START", true, "Player " + player.getName() + " requested item spawn.");

        ItemStack item = player.getInventory().getItemInMainHand();
        String configuredItem = BetterHorses.getInstance().getConfig().getString("settings.horse-item", "SADDLE");

        Material expectedMaterial = Material.getMaterial(configuredItem.toUpperCase());
        if (expectedMaterial == null || !expectedMaterial.isItem()) expectedMaterial = Material.SADDLE;

        if (item == null || item.getType() != expectedMaterial || !item.hasItemMeta()) {
            player.sendMessage(lang.get("messages.invalid-item"));
            plugin.debugLog("HORSE_RESPAWN", "VALIDATION", false, "Invalid item used by " + player.getName() + ".");
            return true;
        }

        Double health = item.getItemMeta().getPersistentDataContainer().get(BetterHorseKeys.HEALTH, PersistentDataType.DOUBLE);
        Double speed = item.getItemMeta().getPersistentDataContainer().get(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE);
        Double jump = item.getItemMeta().getPersistentDataContainer().get(BetterHorseKeys.JUMP, PersistentDataType.DOUBLE);
        String gender = item.getItemMeta().getPersistentDataContainer().get(BetterHorseKeys.GENDER, PersistentDataType.STRING);
        String mountTypeName = item.getItemMeta().getPersistentDataContainer().get(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING);
        SupportedMountType mountType = SupportedMountType.fromNameOrDefault(mountTypeName);
        String mountName = mountType.getDisplayName(lang);

        if (health == null || speed == null || jump == null || gender == null || !mountType.isEnabled(BetterHorses.getInstance().getConfig())) {
            player.sendMessage(lang.getFormatted("messages.invalid-horse-data", "%mount%", mountName));
            plugin.debugLog("HORSE_RESPAWN", "DATA", false, "Invalid horse data for " + player.getName() + ".");
            return true;
        }

        AbstractHorse horse = BetterHorsesAPI.toHorse(item, player);
        if (horse == null) {
            player.sendMessage(lang.get("messages.cant-spawn"));
            plugin.debugLog("HORSE_RESPAWN", "SPAWN", false, "Mount spawn failed for " + player.getName() + ".");
            return true;
        }

        item.setAmount(item.getAmount() - 1);
        BetterHorsesAPI.callSpawnEvent(horse, item, BetterHorseSpawnEvent.SpawnCause.ITEM);
        player.sendMessage(lang.getFormatted("messages.horse-respawned", "%mount%", mountName));
        plugin.debugLog("HORSE_RESPAWN", "COMPLETE", true, "Player " + player.getName() + " spawned " + mountName + ".");
        return true;
    }
}
