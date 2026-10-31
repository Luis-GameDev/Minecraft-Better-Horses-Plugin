package me.luisgamedev.betterhorses.inspection;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.events.BetterHorseNeuterEvent;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class HorseNeuteringServiceTest {
    @Test void successfulNeuteringCommitsFertilityAndOneDurabilityPointAfterBothEvents() throws Exception {
        try (Fixture f = new Fixture()) {
            f.held.damage = 10;
            f.callback = event -> {
                assertFalse(f.data.containsKey(BetterHorseKeys.NEUTERED));
                assertEquals(10, f.held.damage);
                if (event instanceof BetterHorseNeuterEvent neuter) {
                    assertTrue(neuter.isEntity());
                    assertSame(f.horse, neuter.getHorse());
                    assertNull(neuter.getHorseItem());
                }
            };
            assertTrue(f.neuter());
            assertEquals((byte) 1, f.data.get(BetterHorseKeys.NEUTERED));
            assertEquals(0, f.loveTicks);
            assertEquals(11, f.held.damage);
            assertEquals(1, f.count(BetterHorseNeuterEvent.class));
            assertEquals(1, f.count(PlayerItemDamageEvent.class));
            assertEquals(1, f.feedback);
        }
    }

    @Test void cancellingEitherEventLeavesFertilityAndToolUntouched() throws Exception {
        for (boolean cancelNeuter : new boolean[]{true, false}) {
            try (Fixture f = new Fixture()) {
                f.callback = event -> {
                    if (cancelNeuter && event instanceof BetterHorseNeuterEvent neuter) neuter.setCancelled(true);
                    if (!cancelNeuter && event instanceof PlayerItemDamageEvent wear) wear.setCancelled(true);
                };
                assertFalse(f.neuter());
                assertFalse(f.data.containsKey(BetterHorseKeys.NEUTERED));
                assertEquals(0, f.held.damage);
                assertEquals(400, f.loveTicks);
                assertEquals(0, f.feedback);
                assertEquals(cancelNeuter ? 0 : 1, f.count(PlayerItemDamageEvent.class));
            }
        }
    }

    @Test void foreignRegionIsRejectedBeforeAnyMutableEntityAccess() throws Exception {
        try (Fixture f = new Fixture()) {
            f.ownsHorse = false;
            assertFalse(f.neuter());
            assertTrue(f.events.isEmpty());
            f.ownsHorse = true;
            f.ownsPlayer = false;
            assertFalse(f.neuter());
            assertTrue(f.events.isEmpty());
        }
    }

    @Test void callbackOwnershipTransferCannotMutateForeignEntitiesOrConsumeTheTool() throws Exception {
        for (boolean afterWear : new boolean[]{false, true}) {
            try (Fixture f = new Fixture()) {
                f.callback = event -> {
                    if ((!afterWear && event instanceof BetterHorseNeuterEvent)
                            || (afterWear && event instanceof PlayerItemDamageEvent)) f.ownsHorse = false;
                };
                assertFalse(f.neuter());
                assertFalse(f.data.containsKey(BetterHorseKeys.NEUTERED));
                assertEquals(0, f.held.damage);
                assertEquals(400, f.loveTicks);
                assertEquals(0, f.feedback);
            }
        }
    }

    @Test void callbackDisablingGlobalNeuteringOrThePluginPreventsCommit() throws Exception {
        for (boolean disablePlugin : new boolean[]{false, true}) {
            try (Fixture f = new Fixture()) {
                f.callback = event -> {
                    if (event instanceof BetterHorseNeuterEvent) {
                        if (disablePlugin) f.enabled(false);
                        else f.config.set("horse-neutering.enabled", false);
                    }
                };
                assertFalse(f.neuter());
                assertFalse(f.data.containsKey(BetterHorseKeys.NEUTERED));
                assertEquals(0, f.held.damage);
                assertEquals(0, f.count(PlayerItemDamageEvent.class));
            }
        }
    }

    @Test void creativeUnbreakableAndUnbreakingCanAvoidWearWithoutSkippingNeutering() throws Exception {
        for (int kind = 0; kind < 3; kind++) {
            try (Fixture f = new Fixture()) {
                if (kind == 0) f.mode = GameMode.CREATIVE;
                if (kind == 1) f.held.unbreakable = true;
                if (kind == 2) f.held.unbreaking = 3;
                assertTrue(new HorseNeuteringService(f.plugin, () -> 0.99D).neuter(f.player, f.horse, EquipmentSlot.HAND));
                assertEquals((byte) 1, f.data.get(BetterHorseKeys.NEUTERED));
                assertEquals(0, f.held.damage);
                assertEquals(0, f.count(PlayerItemDamageEvent.class));
            }
        }
    }

    @Test void toolBreakCallbackMayTransferOwnershipWithoutPostCommitForeignAccess() throws Exception {
        try (Fixture f = new Fixture()) {
            f.held.damage = Material.SHEARS.getMaxDurability() - 1;
            f.callback = event -> { if (event instanceof PlayerItemBreakEvent) f.ownsHorse = false; };
            assertTrue(f.neuter());
            assertEquals((byte) 1, f.data.get(BetterHorseKeys.NEUTERED));
            assertEquals(Material.AIR, f.current.getType());
            assertEquals(1, f.count(PlayerItemBreakEvent.class));
            assertEquals(0, f.feedback);
        }
    }

    @Test void nativeOwnerFallbackBypassAndAlreadyNeuteredProtectionRemainEffective() throws Exception {
        try (Fixture f = new Fixture()) {
            f.data.put(BetterHorseKeys.OWNER, "damaged-owner-id");
            f.nativeOwner = UUID.randomUUID();
            assertFalse(f.neuter());
            assertFalse(f.data.containsKey(BetterHorseKeys.NEUTERED));
            f.nativeOwner = f.playerId;
            assertTrue(f.neuter());
            int damage = f.held.damage;
            assertFalse(f.neuter());
            assertEquals(damage, f.held.damage);
        }
        try (Fixture f = new Fixture()) {
            f.nativeOwner = UUID.randomUUID();
            f.bypass = true;
            assertTrue(f.neuter());
        }
    }

    public static final class TestPlugin extends BetterHorses {
        FileConfiguration configuration;
        MessageLanguage language;
        @Override public FileConfiguration getConfig() { return configuration; }
        @Override public LanguageManager getLang() { return language; }
        @Override public void refreshMountShaders(Entity entity) { }
    }

    public static final class MessageLanguage extends LanguageManager {
        Fixture fixture;
        private MessageLanguage() { super(null, null); }
        @Override public String getRaw(OfflinePlayer player, String key) { return "Horse"; }
        @Override public void send(Player player, String key) { if (key.equals("messages.successfully-castrated")) fixture.feedback++; }
        @Override public void sendFormatted(Player player, String key, Object... replacements) { }
    }

    private static final class Fixture implements AutoCloseable {
        final Field serverField = field(Bukkit.class, "server"), pluginField = field(BetterHorses.class, "instance");
        final Field policy = field(WorldAccessPolicy.class, "snapshot");
        final Object previousServer = serverField.get(null), previousPlugin = pluginField.get(null);
        final Object previousPolicy = policy.get(null);
        final FileConfiguration config = new YamlConfiguration();
        final Map<NamespacedKey, Object> data = new HashMap<>();
        final List<Event> events = new ArrayList<>();
        final UUID playerId = UUID.randomUUID(), horseId = UUID.randomUUID();
        final TestPlugin plugin;
        final Player player;
        final Horse horse;
        Tool held = new Tool();
        ItemStack current = held;
        UUID nativeOwner = playerId;
        GameMode mode = GameMode.SURVIVAL;
        boolean ownsHorse = true, ownsPlayer = true, bypass;
        int loveTicks = 400, feedback;
        Consumer<Event> callback = event -> { };

        Fixture() throws Exception {
            Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
            plugin = (TestPlugin) unsafe.allocateInstance(TestPlugin.class);
            plugin.configuration = config;
            plugin.language = (MessageLanguage) unsafe.allocateInstance(MessageLanguage.class);
            plugin.language.fixture = this;
            PluginDescriptionFile description = new PluginDescriptionFile("BetterHorses", "test", TestPlugin.class.getName());
            field(JavaPlugin.class, "description").set(plugin, description);
            field(JavaPlugin.class, "pluginMeta").set(plugin, description);
            enabled(true);
            pluginField.set(null, plugin);
            config.set("horse-neutering.enabled", true);
            config.set("horse-neutering.shears-enabled", true);
            WorldAccessPolicy.reload(plugin);
            World world = proxy(World.class, (p, m, a) -> switch (m.getName()) {
                case "getName" -> "world";
                case "getUID" -> UUID.fromString("00000000-0000-0000-0000-000000000001");
                default -> zero(m.getReturnType());
            });
            Location position = new Location(world, 0, 64, 0);
            PersistentDataContainer pdc = proxy(PersistentDataContainer.class, (p, m, a) -> switch (m.getName()) {
                case "get" -> data.get(a[0]);
                case "set" -> { data.put((NamespacedKey) a[0], a[2]); yield null; }
                default -> zero(m.getReturnType());
            });
            PlayerInventory inventory = proxy(PlayerInventory.class, (p, m, a) -> switch (m.getName()) {
                case "getItemInMainHand" -> current;
                case "setItemInMainHand" -> { current = (ItemStack) a[0]; if (current instanceof Tool tool) held = tool; yield null; }
                default -> zero(m.getReturnType());
            });
            player = proxy(Player.class, (p, m, a) -> {
                if (m.getName().equals("getUniqueId")) return playerId;
                if (!ownsPlayer) throw new AssertionError("Foreign player access: " + m);
                return switch (m.getName()) {
                    case "isSneaking", "isOnline" -> true;
                    case "getWorld" -> world;
                    case "getLocation" -> position;
                    case "getInventory" -> inventory;
                    case "getGameMode" -> mode;
                    case "hasPermission" -> !a[0].equals("betterhorses.bypass") || bypass;
                    default -> zero(m.getReturnType());
                };
            });
            horse = proxy(Horse.class, (p, m, a) -> {
                if (m.getName().equals("getUniqueId")) return horseId;
                if (!ownsHorse) throw new AssertionError("Foreign horse access: " + m);
                return switch (m.getName()) {
                    case "getType" -> EntityType.HORSE;
                    case "isValid", "isTamed" -> true;
                    case "getWorld" -> world;
                    case "getLocation" -> position;
                    case "getPersistentDataContainer" -> pdc;
                    case "getOwner" -> proxy(AnimalTamer.class, (pp, mm, aa) -> mm.getName().equals("getUniqueId") ? nativeOwner : zero(mm.getReturnType()));
                    case "setLoveModeTicks" -> { loveTicks = (int) a[0]; yield null; }
                    default -> zero(m.getReturnType());
                };
            });
            PluginManager manager = proxy(PluginManager.class, (p, m, a) -> {
                if (m.getName().equals("callEvent")) { events.add((Event) a[0]); callback.accept((Event) a[0]); }
                return zero(m.getReturnType());
            });
            Server server = proxy(Server.class, (p, m, a) -> switch (m.getName()) {
                case "isOwnedByCurrentRegion" -> a[0] == player ? ownsPlayer : a[0] == horse && ownsHorse;
                case "getPluginManager" -> manager;
                default -> zero(m.getReturnType());
            });
            serverField.set(null, server);
        }

        void enabled(boolean enabled) { try { field(JavaPlugin.class, "isEnabled").setBoolean(plugin, enabled); } catch (IllegalAccessException e) { throw new AssertionError(e); } }
        boolean neuter() { return new HorseNeuteringService(plugin, () -> 0.0D).neuter(player, horse, EquipmentSlot.HAND); }
        long count(Class<?> type) { return events.stream().filter(type::isInstance).count(); }
        @Override public void close() throws Exception {
            serverField.set(null, previousServer); pluginField.set(null, previousPlugin);
            policy.set(null, previousPolicy);
        }
    }

    private static final class Tool extends ItemStack {
        int damage, unbreaking;
        boolean unbreakable;
        Tool() { super(Material.SHEARS); }
        @Override public Tool clone() { Tool copy = new Tool(); copy.damage = damage; copy.unbreaking = unbreaking; copy.unbreakable = unbreakable; return copy; }
        @Override public ItemMeta getItemMeta() {
            int[] copiedDamage = {damage};
            return proxy(Damageable.class, (p, m, a) -> switch (m.getName()) {
                case "getDamage" -> copiedDamage[0];
                case "setDamage" -> { copiedDamage[0] = (int) a[0]; yield null; }
                case "getEnchantLevel" -> unbreaking;
                case "isUnbreakable" -> unbreakable;
                default -> zero(m.getReturnType());
            });
        }
        @Override public boolean setItemMeta(ItemMeta meta) { damage = ((Damageable) meta).getDamage(); return true; }
        @Override public boolean equals(Object other) { return other instanceof Tool tool && tool.damage == damage && tool.unbreaking == unbreaking && tool.unbreakable == unbreakable; }
        @Override public int hashCode() { return Objects.hash(damage, unbreaking, unbreakable); }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if (m.getDeclaringClass() == Object.class) return switch (m.getName()) {
                case "equals" -> p == a[0]; case "hashCode" -> System.identityHashCode(p); default -> type.getSimpleName();
            };
            return handler.invoke(p, m, a);
        }));
    }
    private static Field field(Class<?> type, String name) { try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; } catch (ReflectiveOperationException e) { throw new AssertionError(e); } }
    private static Object zero(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        if (type == float.class) return 0F;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        return null;
    }
}
