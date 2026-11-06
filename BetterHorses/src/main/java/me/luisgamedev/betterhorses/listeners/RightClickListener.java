package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.api.events.BetterHorseSpawnEvent;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.training.TrainingManager;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import me.luisgamedev.betterhorses.utils.HorseItemTransaction;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class RightClickListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent event) {

        if (event.getHand() != EquipmentSlot.HAND || event.useItemInHand() == Event.Result.DENY) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        BetterHorses plugin = BetterHorses.getInstance();
        HorseItemTransaction transaction = HorseItemTransaction.capture(plugin, player);
        if (transaction == null) return;
        ItemStack item = transaction.copy();
        if (item == null || item.getType() == Material.AIR) return;
        LanguageManager lang = BetterHorses.getInstance().getLang();
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        if (!config.getBoolean("settings.allow-rightclick-spawn")) return;

        String configuredItem = config.getString("settings.horse-item", "SADDLE");
        Material expectedMaterial = Material.getMaterial(configuredItem.toUpperCase());
        if (expectedMaterial == null || !expectedMaterial.isItem()) expectedMaterial = Material.SADDLE;

        if (!item.hasItemMeta() || item.getType() != expectedMaterial || !BetterHorsesAPI.isHorseItem(item)) return;
        if (!player.hasPermission(PermissionUtils.SPAWN_RIGHT_CLICK)) {
            event.setCancelled(true);
            return;
        }

        ItemMeta meta = item.getItemMeta();
        TrainingManager.ensureTrainingData(meta.getPersistentDataContainer());
        item.setItemMeta(meta);

        boolean hasStoredChest = meta.getPersistentDataContainer().has(BetterHorseKeys.CHEST_CONTENTS, PersistentDataType.STRING);
        Double health = meta.getPersistentDataContainer().get(BetterHorseKeys.HEALTH, PersistentDataType.DOUBLE);
        Double speed = meta.getPersistentDataContainer().get(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE);
        Double jump = meta.getPersistentDataContainer().get(BetterHorseKeys.JUMP, PersistentDataType.DOUBLE);
        String gender = meta.getPersistentDataContainer().get(BetterHorseKeys.GENDER, PersistentDataType.STRING);
        String mountTypeName = meta.getPersistentDataContainer().get(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING);
        SupportedMountType mountType = SupportedMountType.fromNameOrDefault(mountTypeName);
        String mountName = mountType.getDisplayName(lang, player);
        if (!transaction.matches()) return;

        if (health == null || speed == null || jump == null || gender == null || !mountType.isEnabled(config)) {
            lang.sendFormatted(player, "messages.invalid-horse-data", "%mount%", mountName);
            return;
        }

        if (!transaction.matches()) return;
        AbstractHorse horse = BetterHorsesAPI.toHorse(item, player);
        if (horse == null) {
            if (!HorseItemTransaction.ready(plugin, player)) return;
            if (hasStoredChest) {
                lang.sendFormatted(player, "messages.cant-spawn-chested", "%mount%", mountName);
            } else {
                lang.send(player, "messages.cant-spawn");
            }
            return;
        }

        if (!transaction.matches() || !HorseItemTransaction.owned(horse)) {
            HorseItemTransaction.removeTemporary(plugin, horse);
            return;
        }
        ItemStack snapshot = item.clone();
        snapshot.setAmount(1);
        if (BetterHorsesAPI.callCancellableSpawnEvent(horse, snapshot, BetterHorseSpawnEvent.SpawnCause.ITEM)) {
            HorseItemTransaction.removeTemporary(plugin, horse);
            return;
        }
        if (!HorseItemTransaction.owned(horse) || !horse.isValid() || horse.isDead() || !transaction.matches()
                || horse.getWorld() != player.getWorld()
                || !player.hasPermission(PermissionUtils.SPAWN_RIGHT_CLICK)
                || !plugin.getConfig().getBoolean("settings.allow-rightclick-spawn", true)
                || !mountType.isEnabled(plugin.getConfig()) || event.useItemInHand() == Event.Result.DENY) {
            HorseItemTransaction.removeTemporary(plugin, horse);
            return;
        }
        if (!horse.getPersistentDataContainer().has(BetterHorseKeys.UNDEAD_SKELETON, PersistentDataType.BYTE)) {
            TrainingManager.recalculateAndApplyBonuses(horse);
        }

        item.setAmount(hasStoredChest && item.getAmount() > 1 ? 0 : item.getAmount() - 1);
        if (!transaction.replace(item)) {
            HorseItemTransaction.removeTemporary(plugin, horse);
            return;
        }
        lang.sendFormatted(player, "messages.horse-respawned", "%mount%", mountName);
    }
}
