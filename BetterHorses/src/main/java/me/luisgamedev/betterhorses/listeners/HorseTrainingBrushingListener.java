package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.training.TrainingManager;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class HorseTrainingBrushingListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onBrush(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof AbstractHorse horse)) return;

        FileConfiguration config = BetterHorses.getInstance().getConfig();
        if (!TrainingManager.isTrainingEnabled(config) || !TrainingManager.isCategoryEnabled(config, "brushing")) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item == null || item.getType() == Material.AIR) return;

        Material brushMaterial = Material.matchMaterial(config.getString("training.categories.brushing.item", "BRUSH"));
        if (brushMaterial == null) brushMaterial = Material.BRUSH;
        if (item.getType() != brushMaterial) return;
        event.setCancelled(true);

        long cooldownMillis = Math.max(0L, config.getLong("training.categories.brushing.cooldown-seconds", 180L) * 1000L);
        PersistentDataContainer data = horse.getPersistentDataContainer();
        long now = System.currentTimeMillis();
        long lastBrush = data.getOrDefault(BetterHorseKeys.TRAINING_BRUSH_COOLDOWN, PersistentDataType.LONG, 0L);

        if (cooldownMillis > 0L && now - lastBrush < cooldownMillis) {
            return;
        }
        horse.getWorld().spawnParticle(Particle.COMPOSTER, horse.getLocation().add(0, 1, 0), 5);

        data.set(BetterHorseKeys.TRAINING_BRUSH_COOLDOWN, PersistentDataType.LONG, now);
        double units = config.getDouble("training.categories.brushing.units-per-use", 1.0);
        TrainingManager.addBrushingUnits(horse, units);
    }
}
