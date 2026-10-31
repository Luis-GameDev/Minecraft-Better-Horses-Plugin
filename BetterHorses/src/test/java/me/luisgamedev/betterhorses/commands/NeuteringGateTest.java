package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.utils.NeuteringPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NeuteringGateTest {
    @Test void existingConfigurationsDefaultToEnabledAndHonorExplicitDisable() {
        FileConfiguration config = new YamlConfiguration();
        assertTrue(NeuteringPolicy.isEnabled(config));
        config.set("horse-neutering.shears-enabled", false);
        assertTrue(NeuteringPolicy.isEnabled(config));
        config.set("horse-neutering.enabled", false);
        assertFalse(NeuteringPolicy.isEnabled(config));
        config.set("horse-neutering.enabled", true);
        assertTrue(NeuteringPolicy.isEnabled(config));
    }

    @Test void directlyInvokingTheItemHandlerWhileDisabledCannotReadOrMutateTheItem() throws Exception {
        Field instance = field(BetterHorses.class, "instance");
        Object previous = instance.get(null);
        try {
            TestPlugin plugin = (TestPlugin) ((Unsafe) field(Unsafe.class, "theUnsafe").get(null)).allocateInstance(TestPlugin.class);
            plugin.configuration = new YamlConfiguration();
            plugin.configuration.set("horse-neutering.enabled", false);
            instance.set(null, plugin);
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (proxy, method, args) -> { throw new AssertionError("Disabled neutering accessed player: " + method); });
            assertTrue(HorseNeuterCommand.handle(player));
        } finally {
            instance.set(null, previous);
        }
    }

    @Test void disablingDuringTheCancellableEventCannotCommitNeutering() throws Exception {
        Field instance = field(BetterHorses.class, "instance"), serverField = field(Bukkit.class, "server");
        Object previousPlugin = instance.get(null), previousServer = serverField.get(null);
        try {
            TestPlugin plugin = (TestPlugin) ((Unsafe) field(Unsafe.class, "theUnsafe").get(null)).allocateInstance(TestPlugin.class);
            plugin.configuration = new YamlConfiguration();
            PluginDescriptionFile description = new PluginDescriptionFile("BetterHorses", "test", TestPlugin.class.getName());
            field(JavaPlugin.class, "description").set(plugin, description);
            field(JavaPlugin.class, "pluginMeta").set(plugin, description);
            instance.set(null, plugin);
            Map<NamespacedKey, Object> values = new HashMap<>();
            values.put(BetterHorseKeys.GENDER, "male");
            values.put(BetterHorseKeys.MOUNT_TYPE, "HORSE");
            PersistentDataContainer data = proxy(PersistentDataContainer.class, (target, method, arguments) -> switch (method.getName()) {
                case "has" -> values.containsKey(arguments[0]);
                case "get" -> values.get(arguments[0]);
                case "set" -> throw new AssertionError("Disabled neutering wrote persistent data");
                default -> defaultValue(method.getReturnType());
            });
            ItemMeta meta = proxy(ItemMeta.class, (target, method, arguments) -> switch (method.getName()) {
                case "getPersistentDataContainer" -> data;
                default -> defaultValue(method.getReturnType());
            });
            ItemStack item = new TestItem(meta);
            AtomicInteger inventoryWrites = new AtomicInteger(), events = new AtomicInteger();
            PlayerInventory inventory = proxy(PlayerInventory.class, (target, method, arguments) -> switch (method.getName()) {
                case "getItemInMainHand" -> item;
                case "setItemInMainHand" -> { inventoryWrites.incrementAndGet(); yield null; }
                default -> defaultValue(method.getReturnType());
            });
            Player player = proxy(Player.class, (target, method, arguments) -> switch (method.getName()) {
                case "getInventory" -> inventory;
                case "hasPermission" -> true;
                case "getName" -> "TestPlayer";
                default -> defaultValue(method.getReturnType());
            });
            PluginManager manager = proxy(PluginManager.class, (target, method, arguments) -> {
                if (method.getName().equals("callEvent")) {
                    events.incrementAndGet();
                    plugin.configuration.set("horse-neutering.enabled", false);
                }
                return defaultValue(method.getReturnType());
            });
            serverField.set(null, proxy(Server.class, (target, method, arguments) ->
                    method.getName().equals("getPluginManager") ? manager : defaultValue(method.getReturnType())));
            assertTrue(HorseNeuterCommand.handle(player));
            assertEquals(1, events.get());
            assertEquals(0, inventoryWrites.get());
            assertFalse(values.containsKey(BetterHorseKeys.NEUTERED));
        } finally {
            instance.set(null, previousPlugin);
            serverField.set(null, previousServer);
        }
    }

    private static final class TestItem extends ItemStack {
        private final ItemMeta meta;
        TestItem(ItemMeta meta) { super(Material.SADDLE); this.meta = meta; }
        @Override public boolean hasItemMeta() { return true; }
        @Override public ItemMeta getItemMeta() { return meta; }
        @Override public ItemStack clone() { return new TestItem(meta); }
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == double.class) return 0.0D;
        if (type == float.class) return 0.0F;
        if (type == long.class) return 0L;
        if (type == int.class) return 0;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        return null;
    }

    public static final class TestPlugin extends BetterHorses {
        FileConfiguration configuration;
        @Override public FileConfiguration getConfig() { return configuration; }
        @Override public LanguageManager getLang() { return null; }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}
