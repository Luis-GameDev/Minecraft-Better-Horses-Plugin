package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class HorseItemBlockerListener implements Listener {

    private final NamespacedKey healthKey;

    public HorseItemBlockerListener() {
        this.healthKey = new NamespacedKey(BetterHorses.getInstance(), "health");
    }

    private boolean isHorseItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(healthKey, PersistentDataType.DOUBLE);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (isHorseItem(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (isHorseItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (isHorseItem(inHand)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        if (!isHorseItem(current)) return;

        String invType = event.getInventory().getType().name();

        if (invType.equals("HORSE") || invType.equals("DONKEY")
                || invType.equals("MULE") || invType.equals("LLAMA")
                || invType.equals("CAMEL")) {

            if (event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER
                    || event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR) {
                event.setCancelled(true);
            }
        }
    }


    /*@EventHandler
    public void onInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (isHorseItem(item) && event.hasBlock()) {
            // blockiert nur Rechtsklick auf Blöcke (Platzieren/Essen/Benutzen)
            event.setCancelled(true);
        }
    }*/
}
