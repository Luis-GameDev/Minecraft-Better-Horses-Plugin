package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.training.TrainingManager;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class HorseFeedListener implements Listener {

    @EventHandler
    public void onHorseFeed(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Horse horse)) return;

        final Player player = event.getPlayer();
        final ItemStack item = player.getInventory().getItem(event.getHand());
        if (item == null || item.getType() == Material.AIR) return;

        final Material type = item.getType();
        final boolean isFood =
                type == Material.GOLDEN_APPLE ||
                        type == Material.ENCHANTED_GOLDEN_APPLE ||
                        type == Material.SUGAR ||
                        type == Material.HAY_BLOCK ||
                        type == Material.WHEAT ||
                        type == Material.APPLE ||
                        type == Material.GOLDEN_CARROT ||
                        type == Material.CARROT;

        if (!isFood) return;

        if (!player.hasPermission(PermissionUtils.FEED)) {
            event.setCancelled(true);
            return;
        }

        final double feedingTrainingValue = TrainingManager.getFoodTrainingValue(type);
        final FileConfiguration config = BetterHorses.getInstance().getConfig();

        if (!horse.isAdult() && config.getBoolean("horse-growth-settings.enabled", false)) {
            event.setCancelled(true);
            return;
        }

        if (!horse.isTamed()) return;

        final long now = System.currentTimeMillis();
        final PersistentDataContainer data = horse.getPersistentDataContainer();

        final long cooldownSeconds = config.getLong("settings.breeding-cooldown", 0L);
        if (cooldownSeconds > 0L && !player.hasPermission(PermissionUtils.COOLDOWN_BYPASS)) {
            final long cooldownMillis = cooldownSeconds * 1000L;

            if (config.getBoolean("settings.male-ignore-cooldown", false)) {
                final String gender = data.getOrDefault(BetterHorseKeys.GENDER, PersistentDataType.STRING, "UNKNOWN");
                if ("male".equalsIgnoreCase(gender)) {
                    horse.setAge(0);
                    tryAddFeedingTraining(horse, data, config, player.hasPermission(PermissionUtils.TRAINING) ? feedingTrainingValue : 0.0, now);
                    return;
                }
            }

            final long lastBreed = data.getOrDefault(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG, 0L);
            horse.setAge(0);

            if (lastBreed > 0L) {
                final long elapsed = now - lastBreed;
                final long remaining = cooldownMillis - elapsed;

                if (remaining > 0L) {
                    event.setCancelled(true);
                } else {
                    data.remove(BetterHorseKeys.COOLDOWN);
                }
            }
        }

        if (!event.isCancelled()) {
            tryAddFeedingTraining(horse, data, config, player.hasPermission(PermissionUtils.TRAINING) ? feedingTrainingValue : 0.0, now);
        }
    }

    private void tryAddFeedingTraining(Horse horse, PersistentDataContainer data, FileConfiguration config, double feedingTrainingValue, long now) {
        if (feedingTrainingValue <= 0) {
            return;
        }

        if (TrainingManager.isTrainingEnabled(config) && TrainingManager.isCategoryEnabled(config, "feeding")) {
            final long trainingCooldownMillis = Math.max(0L,
                    config.getLong("training.categories.feeding.cooldown-seconds", 0L) * 1000L);
            final long lastFeedTraining = data.getOrDefault(BetterHorseKeys.TRAINING_FEED_COOLDOWN, PersistentDataType.LONG, 0L);

            if (trainingCooldownMillis == 0L || lastFeedTraining + trainingCooldownMillis > now) {
                return;
            }

            data.set(BetterHorseKeys.TRAINING_FEED_COOLDOWN, PersistentDataType.LONG, now);
        }

        TrainingManager.addFeedingUnits(horse, feedingTrainingValue);
        horse.getWorld().spawnParticle(Particle.COMPOSTER, horse.getEyeLocation(), 5);
    }
}
