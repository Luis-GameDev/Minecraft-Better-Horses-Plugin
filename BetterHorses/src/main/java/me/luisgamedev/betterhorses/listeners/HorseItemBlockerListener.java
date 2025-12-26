package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Camel;
import org.bukkit.entity.Donkey;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Mule;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.entity.Strider;
import org.bukkit.entity.ZombieHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.AbstractHorseInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
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
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer c = meta.getPersistentDataContainer();
        return c.has(healthKey, PersistentDataType.DOUBLE);
    }

    private boolean isMountEntity(Object e) {
        return e instanceof Horse || e instanceof SkeletonHorse || e instanceof ZombieHorse
                || e instanceof Donkey || e instanceof Mule
                || e instanceof Llama || e instanceof Camel
                || e instanceof Pig || e instanceof Strider;
    }

    private boolean isMountTop(InventoryView view) {
        return view.getTopInventory() instanceof AbstractHorseInventory;
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
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryView view = event.getView();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        boolean blockedItem = isHorseItem(current) || isHorseItem(cursor);
        if (!blockedItem) return;

        if (isMountTop(view)) {
            event.setCancelled(true);
            player.updateInventory();
            return;
        }

        if (event.isShiftClick() && isMountTop(view) && isHorseItem(current)) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isHorseItem(event.getOldCursor())) return;

        InventoryView view = event.getView();
        Inventory top = view.getTopInventory();
        if (!(top instanceof AbstractHorseInventory)) return;

        int topSize = top.getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                player.updateInventory();
                return;
            }
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!isMountEntity(event.getRightClicked())) return;

        Player p = event.getPlayer();
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();

        if (isHorseItem(main) || isHorseItem(off)) {
            event.setCancelled(true);
        }
    }
}
