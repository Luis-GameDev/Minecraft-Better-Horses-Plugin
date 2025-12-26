package me.luisgamedev.betterhorses.api;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/**
 * Wrapper around a BetterHorses item to make reading custom metadata easier.
 */
public final class BetterHorseItem {

    private final ItemStack itemStack;

    public BetterHorseItem(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public ItemStack getHandle() {
        return itemStack;
    }

    public Optional<Double> getHealth() {
        return getDouble(BetterHorseKeys.HEALTH);
    }

    public Optional<Double> getSpeed() {
        return getDouble(BetterHorseKeys.SPEED);
    }

    public Optional<Double> getJump() {
        return getDouble(BetterHorseKeys.JUMP);
    }

    public Optional<String> getMountType() {
        ItemMeta meta = itemStack.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        return Optional.ofNullable(data.get(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING));
    }

    private Optional<Double> getDouble(org.bukkit.NamespacedKey key) {
        ItemMeta meta = itemStack.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        return Optional.ofNullable(data.get(key, PersistentDataType.DOUBLE));
    }
}
