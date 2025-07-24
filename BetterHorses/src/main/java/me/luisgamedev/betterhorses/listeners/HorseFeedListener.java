package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public class HorseFeedListener implements Listener {

    private final NamespacedKey cooldownKey = new NamespacedKey(BetterHorses.getInstance(), "cooldown");
    private final NamespacedKey genderKey = new NamespacedKey(BetterHorses.getInstance(), "gender");

    @EventHandler
    public void onHorseFeed(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Horse horse)) return;

        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (item == null || item.getType() == Material.AIR) return;

        Material type = item.getType();
        boolean isFood = type == Material.GOLDEN_APPLE || type == Material.ENCHANTED_GOLDEN_APPLE
                || type == Material.SUGAR || type == Material.HAY_BLOCK
                || type == Material.WHEAT || type == Material.APPLE || type == Material.GOLDEN_CARROT || type == Material.CARROT;

        if (!isFood) return;

        FileConfiguration config = BetterHorses.getInstance().getConfig();

        // Block baby feeding if growth feature is enabled
        if (!horse.isAdult() && config.getBoolean("horse-growth-settings.enabled")) {
            event.setCancelled(true);
            return;
        }

        // Block breeding if not tamed (vanilla behavior)
        if (!horse.isTamed()) return;

        // Breeding cooldown check
        if (config.contains("settings.breeding-cooldown")) {
            long cooldownMillis = config.getLong("settings.breeding-cooldown") * 1000L;
            long now = System.currentTimeMillis();

            PersistentDataContainer data = horse.getPersistentDataContainer();
            Long lastBreed = data.get(cooldownKey, PersistentDataType.LONG);
            Double cooldownLeft = (cooldownMillis - (now - lastBreed)) / 1000.0;

            Player player = event.getPlayer();
            player.sendMessage("§cBreeding cooldown: " + String.format(Locale.US, "%.1f", cooldownLeft) + "s");

            if (config.getBoolean("settings.male-ignore-cooldown", false)) {
                String gender = data.getOrDefault(genderKey, PersistentDataType.STRING, "UNKNOWN");
                if ("male".equalsIgnoreCase(gender)) return;
            }

            if (lastBreed != null && lastBreed != 0 && cooldownLeft > 0) {
                event.setCancelled(true);
            }
        }
    }
}
