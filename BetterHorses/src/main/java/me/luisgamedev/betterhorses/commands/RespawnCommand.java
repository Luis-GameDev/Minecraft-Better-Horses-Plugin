package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.api.events.BetterHorseSpawnEvent;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.HorseItemTransaction;
import org.bukkit.Material;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class RespawnCommand {

    public static boolean spawnHorseFromItem(Player player) {
        BetterHorses plugin = BetterHorses.getInstance();
        LanguageManager lang = plugin.getLang();
        plugin.debugLog("HORSE_RESPAWN", "START", true, "Requested item spawn.");

        HorseItemTransaction transaction = HorseItemTransaction.capture(plugin, player);
        if (transaction == null) return true;
        ItemStack item = transaction.copy();
        String configuredItem = BetterHorses.getInstance().getConfig().getString("settings.horse-item", "SADDLE");

        Material expectedMaterial = Material.getMaterial(configuredItem.toUpperCase());
        if (expectedMaterial == null || !expectedMaterial.isItem()) expectedMaterial = Material.SADDLE;

        if (item == null || item.getType() != expectedMaterial || !item.hasItemMeta() || !BetterHorsesAPI.isHorseItem(item)) {
            lang.send(player, "messages.invalid-item");
            plugin.debugLog("HORSE_RESPAWN", "VALIDATION", false, "Invalid horse item.");
            return true;
        }

        PersistentDataContainer itemData = item.getItemMeta().getPersistentDataContainer();
        Double health = itemData.get(BetterHorseKeys.HEALTH, PersistentDataType.DOUBLE);
        Double speed = itemData.get(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE);
        Double jump = itemData.get(BetterHorseKeys.JUMP, PersistentDataType.DOUBLE);
        String gender = itemData.get(BetterHorseKeys.GENDER, PersistentDataType.STRING);
        String mountTypeName = itemData.get(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING);
        SupportedMountType mountType = SupportedMountType.fromNameOrDefault(mountTypeName);
        String mountName = mountType.getDisplayName(lang, player);
        if (!transaction.matches()) return true;

        if (health == null || speed == null || jump == null || gender == null || !mountType.isEnabled(BetterHorses.getInstance().getConfig())) {
            lang.sendFormatted(player, "messages.invalid-horse-data", "%mount%", mountName);
            plugin.debugLog("HORSE_RESPAWN", "DATA", false, "Invalid horse data.");
            return true;
        }

        if (!transaction.matches()) return true;
        boolean hasStoredChest = itemData.has(BetterHorseKeys.CHEST_CONTENTS, PersistentDataType.STRING);
        AbstractHorse horse = BetterHorsesAPI.toHorse(item, player);
        if (horse == null) {
            if (!HorseItemTransaction.ready(plugin, player)) return true;
            if (hasStoredChest) {
                lang.sendFormatted(player, "messages.cant-spawn-chested", "%mount%", mountName);
            } else {
                lang.send(player, "messages.cant-spawn");
            }
            plugin.debugLog("HORSE_RESPAWN", "SPAWN", false, "Mount spawn failed.");
            return true;
        }

        if (!transaction.matches() || !HorseItemTransaction.owned(horse)) {
            HorseItemTransaction.removeTemporary(plugin, horse);
            return true;
        }
        ItemStack snapshot = item.clone();
        snapshot.setAmount(1);
        if (BetterHorsesAPI.callCancellableSpawnEvent(horse, snapshot, BetterHorseSpawnEvent.SpawnCause.ITEM)) {
            HorseItemTransaction.removeTemporary(plugin, horse);
            return true;
        }
        if (!HorseItemTransaction.owned(horse) || !horse.isValid() || horse.isDead() || !transaction.matches()
                || horse.getWorld() != player.getWorld()
                || !player.hasPermission("betterhorses.spawn.command") || !mountType.isEnabled(plugin.getConfig())) {
            HorseItemTransaction.removeTemporary(plugin, horse);
            return true;
        }
        item.setAmount(hasStoredChest && item.getAmount() > 1 ? 0 : item.getAmount() - 1);
        if (!transaction.replace(item)) {
            HorseItemTransaction.removeTemporary(plugin, horse);
            return true;
        }
        lang.sendFormatted(player, "messages.horse-respawned", "%mount%", mountName);
        plugin.debugLog("HORSE_RESPAWN", "COMPLETE", true, "Spawned " + mountName + ".");
        return true;
    }
}
