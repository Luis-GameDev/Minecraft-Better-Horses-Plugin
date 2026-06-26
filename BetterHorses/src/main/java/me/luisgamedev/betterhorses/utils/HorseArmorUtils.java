package me.luisgamedev.betterhorses.utils;

import org.bukkit.Material;
import org.bukkit.inventory.AbstractHorseInventory;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

public final class HorseArmorUtils {

    private HorseArmorUtils() {
    }

    public static ItemStack getArmor(AbstractHorseInventory inventory) {
        if (inventory == null) return null;

        ItemStack armor = invokeGetter(inventory);

        if (armor == null || armor.getType() == Material.AIR) return null;
        return armor;
    }

    public static void setArmor(AbstractHorseInventory inventory, ItemStack armor) {
        if (inventory == null || armor == null || armor.getType() == Material.AIR) return;

        invokeSetter(inventory, armor);
    }

    private static ItemStack invokeGetter(AbstractHorseInventory inventory) {
        try {
            Method getter = inventory.getClass().getMethod("getArmor");
            Object result = getter.invoke(inventory);
            if (result instanceof ItemStack) {
                return (ItemStack) result;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static void invokeSetter(AbstractHorseInventory inventory, ItemStack armor) {
        try {
            Method setter = inventory.getClass().getMethod("setArmor", ItemStack.class);
            setter.invoke(inventory, armor);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
