package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
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
        boolean inspectedItem = false;

        if (item != null && !item.getType().isAir() && item.hasItemMeta()) {
            inspectedItem = true;
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

            if (!meta.getItemFlags().isEmpty()) {
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
            } else {
                player.sendMessage(ChatColor.YELLOW + "PersistentData:");
                for (NamespacedKey key : container.getKeys()) {
                    String renderedValue = resolveValue(container, key);
                    player.sendMessage(ChatColor.GRAY + "  - " + key + ChatColor.WHITE + " = " + renderedValue);
                }
            }
        }

        boolean inspectedMount = inspectMountedHorse(player, plugin);

        if (!inspectedItem && !inspectedMount) {
            player.sendMessage(ChatColor.RED + "Hold a horse item in your main hand or ride a horse first.");
            plugin.debugLog("HORSE_INFO", "VALIDATION", false,
                    "Player " + player.getName() + " has no inspectable item and is not riding a supported mount.");
            return true;
        }

        plugin.debugLog("HORSE_INFO", "COMPLETE", true,
                "Player " + player.getName() + " inspected item=" + inspectedItem + ", mountedHorse=" + inspectedMount + ".");
        return true;
    }

    private static boolean inspectMountedHorse(Player player, BetterHorses plugin) {
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof AbstractHorse horse)) {
            return false;
        }

        PersistentDataContainer data = horse.getPersistentDataContainer();
        player.sendMessage(ChatColor.GOLD + "------ BetterHorses Mounted Horse Debug ------");
        player.sendMessage(ChatColor.YELLOW + "Entity: " + ChatColor.WHITE + horse.getType());
        player.sendMessage(ChatColor.YELLOW + "UUID: " + ChatColor.WHITE + horse.getUniqueId());
        player.sendMessage(ChatColor.YELLOW + "Tamed: " + ChatColor.WHITE + horse.isTamed());
        player.sendMessage(ChatColor.YELLOW + "Owner: " + ChatColor.WHITE
                + (horse.getOwner() == null ? "<none>" : horse.getOwner().getUniqueId()));

        AttributeInstance maxHealth = horse.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        AttributeInstance speed = horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        AttributeInstance jump = horse.getAttribute(Attribute.HORSE_JUMP_STRENGTH);

        player.sendMessage(ChatColor.YELLOW + "Health: " + ChatColor.WHITE + String.format("%.2f / %.2f",
                horse.getHealth(), maxHealth == null ? 0.0 : maxHealth.getBaseValue()));
        player.sendMessage(ChatColor.YELLOW + "Speed: " + ChatColor.WHITE
                + (speed == null ? "<none>" : String.format("%.4f", speed.getBaseValue())));
        player.sendMessage(ChatColor.YELLOW + "Jump: " + ChatColor.WHITE
                + (jump == null ? "<none>" : String.format("%.4f", jump.getBaseValue())));
        player.sendMessage(ChatColor.YELLOW + "Trait: " + ChatColor.WHITE
                + data.getOrDefault(BetterHorseKeys.TRAIT, PersistentDataType.STRING, "<none>"));
        player.sendMessage(ChatColor.YELLOW + "Gender: " + ChatColor.WHITE
                + data.getOrDefault(BetterHorseKeys.GENDER, PersistentDataType.STRING, "<none>"));
        player.sendMessage(ChatColor.YELLOW + "Growth Stage: " + ChatColor.WHITE
                + data.getOrDefault(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER, -1));
        plugin.debugLog("HORSE_INFO", "MOUNT_SCAN", true,
                "Player " + player.getName() + " inspected mounted horse " + horse.getUniqueId() + ".");
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
