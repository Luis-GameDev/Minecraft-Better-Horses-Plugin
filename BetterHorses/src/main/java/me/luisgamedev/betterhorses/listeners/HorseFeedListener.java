package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.training.TrainingManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

    private final NamespacedKey cooldownKey = new NamespacedKey(BetterHorses.getInstance(), "cooldown");
    private final NamespacedKey genderKey   = new NamespacedKey(BetterHorses.getInstance(), "gender");

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

        final double feedingTrainingValue = TrainingManager.getFoodTrainingValue(type);

        final FileConfiguration config = BetterHorses.getInstance().getConfig();

        if (!horse.isAdult() && config.getBoolean("horse-growth-settings.enabled", false)) {
            event.setCancelled(true);
            return;
        }

        if (!horse.isTamed()) return;

        final long cooldownSeconds = config.getLong("settings.breeding-cooldown", 0L);
        if (cooldownSeconds <= 0L) {
            if (feedingTrainingValue > 0) {
                TrainingManager.addFeedingUnits(horse, feedingTrainingValue);
            }
            return;
        }

        final long cooldownMillis = cooldownSeconds * 1000L;
        final long now = System.currentTimeMillis();

        final PersistentDataContainer data = horse.getPersistentDataContainer();
        if (config.getBoolean("settings.male-ignore-cooldown", false)) {
            final String gender = data.getOrDefault(genderKey, PersistentDataType.STRING, "UNKNOWN");
            if ("male".equalsIgnoreCase(gender)) {
                horse.setAge(0);
                if (feedingTrainingValue > 0) {
                    TrainingManager.addFeedingUnits(horse, feedingTrainingValue);
                }
                return;
            }
        }

        final long lastBreed = data.getOrDefault(cooldownKey, PersistentDataType.LONG, 0L);

        horse.setAge(0);

        if (lastBreed > 0L) {
            final long elapsed = now - lastBreed;
            final long remaining = cooldownMillis - elapsed;

            if (remaining > 0L) {
                event.setCancelled(true);
            } else {
                data.remove(cooldownKey);
            }
        }

        if (!event.isCancelled() && feedingTrainingValue > 0) {
            TrainingManager.addFeedingUnits(horse, feedingTrainingValue);
            horse.getWorld().spawnParticle(Particle.COMPOSTER, horse.getLocation().add(0, 1, 0), 5);
        }
    }
}
