package me.luisgamedev.betterhorses.growing;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HorseGrowthManagerTest {
    @Test void matureHorsesAndNotYetDueJuvenilesReceiveNoRecurringWork() throws Exception {
        try (Fixture f = new Fixture()) {
            Mount mature = f.mount(10, 0);
            Mount juvenile = f.mount(5, 0);
            f.manager.reconcile(mature.horse);
            f.manager.reconcile(juvenile.horse);
            mature.clearCounters(); juvenile.clearCounters();
            for (int second = 1; second < 20; second++) {
                f.now = 1_000L + second * 1_000L;
                f.manager.tick();
            }
            assertEquals(0, mature.reads);
            assertEquals(0, mature.writes);
            assertEquals(0, mature.scaleWrites);
            assertEquals(0, juvenile.reads);
            assertEquals(0, juvenile.writes);
            assertEquals(0, juvenile.scaleWrites);
            assertEquals(0, f.worldScans, "steady-state work must not enumerate world entities");
            f.now = 21_000L;
            f.manager.tick();
            assertEquals(6, juvenile.stage());
            assertEquals(0, mature.reads);
        }
    }

    @Test void onlyDueHorsesAreProcessedAndFinishingGrowthRemovesRecurringWork() throws Exception {
        try (Fixture f = new Fixture()) {
            Mount finishing = f.mount(9, 15_000L);
            Mount later = f.mount(5, 0);
            f.manager.reconcile(finishing.horse);
            f.manager.reconcile(later.horse);
            finishing.clearCounters(); later.clearCounters();
            f.now = 6_000L;
            f.manager.tick();
            assertEquals(10, finishing.stage());
            assertEquals(0, later.reads);
            assertEquals(0, later.writes);
            finishing.clearCounters();
            f.now = 41_000L;
            f.manager.tick();
            assertEquals(7, later.stage());
            assertEquals(0, finishing.reads);
            assertEquals(0, finishing.writes);
            assertEquals(0, finishing.scaleWrites);
        }
    }

    @Test void defaultUnloadPolicyRetainsHalfAnIntervalAcrossLoading() throws Exception {
        try (Fixture f = new Fixture()) {
            Mount mount = f.mount(5, 0);
            f.manager.reconcile(mount.horse);
            f.now = 11_000L;
            f.unload(mount);
            assertEquals(5, mount.stage());
            assertEquals(10_000L, mount.elapsed());
            f.now = 611_000L;
            f.load(mount);
            assertEquals(5, mount.stage(), "unloaded wall-clock time is excluded by default");
            assertEquals(10_000L, mount.elapsed());
            f.now = 620_999L;
            f.manager.tick();
            assertEquals(5, mount.stage());
            f.now = 621_000L;
            f.manager.tick();
            assertEquals(6, mount.stage());
            assertEquals(0L, mount.elapsed());
        }
    }

    @Test void optionalUnloadGrowthCatchesUpAndRetainsTheRemainder() throws Exception {
        try (Fixture f = new Fixture(true)) {
            Mount mount = f.mount(5, 0);
            f.manager.reconcile(mount.horse);
            f.now = 11_000L;
            f.unload(mount);
            f.now = 56_000L;
            f.load(mount);
            assertEquals(7, mount.stage());
            assertEquals(15_000L, mount.elapsed());
            f.now = 61_000L;
            f.manager.tick();
            assertEquals(8, mount.stage());
        }
    }

    @Test void mountedTimeIsPausedWithoutDiscardingPartialProgress() throws Exception {
        try (Fixture f = new Fixture()) {
            Mount mount = f.mount(7, 0);
            Entity rider = proxy(Entity.class, (target, method, args) -> zero(method.getReturnType()));
            f.manager.reconcile(mount.horse);
            f.now = 11_000L;
            mount.passengers.add(rider);
            f.manager.onMount(new EntityMountEvent(rider, mount.horse));
            f.runDeferred();
            assertEquals(10_000L, mount.elapsed());
            mount.clearCounters();
            f.now = 611_000L;
            f.manager.tick();
            assertEquals(0, mount.reads);
            mount.passengers.clear();
            f.manager.onDismount(new EntityDismountEvent(rider, mount.horse));
            f.runDeferred();
            assertEquals(7, mount.stage());
            assertEquals(10_000L, mount.elapsed());
            f.now = 621_000L;
            f.manager.tick();
            assertEquals(8, mount.stage());
        }
    }

    @Test void anInspectionDisplayDoesNotPauseGrowth() throws Exception {
        try (Fixture f = new Fixture()) {
            Mount mount = f.mount(5, 0);
            Map<NamespacedKey, Object> displayValues = new HashMap<>();
            displayValues.put(new NamespacedKey("betterhorses", "inspection_display"), (byte) 1);
            PersistentDataContainer displayData = container(displayValues, () -> { });
            TextDisplay display = proxy(TextDisplay.class, (target, method, args) -> switch (method.getName()) {
                case "getPersistentDataContainer" -> displayData;
                default -> zero(method.getReturnType());
            });
            mount.passengers.add(display);
            f.manager.reconcile(mount.horse);
            f.now = 21_000L;
            f.manager.tick();
            assertEquals(6, mount.stage());
        }
    }

    @Test void reloadCheckpointsTheOldIntervalAndPreservesEarnedMilliseconds() throws Exception {
        try (Fixture f = new Fixture()) {
            Mount mount = f.mount(5, 0);
            f.manager.reconcile(mount.horse);
            f.now = 11_000L;
            f.manager.prepareReload();
            assertEquals(10_000L, mount.elapsed());
            f.config.set("horse-growth-settings.time-until-adult", 6);
            f.manager.reload();
            assertEquals(5, mount.stage());
            assertEquals(10_000L, mount.elapsed());
            f.now = 40_999L;
            f.manager.tick();
            assertEquals(5, mount.stage());
            f.now = 41_000L;
            f.manager.tick();
            assertEquals(6, mount.stage());
        }
    }

    @Test void shutdownPreservesPartialGrowthAndDefaultPolicyExcludesServerDowntime() throws Exception {
        try (Fixture f = new Fixture()) {
            Mount mount = f.mount(5, 0);
            f.manager.reconcile(mount.horse);
            f.now = 11_000L;
            f.manager.shutdown();
            assertEquals(10_000L, mount.elapsed());
            f.now = 611_000L;
            HorseGrowthManager restarted = new HorseGrowthManager(f.plugin, () -> f.now);
            try {
                restarted.reconcile(mount.horse);
                assertEquals(5, mount.stage());
                assertEquals(10_000L, mount.elapsed());
                f.now = 621_000L;
                restarted.tick();
                assertEquals(6, mount.stage());
            } finally { restarted.shutdown(); }
        }
    }

    @Test void matureSizeIsReconciledOnReloadWithoutReactivatingItsGrowthTask() throws Exception {
        try (Fixture f = new Fixture()) {
            Mount mature = f.mount(10, 0);
            f.manager.reconcile(mature.horse);
            assertEquals(1.3D, mature.scale, 1e-12);
            f.manager.prepareReload();
            f.config.set("horse-growth-settings.max-size", 1.6D);
            f.manager.reload();
            assertEquals(1.6D, mature.scale, 1e-12);
            mature.clearCounters();
            f.now = 1_001_000L;
            f.manager.tick();
            assertEquals(0, mature.reads);
            assertEquals(0, mature.writes);
            f.manager.prepareReload();
            f.config.set("horse-growth-settings.enabled", false);
            f.manager.reload();
            assertFalse(mature.values.containsKey(BetterHorseKeys.GROWTH_STAGE));
            assertFalse(mature.values.containsKey(BetterHorseKeys.GROWTH_PROGRESS_MILLIS));
            assertEquals(1.0D, mature.scale, 1e-12);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Field instance = field(BetterHorses.class, "instance");
        final Field serverField = field(Bukkit.class, "server");
        final Object previousPlugin = instance.get(null), previousServer = serverField.get(null);
        final FileConfiguration config = new YamlConfiguration();
        final List<Mount> mounts = new ArrayList<>();
        final List<Deferred> deferred = new ArrayList<>();
        final TestPlugin plugin;
        final World world;
        final Chunk chunk;
        final HorseGrowthManager manager;
        long now = 1_000L;
        int worldScans;

        Fixture() throws Exception { this(false); }

        Fixture(boolean growWhileUnloaded) throws Exception {
            Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
            plugin = (TestPlugin) unsafe.allocateInstance(TestPlugin.class);
            plugin.configuration = config;
            PluginDescriptionFile description = new PluginDescriptionFile("BetterHorses", "test", TestPlugin.class.getName());
            field(JavaPlugin.class, "description").set(plugin, description);
            field(JavaPlugin.class, "pluginMeta").set(plugin, description);
            field(JavaPlugin.class, "isEnabled").setBoolean(plugin, true);
            instance.set(null, plugin);
            world = proxy(World.class, (target, method, args) -> switch (method.getName()) {
                case "getName" -> "world";
                case "getUID" -> UUID.fromString("00000000-0000-0000-0000-000000000001");
                case "getEntitiesByClass", "getEntities" -> {
                    worldScans++;
                    yield mounts.stream().map(mount -> mount.horse).toList();
                }
                case "equals" -> target == args[0];
                case "hashCode" -> System.identityHashCode(target);
                default -> zero(method.getReturnType());
            });
            chunk = proxy(Chunk.class, (target, method, args) -> method.getName().equals("getWorld")
                    ? world : zero(method.getReturnType()));
            BukkitScheduler scheduler = proxy(BukkitScheduler.class, (target, method, args) -> {
                if (method.getName().startsWith("runTask")) {
                    Deferred task = new Deferred((Runnable) args[1], args.length > 3);
                    deferred.add(task);
                    return task.handle;
                }
                return zero(method.getReturnType());
            });
            PluginManager plugins = proxy(PluginManager.class, (target, method, args) -> zero(method.getReturnType()));
            Server server = proxy(Server.class, (target, method, args) -> switch (method.getName()) {
                case "getScheduler" -> scheduler;
                case "getPluginManager" -> plugins;
                case "getWorlds" -> List.of(world);
                case "getBukkitVersion", "getMinecraftVersion" -> "1.20.6-R0.1-SNAPSHOT";
                case "isPrimaryThread" -> true;
                default -> zero(method.getReturnType());
            });
            serverField.set(null, server);
            field(JavaPlugin.class, "server").set(plugin, server);
            config.set("horse-growth-settings.enabled", true);
            config.set("horse-growth-settings.time-until-adult", 3);
            config.set("horse-growth-settings.grow-while-unloaded", growWhileUnloaded);
            config.set("random-size-factor.enabled", false);
            WorldAccessPolicy.reload(plugin);
            manager = new HorseGrowthManager(plugin, () -> now);
        }

        Mount mount(int stage, long elapsed) {
            Mount mount = new Mount(this, stage, elapsed);
            mounts.add(mount);
            return mount;
        }

        void unload(Mount mount) { manager.onEntitiesUnloaded(new EntitiesUnloadEvent(chunk, List.of(mount.horse))); }
        void load(Mount mount) {
            manager.onEntitiesLoaded(new EntitiesLoadEvent(chunk, List.of(mount.horse)));
            runDeferred();
        }
        void runDeferred() {
            for (Deferred task : List.copyOf(deferred)) if (!task.cancelled && !task.repeating) {
                task.cancelled = true;
                task.action.run();
            }
            deferred.removeIf(task -> task.cancelled);
        }
        @Override public void close() throws Exception {
            try { manager.shutdown(); }
            finally { instance.set(null, previousPlugin); serverField.set(null, previousServer); }
        }
    }

    private static final class Mount {
        final UUID id = UUID.randomUUID();
        final Map<NamespacedKey, Object> values = new HashMap<>();
        final List<Entity> passengers = new ArrayList<>();
        final Horse horse;
        boolean adult, valid = true;
        int reads, writes, scaleWrites;
        double scale = 1.0D;

        Mount(Fixture f, int stage, long elapsed) {
            adult = stage >= 7;
            PersistentDataContainer data = container(values, () -> writes++);
            AttributeInstance attribute = proxy(AttributeInstance.class, (target, method, args) -> switch (method.getName()) {
                case "getBaseValue" -> scale;
                case "setBaseValue" -> { scale = (Double) args[0]; scaleWrites++; yield null; }
                default -> zero(method.getReturnType());
            });
            horse = proxy(Horse.class, (target, method, args) -> {
                reads++;
                return switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getWorld" -> f.world;
                    case "getPersistentDataContainer" -> data;
                    case "getPassengers" -> List.copyOf(passengers);
                    case "getType" -> EntityType.HORSE;
                    case "isValid" -> valid;
                    case "isDead" -> !valid;
                    case "isAdult" -> adult;
                    case "setAdult" -> { adult = true; yield null; }
                    case "setBaby" -> { adult = false; yield null; }
                    case "getAttribute" -> attribute;
                    case "equals" -> target == args[0];
                    case "hashCode" -> System.identityHashCode(target);
                    default -> zero(method.getReturnType());
                };
            });
            HorseGrowthData.write(data, new GrowthState(stage, elapsed, f.now), HorseGrowthData.Availability.LOADED);
        }
        int stage() { return (Integer) values.get(BetterHorseKeys.GROWTH_STAGE); }
        long elapsed() { return (Long) values.get(BetterHorseKeys.GROWTH_PROGRESS_MILLIS); }
        void clearCounters() { reads = writes = scaleWrites = 0; }
    }

    public static class TestPlugin extends BetterHorses {
        FileConfiguration configuration;
        @Override public FileConfiguration getConfig() { return configuration; }
        @Override public void refreshMountShaders(Entity horse) { }
        @Override public void debugLog(String action, String checkpoint, boolean success, String details) { }
    }

    private static final class Deferred {
        final Runnable action;
        final boolean repeating;
        final BukkitTask handle;
        boolean cancelled;
        Deferred(Runnable action, boolean repeating) {
            this.action = action;
            this.repeating = repeating;
            handle = proxy(BukkitTask.class, (target, method, args) -> {
                if (method.getName().equals("cancel")) cancelled = true;
                return zero(method.getReturnType());
            });
        }
    }

    private static PersistentDataContainer container(Map<NamespacedKey, Object> values, Runnable onWrite) {
        return proxy(PersistentDataContainer.class, (target, method, args) -> switch (method.getName()) {
            case "get" -> values.get(args[0]);
            case "has" -> values.containsKey(args[0]);
            case "getOrDefault" -> values.getOrDefault(args[0], args[2]);
            case "getKeys" -> values.keySet();
            case "set" -> { values.put((NamespacedKey) args[0], args[2]); onWrite.run(); yield null; }
            case "remove" -> { values.remove(args[0]); onWrite.run(); yield null; }
            default -> zero(method.getReturnType());
        });
    }

    private static Field field(Class<?> owner, String name) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Object zero(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == double.class) return 0.0D;
        if (type == float.class) return 0.0F;
        if (type == long.class) return 0L;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        return 0;
    }
}
