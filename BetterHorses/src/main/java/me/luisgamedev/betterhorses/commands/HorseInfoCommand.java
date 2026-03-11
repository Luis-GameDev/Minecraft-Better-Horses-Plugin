package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;


public final class HorseInfoCommand {

    private HorseInfoCommand() {
    }

    public static boolean handle(Player player) {
        BetterHorses plugin = BetterHorses.getInstance();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            player.sendMessage(ChatColor.RED + "Hold a horse item in your main hand first.");
            plugin.debugLog("HORSE_INFO", "VALIDATION", false,
                    "Player " + player.getName() + " is not holding an item with meta.");
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        player.sendMessage(ChatColor.GOLD + "------ BetterHorses Item Debug ------");
        player.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + item.getType());
        player.sendMessage(ChatColor.YELLOW + "Amount: " + ChatColor.WHITE + item.getAmount());
        player.sendMessage(ChatColor.YELLOW + "Display Name: " + ChatColor.WHITE
                + (meta.hasDisplayName() ? meta.getDisplayName() : "<none>"));
        player.sendMessage(ChatColor.YELLOW + "Unbreakable: " + ChatColor.WHITE + meta.isUnbreakable());

        if (meta instanceof Damageable damageable) {
            player.sendMessage(ChatColor.YELLOW + "Damage: " + ChatColor.WHITE + damageable.getDamage());
        }

        player.sendMessage(ChatColor.YELLOW + "CustomModelData: " + ChatColor.WHITE
                + (meta.hasCustomModelData() ? meta.getCustomModelData() : "<none>"));

        if (meta.hasLore()) {
            player.sendMessage(ChatColor.YELLOW + "Lore:");
            for (String line : meta.getLore()) {
                player.sendMessage(ChatColor.GRAY + "  - " + line);
            }
        } else {
            player.sendMessage(ChatColor.YELLOW + "Lore: " + ChatColor.WHITE + "<none>");
        }

        if (meta.hasItemFlags()) {
            player.sendMessage(ChatColor.YELLOW + "ItemFlags:");
            for (ItemFlag itemFlag : meta.getItemFlags()) {
                player.sendMessage(ChatColor.GRAY + "  - " + itemFlag.name());
            }
        } else {
            player.sendMessage(ChatColor.YELLOW + "ItemFlags: " + ChatColor.WHITE + "<none>");
        }

        if (container.getKeys().isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "PersistentData: " + ChatColor.WHITE + "<none>");
            plugin.debugLog("HORSE_INFO", "PDC_SCAN", true,
                    "Player " + player.getName() + " inspected item without custom PDC keys.");
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "PersistentData:");
        for (NamespacedKey key : container.getKeys()) {
            String renderedValue = resolveValue(container, key);
            player.sendMessage(ChatColor.GRAY + "  - " + key + ChatColor.WHITE + " = " + renderedValue);
        }

        plugin.debugLog("HORSE_INFO", "COMPLETE", true,
                "Player " + player.getName() + " inspected item with " + container.getKeys().size() + " PDC keys.");
        return true;
    }

    private static String resolveValue(PersistentDataContainer container, NamespacedKey key) {
        if (container.has(key, PersistentDataType.STRING)) {
            return container.get(key, PersistentDataType.STRING);
        }
        if (container.has(key, PersistentDataType.DOUBLE)) {
            return String.valueOf(container.get(key, PersistentDataType.DOUBLE));
        }
        if (container.has(key, PersistentDataType.INTEGER)) {
            return String.valueOf(container.get(key, PersistentDataType.INTEGER));
        }
        if (container.has(key, PersistentDataType.LONG)) {
            return String.valueOf(container.get(key, PersistentDataType.LONG));
        }
        if (container.has(key, PersistentDataType.FLOAT)) {
            return String.valueOf(container.get(key, PersistentDataType.FLOAT));
        }
        if (container.has(key, PersistentDataType.SHORT)) {
            return String.valueOf(container.get(key, PersistentDataType.SHORT));
        }
        if (container.has(key, PersistentDataType.BYTE)) {
            return String.valueOf(container.get(key, PersistentDataType.BYTE));
        }
        if (container.has(key, PersistentDataType.BYTE_ARRAY)) {
            byte[] values = container.get(key, PersistentDataType.BYTE_ARRAY);
            return values == null ? "null" : toCompactArray(values.length);
        }
        if (container.has(key, PersistentDataType.INTEGER_ARRAY)) {
            int[] values = container.get(key, PersistentDataType.INTEGER_ARRAY);
            return values == null ? "null" : toCompactArray(values.length);
        }
        if (container.has(key, PersistentDataType.LONG_ARRAY)) {
            long[] values = container.get(key, PersistentDataType.LONG_ARRAY);
            return values == null ? "null" : toCompactArray(values.length);
        }
        if (container.has(key, PersistentDataType.TAG_CONTAINER)) {
            return "<nested-container>";
        }
        return "<unsupported-type>";
    }

    private static String toCompactArray(int length) {
        return "<array length=" + length + ">";
    }
}
