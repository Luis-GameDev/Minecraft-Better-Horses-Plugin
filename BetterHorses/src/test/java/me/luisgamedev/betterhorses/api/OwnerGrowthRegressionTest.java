package me.luisgamedev.betterhorses.api;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.growing.HorseGrowthManager;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.listeners.HorseMountListener;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OwnerGrowthRegressionTest {
    @Test void nativeOwnerIsProtectedWithoutRequiringPluginOwnershipMetadata() throws Exception {
        try (Fixture f = new Fixture()) {
            f.owner = UUID.randomUUID();
            EntityMountEvent attempt = new EntityMountEvent(f.player, f.horse);
            new HorseMountListener().onHorseMount(attempt);
            assertTrue(attempt.isCancelled());
            assertFalse(f.values.containsKey(BetterHorseKeys.OWNER));
            f.owner = f.playerId;
            EntityMountEvent ownerAttempt = new EntityMountEvent(f.player, f.horse);
            new HorseMountListener().onHorseMount(ownerAttempt);
            assertFalse(ownerAttempt.isCancelled());
        }
    }

    @Test void malformedMetadataFallsBackToNativeOwnerAndUuidComparisonIsCaseInsensitive() throws Exception {
        try (Fixture f = new Fixture()) {
            f.owner = UUID.randomUUID();
            f.values.put(BetterHorseKeys.OWNER, "damaged-owner-id");
            EntityMountEvent denied = new EntityMountEvent(f.player, f.horse);
            new HorseMountListener().onHorseMount(denied);
            assertTrue(denied.isCancelled());
            f.values.put(BetterHorseKeys.OWNER, f.playerId.toString().toUpperCase(java.util.Locale.ROOT));
            EntityMountEvent allowed = new EntityMountEvent(f.player, f.horse);
            new HorseMountListener().onHorseMount(allowed);
            assertFalse(allowed.isCancelled());
        }
    }

    @Test void ownerlessMountsBypassPermissionAndDisabledRestrictionRetainTheirBehavior() throws Exception {
        try (Fixture f = new Fixture()) {
            EntityMountEvent wild = new EntityMountEvent(f.player, f.horse);
            new HorseMountListener().onHorseMount(wild);
            assertFalse(wild.isCancelled());
            f.owner = UUID.randomUUID();
            f.bypass = true;
            EntityMountEvent bypass = new EntityMountEvent(f.player, f.horse);
            new HorseMountListener().onHorseMount(bypass);
            assertFalse(bypass.isCancelled());
            f.bypass = false;
            f.config.set("settings.restrict-mounting-to-owner", false);
            EntityMountEvent unrestricted = new EntityMountEvent(f.player, f.horse);
            new HorseMountListener().onHorseMount(unrestricted);
            assertFalse(unrestricted.isCancelled());
        }
    }

    @Test void explicitGrowthEditsImmediatelyDemoteAndPromoteAtTheConfiguredThreshold() throws Exception {
        try (Fixture f = new Fixture()) {
            f.mounted = true;
            f.loveMode = 400;
            f.values.put(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, 1.08D);
            BetterHorse api = new BetterHorse(f.horse);
            long started = System.currentTimeMillis();
            api.setGrowthStage(1);
            assertFalse(f.adult);
            assertTrue(f.ageLocked);
            assertFalse(f.mounted);
            assertEquals(1, f.ejections);
            assertEquals(0, f.loveMode);
            assertEquals(1, api.getGrowthStage().orElseThrow());
            assertTrue((long) f.values.get(new NamespacedKey("betterhorses", "growth_updated_at")) >= started);
            assertEquals(1.404D, f.scale, 1e-12);
            assertEquals(1.08D, f.values.get(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER));

            api.setGrowthStage(7);
            assertTrue(f.adult);
            assertFalse(f.ageLocked);
            assertEquals(1.17D, f.scale, 1e-12);
            assertEquals(1, f.ejections);
        }
    }

    @Test void customRideAndBreedThresholdControlsTheApiAgeTransition() throws Exception {
        try (Fixture f = new Fixture()) {
            f.config.set("horse-growth-settings.ride-and-breed-threshhold", 4);
            BetterHorse api = new BetterHorse(f.horse);
            api.setGrowthStage(3);
            assertFalse(f.adult);
            assertTrue(f.ageLocked);
            api.setGrowthStage(4);
            assertTrue(f.adult);
            assertFalse(f.ageLocked);
        }
    }

    @Test void rejectedGrowthStagesCannotChangeMetadataOrNativeAge() throws Exception {
        try (Fixture f = new Fixture()) {
            BetterHorse api = new BetterHorse(f.horse);
            assertThrows(IllegalArgumentException.class, () -> api.setGrowthStage(0));
            assertThrows(IllegalArgumentException.class, () -> api.setGrowthStage(11));
            assertTrue(f.adult);
            assertTrue(f.values.isEmpty());
            assertEquals(2.0D, f.scale);
        }
    }

    @Test void disabledCustomGrowthDoesNotOverrideNativeAgeOrExternalScale() throws Exception {
        try (Fixture f = new Fixture()) {
            f.config.set("horse-growth-settings.enabled", false);
            new BetterHorse(f.horse).setGrowthStage(1);
            assertTrue(f.adult);
            assertFalse(f.ageLocked);
            assertEquals(2.0D, f.scale);
            assertEquals(1, f.values.get(BetterHorseKeys.GROWTH_STAGE));
        }
    }

    @Test void managerRepairsAdultEntitiesWithPersistedJuvenileStage() throws Exception {
        try (Fixture f = new Fixture()) {
            f.values.put(BetterHorseKeys.GROWTH_STAGE, 1);
            f.values.put(new NamespacedKey("betterhorses", "growth_updated_at"), System.currentTimeMillis());
            f.values.put(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, 1.08D);
            Method process = HorseGrowthManager.class.getDeclaredMethod("processGrowth",
                    org.bukkit.entity.AbstractHorse.class, SupportedMountType.class, FileConfiguration.class);
            process.setAccessible(true);
            process.invoke(new HorseGrowthManager(f.plugin), f.horse, SupportedMountType.HORSE, f.config);
            assertFalse(f.adult);
            assertTrue(f.ageLocked);
            assertEquals(1, f.values.get(BetterHorseKeys.GROWTH_STAGE));
            assertEquals(1.404D, f.scale, 1e-12);
        }
    }

    @Test void occupiedGrowthRemainsPaused() throws Exception {
        try (Fixture f = new Fixture()) {
            f.adult = false;
            f.mounted = true;
            f.values.put(BetterHorseKeys.GROWTH_STAGE, 1);
            f.values.put(new NamespacedKey("betterhorses", "growth_updated_at"), 1L);
            Method process = HorseGrowthManager.class.getDeclaredMethod("processGrowth",
                    org.bukkit.entity.AbstractHorse.class, SupportedMountType.class, FileConfiguration.class);
            process.setAccessible(true);
            process.invoke(new HorseGrowthManager(f.plugin), f.horse, SupportedMountType.HORSE, f.config);
            assertEquals(1, f.values.get(BetterHorseKeys.GROWTH_STAGE));
            assertFalse(f.adult);
        }
    }

    @Test void privateInspectionDoesNotPauseGrowthButARealRiderStillDoes() throws Exception {
        try (Fixture f = new Fixture()) {
            f.adult = false;
            f.config.set("horse-growth-settings.time-until-adult", 3);
            NamespacedKey marker = new NamespacedKey("betterhorses", "inspection_display");
            PersistentDataContainer markerData = proxy(PersistentDataContainer.class, (p, m, a) -> {
                if (m.getName().equals("has")) return marker.equals(a[0]);
                return defaultValue(m.getReturnType());
            });
            org.bukkit.entity.TextDisplay display = proxy(org.bukkit.entity.TextDisplay.class, (p, m, a) ->
                    m.getName().equals("getPersistentDataContainer") ? markerData : defaultValue(m.getReturnType()));
            f.passengers = List.of(display);
            f.values.put(BetterHorseKeys.GROWTH_STAGE, 1);
            NamespacedKey updated = new NamespacedKey("betterhorses", "growth_updated_at");
            f.values.put(updated, System.currentTimeMillis() - 25_000L);
            Method process = HorseGrowthManager.class.getDeclaredMethod("processGrowth",
                    org.bukkit.entity.AbstractHorse.class, SupportedMountType.class, FileConfiguration.class);
            process.setAccessible(true);
            process.invoke(new HorseGrowthManager(f.plugin), f.horse, SupportedMountType.HORSE, f.config);
            assertEquals(2, f.values.get(BetterHorseKeys.GROWTH_STAGE));

            f.passengers = List.of(display, f.player);
            f.values.put(updated, System.currentTimeMillis() - 25_000L);
            process.invoke(new HorseGrowthManager(f.plugin), f.horse, SupportedMountType.HORSE, f.config);
            assertEquals(2, f.values.get(BetterHorseKeys.GROWTH_STAGE));
            assertFalse(f.adult);
        }
    }

    public static final class TestPlugin extends BetterHorses {
        FileConfiguration config;
        SilentLanguage language;
        @Override public FileConfiguration getConfig() { return config; }
        @Override public LanguageManager getLang() { return language; }
        @Override public void refreshMountShaders(org.bukkit.entity.Entity mount) { }
    }

    public static final class SilentLanguage extends LanguageManager {
        private SilentLanguage() { super(null, null); }
        @Override public void sendFormatted(Player player, String key, Object... replacements) { }
    }

    private static final class Fixture implements AutoCloseable {
        final Field pluginField = field(BetterHorses.class, "instance"), serverField = field(Bukkit.class, "server");
        final Object previousPlugin = pluginField.get(null), previousServer = serverField.get(null);
        final FileConfiguration config = new YamlConfiguration();
        final Map<NamespacedKey, Object> values = new HashMap<>();
        final UUID playerId = UUID.randomUUID();
        final TestPlugin plugin;
        final Horse horse;
        final Player player;
        UUID owner;
        boolean adult = true, ageLocked, mounted, bypass;
        List<org.bukkit.entity.Entity> passengers;
        int loveMode, ejections;
        double scale = 2.0D;

        Fixture() throws Exception {
            try {
                Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
                Server server = proxy(Server.class, (p, m, a) -> switch (m.getName()) {
                    case "getBukkitVersion" -> "1.20.6-R0.1-SNAPSHOT";
                    case "getName" -> "Paper";
                    default -> defaultValue(m.getReturnType());
                });
                serverField.set(null, server);
                plugin = (TestPlugin) unsafe.allocateInstance(TestPlugin.class);
                plugin.config = config;
                plugin.language = (SilentLanguage) unsafe.allocateInstance(SilentLanguage.class);
                PluginDescriptionFile description = new PluginDescriptionFile("BetterHorses", "test", TestPlugin.class.getName());
                field(JavaPlugin.class, "description").set(plugin, description);
                field(JavaPlugin.class, "pluginMeta").set(plugin, description);
                pluginField.set(null, plugin);
                config.set("settings.restrict-mounting-to-owner", true);
                config.set("horse-growth-settings.enabled", true);
                config.set("horse-growth-settings.ride-and-breed-threshhold", 7);
                config.set("horse-growth-settings.max-size", 1.3D);

                PersistentDataContainer data = proxy(PersistentDataContainer.class, (p, m, a) -> {
                    NamespacedKey key = a != null && a.length > 0 && a[0] instanceof NamespacedKey k ? k : null;
                    return switch (m.getName()) {
                        case "set" -> { values.put(key, a[2]); yield null; }
                        case "get" -> values.get(key);
                        case "getOrDefault" -> values.getOrDefault(key, a[2]);
                        case "has" -> values.containsKey(key);
                        case "remove" -> { values.remove(key); yield null; }
                        case "getKeys" -> Set.copyOf(values.keySet());
                        case "isEmpty" -> values.isEmpty();
                        default -> defaultValue(m.getReturnType());
                    };
                });
                AttributeInstance attribute = proxy(AttributeInstance.class, (p, m, a) -> switch (m.getName()) {
                    case "getBaseValue", "getValue" -> scale;
                    case "setBaseValue" -> { scale = (double) a[0]; yield null; }
                    default -> defaultValue(m.getReturnType());
                });
                player = proxy(Player.class, (p, m, a) -> switch (m.getName()) {
                    case "getUniqueId" -> playerId;
                    case "hasPermission" -> "betterhorses.bypass".equals(a[0]) ? bypass : true;
                    default -> defaultValue(m.getReturnType());
                });
                horse = proxy(Horse.class, (p, m, a) -> switch (m.getName()) {
                    case "getPersistentDataContainer" -> data;
                    case "getOwner" -> owner == null ? null : proxy(AnimalTamer.class, (pp, mm, aa) -> mm.getName().equals("getUniqueId") ? owner : defaultValue(mm.getReturnType()));
                    case "getType" -> EntityType.HORSE;
                    case "getAttribute" -> attribute;
                    case "isAdult" -> adult;
                    case "setAdult" -> { adult = true; yield null; }
                    case "setBaby" -> { adult = false; yield null; }
                    case "setAgeLock" -> { ageLocked = (boolean) a[0]; yield null; }
                    case "setLoveModeTicks" -> { loveMode = (int) a[0]; yield null; }
                    case "eject" -> { mounted = false; ejections++; yield true; }
                    case "getPassengers" -> passengers != null ? passengers : mounted ? List.of(player) : List.of();
                    default -> defaultValue(m.getReturnType());
                });
            } catch (Exception | Error failure) { close(); throw failure; }
        }

        @Override public void close() throws Exception {
            pluginField.set(null, previousPlugin);
            serverField.set(null, previousServer);
        }
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Field field(Class<?> type, String name) {
        try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
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
}
