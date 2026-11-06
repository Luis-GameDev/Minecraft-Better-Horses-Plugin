package me.luisgamedev.betterhorses.api;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.language.LanguageManager;
import org.bukkit.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

class FoliaConversionCallbackTest {
    @Test void formattingTransferStopsConversionBeforeReadingThePlayersNewRegion() throws Exception {
        Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
        Field serverField = field(Bukkit.class, "server");
        Field pluginField = field(BetterHorses.class, "instance");
        Object previousServer = serverField.get(null), previousPlugin = pluginField.get(null);
        try {
            AtomicBoolean owned = new AtomicBoolean(true);
            AtomicInteger nativeReads = new AtomicInteger();
            TestPlugin plugin = (TestPlugin) unsafe.allocateInstance(TestPlugin.class);
            plugin.config = new YamlConfiguration();
            CallbackLanguage language = (CallbackLanguage) unsafe.allocateInstance(CallbackLanguage.class);
            language.owned = owned;
            plugin.language = language;
            PluginDescriptionFile description = new PluginDescriptionFile("BetterHorses", "test", TestPlugin.class.getName());
            field(JavaPlugin.class, "description").set(plugin, description);
            field(JavaPlugin.class, "pluginMeta").set(plugin, description);
            pluginField.set(null, plugin);
            serverField.set(null, proxy(Server.class, (p, m, a) -> m.getName().equals("isOwnedByCurrentRegion")
                    ? owned.get() : zero(m.getReturnType())));

            Map<NamespacedKey, Object> values = new HashMap<>();
            values.put(BetterHorseKeys.GENDER, "male");
            values.put(BetterHorseKeys.MOUNT_TYPE, "HORSE");
            values.put(BetterHorseKeys.HEALTH, 24.0);
            values.put(BetterHorseKeys.BASE_HEALTH, 20.0);
            values.put(BetterHorseKeys.SPEED, .3);
            values.put(BetterHorseKeys.JUMP, .7);
            PersistentDataContainer data = proxy(PersistentDataContainer.class, (p, m, a) -> switch (m.getName()) {
                case "has" -> values.containsKey(a[0]);
                case "get" -> values.get(a[0]);
                case "getOrDefault" -> values.getOrDefault(a[0], a[2]);
                case "set" -> { values.put((NamespacedKey) a[0], a[2]); yield null; }
                default -> zero(m.getReturnType());
            });
            ItemMeta meta = proxy(ItemMeta.class, (p, m, a) -> m.getName().equals("getPersistentDataContainer")
                    ? data : zero(m.getReturnType()));
            ItemStack legacyItem = new ItemStack(Material.SADDLE) {
                @Override public boolean hasItemMeta() { return true; }
                @Override public ItemMeta getItemMeta() { return meta; }
                @Override public boolean setItemMeta(ItemMeta replacement) { return true; }
            };
            Player player = proxy(Player.class, (p, m, a) -> {
                if (!owned.get() && Set.of("getWorld", "getLocation", "getInventory").contains(m.getName())) {
                    nativeReads.incrementAndGet();
                    throw new IllegalStateException("Player no longer belongs to this region");
                }
                return zero(m.getReturnType());
            });

            assertNull(BetterHorsesAPI.toHorse(legacyItem, player));
            assertFalse(owned.get(), "The real legacy normalization must invoke the external formatter");
            assertEquals(0, nativeReads.get(), "Conversion must stop before reading a transferred player");
        } finally {
            serverField.set(null, previousServer);
            pluginField.set(null, previousPlugin);
        }
    }

    public static class TestPlugin extends BetterHorses {
        FileConfiguration config;
        LanguageManager language;
        @Override public FileConfiguration getConfig() { return config; }
        @Override public LanguageManager getLang() { return language; }
    }
    public static class CallbackLanguage extends LanguageManager {
        AtomicBoolean owned;
        public CallbackLanguage() { super(null, null); }
        @Override public FileConfiguration getConfig() { return new YamlConfiguration(); }
        @Override public String parseToString(OfflinePlayer player, String message) { return message; }
        @Override public String getFormattedRaw(OfflinePlayer player, String key, Object... replacements) {
            owned.set(false);
            return key;
        }
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
    private static Object zero(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        if (type == float.class) return 0F;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return (char) 0;
        return null;
    }
}
