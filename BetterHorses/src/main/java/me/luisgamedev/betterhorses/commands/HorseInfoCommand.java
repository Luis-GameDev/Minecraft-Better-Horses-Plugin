package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


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

            Map<String, String> placeholders = createCommonPlaceholders(container);
            placeholders.put("%item_type%", String.valueOf(item.getType()));
            placeholders.put("%item_amount%", String.valueOf(item.getAmount()));
            placeholders.put("%item_display_name%", meta.hasDisplayName() ? meta.getDisplayName() : "<none>");
            placeholders.put("%item_unbreakable%", String.valueOf(meta.isUnbreakable()));
            if (meta instanceof Damageable damageable) {
                placeholders.put("%item_damage%", String.valueOf(damageable.getDamage()));
                placeholders.put("%item_damage_line%", "&eDamage: &f" + damageable.getDamage());
            } else {
                placeholders.put("%item_damage%", "<none>");
                placeholders.put("%item_damage_line%", "");
            }
            placeholders.put("%item_custom_model_data%", meta.hasCustomModelData() ? String.valueOf(meta.getCustomModelData()) : "<none>");
            placeholders.put("%item_lore%", renderItemLore(meta));
            placeholders.put("%item_flags%", renderItemFlags(meta));
            placeholders.put("%persistent_data%", renderPersistentData(container));
            placeholders.put("%known_values_title%", "Known BetterHorses Values (item)");
            placeholders.put("%known_values%", renderKnownHorseDataValues(container));
            sendTemplate(player, "messages.horse-info.item", placeholders);

            if (container.getKeys().isEmpty()) {
                plugin.debugLog("HORSE_INFO", "PDC_SCAN", true,
                        "Player " + player.getName() + " inspected item without custom PDC keys.");
            }
        }

        boolean inspectedMount = inspectMountedHorse(player, plugin);

        if (!inspectedItem && !inspectedMount) {
            sendTemplate(player, "messages.horse-info.no-target", Map.of());
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
        AttributeInstance maxHealth = horse.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        AttributeInstance speed = horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        AttributeInstance jump = horse.getAttribute(Attribute.valueOf("HORSE_JUMP_STRENGTH"));

        Map<String, String> placeholders = createCommonPlaceholders(data);
        placeholders.put("%entity_type%", String.valueOf(horse.getType()));
        placeholders.put("%entity_uuid%", String.valueOf(horse.getUniqueId()));
        placeholders.put("%tamed%", String.valueOf(horse.isTamed()));
        placeholders.put("%owner%", horse.getOwner() == null ? "<none>" : horse.getOwner().getUniqueId().toString());
        placeholders.put("%current_health%", String.format("%.2f", horse.getHealth()));
        placeholders.put("%max_health%", String.format("%.2f", maxHealth == null ? 0.0 : maxHealth.getBaseValue()));
        placeholders.put("%health%", String.format("%.2f / %.2f", horse.getHealth(), maxHealth == null ? 0.0 : maxHealth.getBaseValue()));
        placeholders.put("%speed%", speed == null ? "<none>" : String.format("%.4f", speed.getBaseValue()));
        placeholders.put("%jump%", jump == null ? "<none>" : String.format("%.4f", jump.getBaseValue()));
        placeholders.put("%trait%", data.getOrDefault(BetterHorseKeys.TRAIT, PersistentDataType.STRING, "<none>"));
        placeholders.put("%gender%", data.getOrDefault(BetterHorseKeys.GENDER, PersistentDataType.STRING, "<none>"));
        placeholders.put("%growth_stage%", String.valueOf(data.getOrDefault(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER, -1)));
        placeholders.put("%neutered%", resolveBooleanFlag(data, BetterHorseKeys.NEUTERED));
        placeholders.put("%persistent_data%", renderPersistentData(data));
        placeholders.put("%known_values_title%", "Known BetterHorses Values (mounted horse)");
        placeholders.put("%known_values%", renderKnownHorseDataValues(data));
        sendTemplate(player, "messages.horse-info.mounted-horse", placeholders);

        plugin.debugLog("HORSE_INFO", "MOUNT_SCAN", true,
                "Player " + player.getName() + " inspected mounted horse " + horse.getUniqueId() + ".");
        return true;
    }

    private static String renderKnownHorseDataValues(PersistentDataContainer container) {
        List<String> lines = new ArrayList<>();
        for (NamespacedKey key : getKnownHorseKeys()) {
            boolean present = container.getKeys().contains(key);
            String renderedValue = resolveKnownValue(container, key);
            String state = present ? "&a" + "present" : "&c" + "missing";
            lines.add("&7  - " + key + "&f = " + renderedValue
                    + "&8 [" + state + "&8]");
        }
        return String.join("\n", lines);
    }

    private static String renderPersistentData(PersistentDataContainer container) {
        if (container.getKeys().isEmpty()) {
            return "&ePersistentData: &f<none>";
        }

        List<String> lines = new ArrayList<>();
        lines.add("&ePersistentData:");
        for (NamespacedKey key : container.getKeys()) {
            lines.add("&7  - " + key + "&f = " + resolveValue(container, key));
        }
        return String.join("\n", lines);
    }

    private static String renderItemLore(ItemMeta meta) {
        if (!meta.hasLore()) {
            return "&eLore: &f<none>";
        }

        List<String> lines = new ArrayList<>();
        lines.add("&eLore:");
        for (String line : meta.getLore()) {
            lines.add("&7  - " + line);
        }
        return String.join("\n", lines);
    }

    private static String renderItemFlags(ItemMeta meta) {
        if (meta.getItemFlags().isEmpty()) {
            return "&eItemFlags: &f<none>";
        }

        List<String> lines = new ArrayList<>();
        lines.add("&eItemFlags:");
        for (ItemFlag itemFlag : meta.getItemFlags()) {
            lines.add("&7  - " + itemFlag.name());
        }
        return String.join("\n", lines);
    }

    private static Map<String, String> createCommonPlaceholders(PersistentDataContainer container) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        for (NamespacedKey key : getKnownHorseKeys()) {
            String value = resolveKnownValue(container, key);
            placeholders.put("%" + key.getKey() + "%", value);
            placeholders.put("%pdc_" + key.getKey() + "%", value);
            placeholders.put("%pdc_" + key + "%", value);
        }
        return placeholders;
    }

    private static void sendTemplate(Player player, String path, Map<String, String> placeholders) {
        BetterHorses plugin = BetterHorses.getInstance();
        List<String> lines = plugin.getLang().getConfig().getStringList(path);
        if (lines.isEmpty()) {
            lines = List.of(plugin.getLang().getRaw(path));
        }

        for (String line : lines) {
            String replaced = replacePlaceholders(line, placeholders);
            if (replaced.isEmpty()) {
                continue;
            }
            String parsed = plugin.getLang().parseToString(player, replaced);
            for (String splitLine : parsed.split("\n", -1)) {
                player.sendMessage(splitLine);
            }
        }
    }

    private static String replacePlaceholders(String message, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace(entry.getKey(), escapeMiniMessageTags(entry.getValue()));
        }
        return message;
    }

    private static String escapeMiniMessageTags(String value) {
        return value.replace("<", "\\<");
    }

    private static List<NamespacedKey> getKnownHorseKeys() {
        List<NamespacedKey> keys = new ArrayList<>();
        for (Field field : BetterHorseKeys.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != NamespacedKey.class) {
                continue;
            }

            try {
                NamespacedKey key = (NamespacedKey) field.get(null);
                if (key != null) {
                    keys.add(key);
                }
            } catch (IllegalAccessException ignored) {
                // Should not happen for public constants; skip inaccessible fields.
            }
        }
        keys.sort(Comparator.comparing(NamespacedKey::toString));
        return keys;
    }

    private static String resolveKnownValue(PersistentDataContainer container, NamespacedKey key) {
        if (key.equals(BetterHorseKeys.NEUTERED)) {
            return resolveBooleanFlag(container, key);
        }
        if (key.equals(BetterHorseKeys.TRAINING_BRUSH_COOLDOWN)
                || key.equals(BetterHorseKeys.TRAINING_FEED_COOLDOWN)
                || key.equals(BetterHorseKeys.COOLDOWN)) {
            return resolveTimestamp(container, key);
        }
        if (container.getKeys().contains(key)) {
            return resolveValue(container, key);
        }
        return "<missing>";
    }

    private static String resolveBooleanFlag(PersistentDataContainer container, NamespacedKey key) {
        Byte byteValue = container.get(key, PersistentDataType.BYTE);
        if (byteValue != null) {
            return byteValue != 0 ? "true (byte=" + byteValue + ")" : "false (byte=0)";
        }

        Integer integerValue = container.get(key, PersistentDataType.INTEGER);
        if (integerValue != null) {
            return integerValue != 0 ? "true (int=" + integerValue + ")" : "false (int=0)";
        }

        return "<missing>";
    }

    private static String resolveTimestamp(PersistentDataContainer container, NamespacedKey key) {
        Long timestamp = container.get(key, PersistentDataType.LONG);
        if (timestamp == null) {
            return "<missing>";
        }

        final FileConfiguration config = BetterHorses.getInstance().getConfig();
        final long cooldownMillis = resolveCooldownMillis(config, key);
        final long cooldownEnd = timestamp + cooldownMillis;

        long delta = cooldownEnd - System.currentTimeMillis();
        if (delta > 0) {
            return timestamp + " (active, " + (delta / 1000.0) + "s remaining)";
        }
        return timestamp + " (ready)";
    }

    private static long resolveCooldownMillis(FileConfiguration config, NamespacedKey key) {
        if (key.equals(BetterHorseKeys.COOLDOWN)) {
            return Math.max(0L, config.getLong("settings.breeding-cooldown", 0L) * 1000L);
        }

        if (key.equals(BetterHorseKeys.TRAINING_BRUSH_COOLDOWN)) {
            return Math.max(0L, config.getLong("training.categories.brushing.cooldown-seconds", 180L) * 1000L);
        }

        if (key.equals(BetterHorseKeys.TRAINING_FEED_COOLDOWN)) {
            return Math.max(0L, config.getLong("training.categories.feeding.cooldown-seconds", 0L) * 1000L);
        }

        return 0L;
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
