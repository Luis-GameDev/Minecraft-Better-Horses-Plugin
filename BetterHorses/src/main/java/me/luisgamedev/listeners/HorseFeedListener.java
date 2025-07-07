package me.luisgamedev.listeners;

import me.luisgamedev.BetterHorses;
import org.bukkit.Material;
import org.bukkit.entity.Horse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

public class HorseFeedListener implements Listener {
    @EventHandler
    public void onHorseFeed(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Horse horse)) return;
        if (horse.isAdult()) return;

        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (item == null || item.getType() == Material.AIR) return;

        // List of all food items
        Material type = item.getType();
        if (type == Material.GOLDEN_APPLE || type == Material.ENCHANTED_GOLDEN_APPLE
                || type == Material.SUGAR || type == Material.HAY_BLOCK
                || type == Material.WHEAT || type == Material.APPLE) {

            if (BetterHorses.getInstance().getConfig().getBoolean("horse-growth-settings.enabled")) {
                event.setCancelled(true);
            }
        }
    }

}
