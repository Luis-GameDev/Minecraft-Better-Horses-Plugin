package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.training.TrainingManager;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.IllegalPluginAccessException;

public class HorseTrainingBrushingListener implements Listener {

    // Consume the brush gesture only after normal protection priorities have accepted it.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrush(PlayerInteractEntityEvent event) {
        if (event.isCancelled() || !(event.getRightClicked() instanceof AbstractHorse horse)) return;
        BetterHorses plugin = BetterHorses.getInstance();
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        if (!eligible(plugin, player, horse, hand)) return;
        ItemStack original = player.getInventory().getItem(hand).clone();
        int slot = player.getInventory().getHeldItemSlot();
        World world = horse.getWorld();
        event.setCancelled(true); // Suppress native mounting/feeding; protections saw the original gesture.
        Runnable award = () -> {
            if (!eligible(plugin, player, horse, hand) || player.getWorld() != world
                    || player.getInventory().getHeldItemSlot() != slot
                    || !original.equals(player.getInventory().getItem(hand))
                    || player.getLocation().distanceSquared(horse.getLocation()) > 64.0D) return;
            FileConfiguration config = plugin.getConfig();
            long cooldownMillis = Math.max(0L, config.getLong("training.categories.brushing.cooldown-seconds", 180L) * 1000L);
            PersistentDataContainer data = horse.getPersistentDataContainer();
            long now = System.currentTimeMillis();
            long lastBrush = data.getOrDefault(BetterHorseKeys.TRAINING_BRUSH_COOLDOWN, PersistentDataType.LONG, 0L);
            if (cooldownMillis > 0L && lastBrush + cooldownMillis > now) return;
            if (cooldownMillis > 0L) data.set(BetterHorseKeys.TRAINING_BRUSH_COOLDOWN, PersistentDataType.LONG, now);
            TrainingManager.addBrushingUnits(horse, config.getDouble("training.categories.brushing.units-per-use", 1.0));
            horse.getWorld().spawnParticle(Particle.COMPOSTER, horse.getEyeLocation(), 5);
        };
        try {
            plugin.scheduler().entity(horse, 1L, ignored -> award.run(), null);
        } catch (IllegalPluginAccessException stopped) {
            // A shutdown between the accepted interaction and scheduling must not award training.
        }
    }

    private boolean eligible(BetterHorses plugin, Player player, AbstractHorse horse, EquipmentSlot hand) {
        if (plugin == null || !plugin.isEnabled() || player == null || horse == null
                || (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND)) return false;
        if (!Bukkit.isOwnedByCurrentRegion(player) || !Bukkit.isOwnedByCurrentRegion(horse)) return false;
        if (!player.isOnline() || player.isDead() || !horse.isValid() || horse.isDead()
                || player.getWorld() != horse.getWorld() || !WorldAccessPolicy.isEnabled(horse.getWorld())
                || !player.hasPermission(PermissionUtils.TRAINING)) return false;
        FileConfiguration config = plugin.getConfig();
        SupportedMountType mountType = SupportedMountType.fromEntity(horse).orElse(null);
        if (mountType == null || !mountType.isEnabled(config) || !TrainingManager.isTrainingEnabled(config)
                || !TrainingManager.isCategoryEnabled(config, "brushing")) return false;
        ItemStack item = player.getInventory().getItem(hand);
        if (item == null || item.getType() == Material.AIR) return false;
        Material brush = Material.matchMaterial(config.getString("training.categories.brushing.item", "BRUSH"));
        return item.getType() == (brush == null ? Material.BRUSH : brush);
    }
}
