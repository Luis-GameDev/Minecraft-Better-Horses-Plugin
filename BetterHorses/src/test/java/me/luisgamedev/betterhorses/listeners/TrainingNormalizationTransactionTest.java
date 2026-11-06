package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrainingNormalizationTransactionTest {
    @Test void unchangedSlotReceivesNormalizedCopyWithoutMutatingTheSourceReference() throws Exception {
        try (Fixture f = new Fixture()) {
            ItemStack original = new ItemStack(Material.SADDLE);
            f.items[0] = original;
            f.normalize(f.inventory);
            assertEquals(2, f.items[0].getAmount());
            assertEquals(1, original.getAmount(), "formatting must not mutate the live source before commit");
            assertEquals(1, f.writes);
        }
    }

    @Test void formattingCallbackCannotOverwriteAReplacementStack() throws Exception {
        try (Fixture f = new Fixture()) {
            f.items[0] = new ItemStack(Material.SADDLE);
            ItemStack replacement = new ItemStack(Material.DIAMOND, 64);
            f.callback = () -> f.items[0] = replacement;
            f.normalize(f.inventory);
            assertSame(replacement, f.items[0]);
            assertEquals(64, replacement.getAmount());
            assertEquals(0, f.writes);
        }
    }

    @Test void disconnectOrShutdownDuringFormattingAbortsTheCommit() throws Exception {
        for (boolean shutdown : new boolean[]{false, true}) {
            try (Fixture f = new Fixture()) {
                f.items[0] = new ItemStack(Material.SADDLE);
                f.callback = () -> { if (shutdown) f.listener.shutdown(); else f.online = false; };
                f.normalize(f.inventory);
                assertEquals(1, f.items[0].getAmount());
                assertEquals(0, f.writes);
            }
        }
    }

    @Test void closingTheTopInventoryDuringFormattingKeepsItsOriginalContents() throws Exception {
        try (Fixture f = new Fixture()) {
            Inventory original = f.top;
            f.topItems[0] = new ItemStack(Material.SADDLE);
            f.callback = () -> { f.top = f.emptyTop(); f.view = f.newView(); };
            f.normalize(original);
            assertEquals(1, f.topItems[0].getAmount());
            assertEquals(0, f.writes);
        }
    }

    @Test void cursorReplacementDuringFormattingIsNotOverwritten() throws Exception {
        try (Fixture f = new Fixture()) {
            f.cursor = new ItemStack(Material.SADDLE);
            ItemStack replacement = new ItemStack(Material.DIAMOND, 64);
            f.callback = () -> f.cursor = replacement;
            f.normalizePlayer();
            assertSame(replacement, f.cursor);
            assertEquals(0, f.writes);
        }
    }

    @Test void aNewViewCannotReceiveThePreviousViewsNormalizedCursor() throws Exception {
        try (Fixture f = new Fixture()) {
            f.cursor = new ItemStack(Material.SADDLE);
            f.callback = () -> f.view = f.newView();
            f.normalizePlayer();
            assertEquals(1, f.cursor.getAmount());
            assertEquals(0, f.writes);
        }
    }

    @Test void unchangedCursorIsNormalizedNormally() throws Exception {
        try (Fixture f = new Fixture()) {
            f.cursor = new ItemStack(Material.SADDLE);
            f.normalizePlayer();
            assertEquals(2, f.cursor.getAmount());
            assertEquals(1, f.writes);
        }
    }


    private static final class Fixture implements AutoCloseable {
        final Server previous = Bukkit.getServer();
        final TestPlugin plugin;
        final TrainingDataLifecycleListener listener;
        final ItemStack[] items = new ItemStack[1], topItems = new ItemStack[1];
        final PlayerInventory inventory;
        final World world;
        final Player player;
        Inventory top;
        InventoryView view;
        ItemStack cursor;
        Runnable callback = () -> {};
        boolean online = true, owned = true;
        int writes;

        Fixture() throws Exception {
            Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
            plugin = (TestPlugin) unsafe.allocateInstance(TestPlugin.class);
            plugin.config = new YamlConfiguration();
            PluginDescriptionFile description = new PluginDescriptionFile("BetterHorses", "test", TestPlugin.class.getName());
            field(JavaPlugin.class, "description").set(plugin, description);
            field(JavaPlugin.class, "pluginMeta").set(plugin, description);
            field(JavaPlugin.class, "isEnabled").setBoolean(plugin, true);
            ItemFactory factory = proxy(ItemFactory.class, (self, method, args) -> switch (method.getName()) {
                case "equals" -> args[0] == args[1];
                default -> zero(method.getReturnType());
            });
            field(Bukkit.class, "server").set(null, proxy(Server.class, (self, method, args) -> switch (method.getName()) {
                case "getItemFactory" -> factory;
                case "isOwnedByCurrentRegion" -> owned;
                default -> zero(method.getReturnType());
            }));
            world = proxy(World.class, (self, method, args) -> switch (method.getName()) {
                case "getName" -> "lifecycle-world";
                case "getUID" -> UUID.fromString("00000000-0000-0000-0000-000000000001");
                default -> zero(method.getReturnType());
            });
            inventory = proxy(PlayerInventory.class, (self, method, args) -> {
                checkOwned();
                return switch (method.getName()) {
                    case "getSize" -> 1;
                    case "getItem" -> items[(Integer) args[0]];
                    case "setItem" -> { items[(Integer) args[0]] = (ItemStack) args[1]; writes++; yield null; }
                    default -> zero(method.getReturnType());
                };
            });
            top = proxy(Inventory.class, (self, method, args) -> {
                checkOwned();
                return switch (method.getName()) {
                    case "getType" -> InventoryType.CRAFTING;
                    case "getSize" -> 1;
                    case "getItem" -> topItems[(Integer) args[0]];
                    case "setItem" -> { topItems[(Integer) args[0]] = (ItemStack) args[1]; writes++; yield null; }
                    default -> zero(method.getReturnType());
                };
            });
            player = proxy(Player.class, (self, method, args) -> {
                if (method.getName().equals("getUniqueId")) return UUID.fromString("00000000-0000-0000-0000-000000000002");
                checkOwned();
                return switch (method.getName()) {
                    case "isOnline" -> online;
                    case "getWorld" -> world;
                    case "getInventory" -> inventory;
                    case "getOpenInventory" -> view;
                    case "getItemOnCursor" -> cursor;
                    case "setItemOnCursor" -> { cursor = (ItemStack) args[0]; writes++; yield null; }
                    default -> zero(method.getReturnType());
                };
            });
            view = newView();
            WorldAccessPolicy.reload(plugin);
            listener = new TrainingDataLifecycleListener(plugin, (copy, viewer) -> {
                callback.run();
                copy.setAmount(2);
                return true;
            });
            field(TrainingDataLifecycleListener.class, "active").setBoolean(listener, true);
        }
        void checkOwned() {  }
        Inventory emptyTop() { return proxy(Inventory.class, (self, method, args) -> method.getName().equals("getType") ? InventoryType.CRAFTING : zero(method.getReturnType())); }
        InventoryView newView() {
            Inventory captured = top;
            return new InventoryView() {
                @Override public Inventory getTopInventory() { return captured; }
                @Override public Inventory getBottomInventory() { return inventory; }
                @Override public HumanEntity getPlayer() { return player; }
                @Override public InventoryType getType() { return InventoryType.CRAFTING; }
                @Override public String getTitle() { return "test"; }
                @Override public String getOriginalTitle() { return "test"; }
                @Override public void setTitle(String title) { }
            };
        }
        void normalize(Inventory target) throws Exception {
            var method = TrainingDataLifecycleListener.class.getDeclaredMethod("normalizeInventory", Inventory.class, Player.class);
            method.setAccessible(true);
            method.invoke(listener, target, player);
        }
        void normalizePlayer() throws Exception {
            var method = TrainingDataLifecycleListener.class.getDeclaredMethod("normalizePlayer", Player.class);
            method.setAccessible(true);
            method.invoke(listener, player);
        }
        @Override public void close() throws Exception { listener.shutdown(); field(Bukkit.class, "server").set(null, previous); }
    }

    public static final class TestPlugin extends BetterHorses {
        FileConfiguration config;
        @Override public FileConfiguration getConfig() { return config; }
    }
    private static Field field(Class<?> type, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "equals" -> self == args[0];
                case "hashCode" -> System.identityHashCode(self);
                default -> type.getSimpleName();
            };
            return handler.invoke(self, method, args);
        }));
    }
    private static Object zero(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        if (type == float.class) return 0F;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        return 0;
    }
}
