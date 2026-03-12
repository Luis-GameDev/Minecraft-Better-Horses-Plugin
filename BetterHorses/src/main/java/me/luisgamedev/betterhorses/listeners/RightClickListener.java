package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.api.events.BetterHorseSpawnEvent;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.training.TrainingManager;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class RightClickListener implements Listener {

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {

        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;
        if (!player.hasPermission("betterhorses.base")) return;

        LanguageManager lang = BetterHorses.getInstance().getLang();
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        if (!config.getBoolean("settings.allow-rightclick-spawn")) return;

        String configuredItem = config.getString("settings.horse-item", "SADDLE");
        Material expectedMaterial = Material.getMaterial(configuredItem.toUpperCase());
        if (expectedMaterial == null || !expectedMaterial.isItem()) expectedMaterial = Material.SADDLE;

        if (!item.hasItemMeta() || item.getType() != expectedMaterial) return;

        ItemMeta meta = item.getItemMeta();
        TrainingManager.ensureTrainingData(meta.getPersistentDataContainer());
        item.setItemMeta(meta);

        Double health = meta.getPersistentDataContainer().get(BetterHorseKeys.HEALTH, PersistentDataType.DOUBLE);
        Double speed = meta.getPersistentDataContainer().get(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE);
        Double jump = meta.getPersistentDataContainer().get(BetterHorseKeys.JUMP, PersistentDataType.DOUBLE);
        String gender = meta.getPersistentDataContainer().get(BetterHorseKeys.GENDER, PersistentDataType.STRING);
        String mountTypeName = meta.getPersistentDataContainer().get(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING);
        SupportedMountType mountType = SupportedMountType.fromNameOrDefault(mountTypeName);
        String mountName = mountType.getDisplayName(lang);

        if (health == null || speed == null || jump == null || gender == null || !mountType.isEnabled(config)) {
            player.sendMessage(lang.getFormatted("messages.invalid-horse-data", "%mount%", mountName));
            return;
        }

        AbstractHorse horse = BetterHorsesAPI.toHorse(item, player);
        if (horse == null) {
            player.sendMessage(lang.get("messages.cant-spawn"));
            return;
        }

        BetterHorsesAPI.callSpawnEvent(horse, item.clone(), BetterHorseSpawnEvent.SpawnCause.ITEM);
        TrainingManager.recalculateAndApplyBonuses(horse);

        item.setAmount(item.getAmount() - 1);
        player.sendMessage(lang.getFormatted("messages.horse-respawned", "%mount%", mountName));
    }
}
