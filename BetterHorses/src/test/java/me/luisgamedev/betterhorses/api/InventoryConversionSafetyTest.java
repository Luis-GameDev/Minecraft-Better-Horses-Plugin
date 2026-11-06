package me.luisgamedev.betterhorses.api;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.language.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.AbstractHorseInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class InventoryConversionSafetyTest {
    @Test void corruptChestKeepsTheSourceItemAndDoesNotAttemptASpawn() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.values.put(BetterHorseKeys.CHESTED, (byte) 1);
            fixture.values.put(BetterHorseKeys.CHEST_CONTENTS, "not base64 inventory");
            ItemStack source = fixture.item();
            assertNull(BetterHorsesAPI.toHorse(source, fixture.noSpawnPlayer()));
            assertEquals(1, source.getAmount());
            assertEquals("not base64 inventory", fixture.values.get(BetterHorseKeys.CHEST_CONTENTS));
        }
    }

    @Test void unreadableEquipmentNeverFallsBackToAMetadataFreeMaterial() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.values.put(BetterHorseKeys.ARMOR, Material.DIAMOND_HORSE_ARMOR.name());
            fixture.values.put(BetterHorseKeys.ARMOR_DATA, "broken equipment");
            ItemStack source = fixture.item();
            assertNull(BetterHorsesAPI.toHorse(source, fixture.noSpawnPlayer()));
            assertEquals(1, source.getAmount());
            assertEquals("broken equipment", fixture.values.get(BetterHorseKeys.ARMOR_DATA));
        }
    }

    @Test void oversizedNegativeAndWrongTypeInventoriesAreRejected() throws Exception {
        for (String encoded : new String[]{inventory(-1), inventory(Integer.MAX_VALUE), inventory(1, "not an item")}) {
            try (Fixture fixture = new Fixture()) {
                fixture.values.put(BetterHorseKeys.CHEST_CONTENTS, encoded);
                assertNull(BetterHorsesAPI.toHorse(fixture.item(), fixture.noSpawnPlayer()));
            }
        }
    }

    @Test void validEmptySlotsRemainAValidChestSnapshot() throws Exception {
        Method decoder = BetterHorsesAPI.class.getDeclaredMethod("deserializeStorageContents", String.class);
        decoder.setAccessible(true);
        assertArrayEquals(new ItemStack[]{null, null}, (ItemStack[]) decoder.invoke(null, inventory(2, null, null)));
    }

    @Test void serializationFailureDoesNotReturnAnIncompleteItemOrRemoveTheMount() throws Exception {
        try (Fixture fixture = new Fixture()) {
            AtomicInteger removals = new AtomicInteger();
            ItemStack unreadableSaddle = new ItemStack(Material.SADDLE) {
                @Override public Map<String, Object> serialize() { throw new IllegalArgumentException("metadata serialization failed"); }
            };
            AbstractHorseInventory inventory = proxy(AbstractHorseInventory.class, (target, method, arguments) ->
                    method.getName().equals("getSaddle") ? unreadableSaddle : defaultValue(method.getReturnType()));
            AttributeInstance attribute = proxy(AttributeInstance.class, (target, method, arguments) -> switch (method.getName()) {
                case "getBaseValue", "getValue" -> 20.0D;
                case "getModifiers" -> List.of();
                default -> defaultValue(method.getReturnType());
            });
            Horse horse = proxy(Horse.class, (target, method, arguments) -> switch (method.getName()) {
                case "getPersistentDataContainer" -> fixture.data;
                case "getType" -> EntityType.HORSE;
                case "getUniqueId" -> fixture.id;
                case "getAttribute" -> attribute;
                case "getInventory" -> inventory;
                case "getHealth" -> 20.0D;
                case "getStyle" -> Horse.Style.WHITE;
                case "getColor" -> Horse.Color.WHITE;
                case "getPassengers" -> List.of();
                case "isValid", "isAdult" -> true;
                case "remove" -> { removals.incrementAndGet(); yield null; }
                default -> defaultValue(method.getReturnType());
            });
            assertNull(BetterHorsesAPI.toItem(horse, null));
            assertEquals(0, removals.get());
        }
    }

    private static String inventory(int length, Object... items) throws Exception {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeInt(length);
            for (Object item : items) output.writeObject(item);
            output.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Field instance = field(BetterHorses.class, "instance"), server = field(Bukkit.class, "server");
        final Object previousPlugin = instance.get(null), previousServer = server.get(null);
        final UUID id = UUID.randomUUID();
        final Map<NamespacedKey, Object> values = new HashMap<>();
        final PersistentDataContainer data;

        Fixture() throws Exception {
            Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
            TestPlugin plugin = (TestPlugin) unsafe.allocateInstance(TestPlugin.class);
            plugin.configuration = new YamlConfiguration();
            plugin.logger = Logger.getAnonymousLogger();
            plugin.logger.setUseParentHandlers(false);
            plugin.language = (TestLanguage) unsafe.allocateInstance(TestLanguage.class);
            PluginDescriptionFile description = new PluginDescriptionFile("BetterHorses", "test", TestPlugin.class.getName());
            field(JavaPlugin.class, "description").set(plugin, description);
            field(JavaPlugin.class, "pluginMeta").set(plugin, description);
            instance.set(null, plugin);
            server.set(null, proxy(Server.class, (target, method, arguments) -> switch (method.getName()) {
                case "isOwnedByCurrentRegion", "isPrimaryThread" -> true;
                case "getBukkitVersion", "getMinecraftVersion" -> "1.20.6-R0.1-SNAPSHOT";
                default -> defaultValue(method.getReturnType());
            }));
            values.put(BetterHorseKeys.GENDER, "male");
            values.put(BetterHorseKeys.MOUNT_TYPE, "HORSE");
            values.put(BetterHorseKeys.HEALTH, 20.0D);
            values.put(BetterHorseKeys.SPEED, 0.3D);
            values.put(BetterHorseKeys.JUMP, 0.7D);
            values.put(BetterHorseKeys.GROWTH_STAGE, 10);
            values.put(new NamespacedKey("betterhorses", "training_stat_model"), 1);
            data = proxy(PersistentDataContainer.class, (target, method, arguments) -> switch (method.getName()) {
                case "has" -> values.containsKey(arguments[0]);
                case "get" -> values.get(arguments[0]);
                case "getOrDefault" -> values.getOrDefault(arguments[0], arguments[2]);
                case "set" -> { values.put((NamespacedKey) arguments[0], arguments[2]); yield null; }
                case "remove" -> values.remove(arguments[0]);
                case "getKeys" -> Set.copyOf(values.keySet());
                default -> defaultValue(method.getReturnType());
            });
        }

        ItemStack item() {
            ItemMeta meta = proxy(ItemMeta.class, (target, method, arguments) ->
                    method.getName().equals("getPersistentDataContainer") ? data : defaultValue(method.getReturnType()));
            return new ItemStack(Material.SADDLE) {
                @Override public boolean hasItemMeta() { return true; }
                @Override public ItemMeta getItemMeta() { return meta; }
                @Override public boolean setItemMeta(ItemMeta replacement) { return true; }
            };
        }

        Player noSpawnPlayer() {
            return proxy(Player.class, (target, method, arguments) -> {
                if (method.getName().equals("getUniqueId")) return id;
                throw new AssertionError("Invalid source reached spawning or player mutation: " + method);
            });
        }

        @Override public void close() throws Exception {
            instance.set(null, previousPlugin);
            server.set(null, previousServer);
        }
    }

    public static final class TestPlugin extends BetterHorses {
        FileConfiguration configuration;
        Logger logger;
        LanguageManager language;
        @Override public FileConfiguration getConfig() { return configuration; }
        @Override public LanguageManager getLang() { return language; }
        @Override public Logger getLogger() { return logger; }
    }

    public static final class TestLanguage extends LanguageManager {
        public TestLanguage() { super(null, null); }
        @Override public String getRaw(OfflinePlayer player, String key) { return key; }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
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

    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}
