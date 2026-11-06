package me.luisgamedev.betterhorses.utils;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Small, synchronous commit boundary for horse item interactions that call other plugins. */
public final class HorseItemTransaction {
    private final BetterHorses plugin;
    private final Player player;
    private final World world;
    private final int slot;
    private final ItemStack expected;

    private HorseItemTransaction(BetterHorses plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        world = player.getWorld();
        slot = player.getInventory().getHeldItemSlot();
        expected = player.getInventory().getItemInMainHand().clone();
    }

    public static HorseItemTransaction capture(BetterHorses plugin, Player player) {
        return ready(plugin, player) ? new HorseItemTransaction(plugin, player) : null;
    }

    public ItemStack copy() { return expected.clone(); }

    public boolean matches() {
        return ready(plugin, player) && player.getWorld() == world
                && player.getInventory().getHeldItemSlot() == slot
                && expected.equals(player.getInventory().getItemInMainHand());
    }

    /** No external event is dispatched between the final comparison and inventory write. */
    public boolean replace(ItemStack replacement) {
        if (!matches()) return false;
        player.getInventory().setItemInMainHand(replacement);
        return true;
    }

    public static boolean ready(BetterHorses plugin, Player player) {
        return plugin.isEnabled() && owned(player) && player.isOnline() && !player.isDead()
                && WorldAccessPolicy.isEnabled(player.getWorld());
    }

    public static boolean owned(Entity entity) { return entity != null; }
    public static boolean owned(Location location) { return location != null; }

    /** Rollback can follow a temporary entity after a callback transfers it to another region. */
    public static void removeTemporary(BetterHorses plugin, Entity entity) {
        if (entity == null) return;
        entity.remove();
    }

    /** Stage delivery before source removal, so a cancelled item spawn leaves the mount intact. */
    public static Delivery stage(BetterHorses plugin, Player player, ItemStack item) {
        if (!ready(plugin, player)) return null;
        int slot = player.getInventory().firstEmpty();
        if (slot >= 0) {
            ItemStack staged = item.clone();
            player.getInventory().setItem(slot, staged);
            return new Delivery(plugin, player, slot, staged, null, 0);
        }
        int[] pickupDelay = {10};
        Item dropped = player.getWorld().dropItem(player.getLocation(), item.clone(), candidate -> {
            pickupDelay[0] = candidate.getPickupDelay();
            // Bounded delay also remains recoverable if a callback disables this plugin.
            candidate.setPickupDelay(40);
        });
        if (dropped == null) return null;
        if (!owned(dropped)) {
            removeTemporary(plugin, dropped);
            return null;
        }
        if (!dropped.isValid()) return null;
        if (!item.equals(dropped.getItemStack())) {
            removeTemporary(plugin, dropped);
            return null;
        }
        return new Delivery(plugin, player, -1, item.clone(), dropped, pickupDelay[0]);
    }

    public static final class Delivery {
        private final BetterHorses plugin;
        private final Player player;
        private final int slot;
        private final ItemStack staged;
        private final Item dropped;
        private final int pickupDelay;

        private Delivery(BetterHorses plugin, Player player, int slot, ItemStack staged, Item dropped, int pickupDelay) {
            this.plugin = plugin; this.player = player; this.slot = slot;
            this.staged = staged; this.dropped = dropped; this.pickupDelay = pickupDelay;
        }

        public void commit() {
            if (dropped != null && owned(dropped) && dropped.isValid()
                    && dropped.getPickupDelay() == 40) dropped.setPickupDelay(pickupDelay);
        }

        public void rollback() {
            if (dropped != null) removeTemporary(plugin, dropped);
            else if (owned(player) && staged.equals(player.getInventory().getItem(slot))) {
                player.getInventory().setItem(slot, null);
            }
        }
    }
}
