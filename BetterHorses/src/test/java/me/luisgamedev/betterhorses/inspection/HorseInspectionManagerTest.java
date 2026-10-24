package me.luisgamedev.betterhorses.inspection;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the production manager through real Paper interfaces without a running Minecraft server. */
class HorseInspectionManagerTest {
    @Test void privacyAndPersistenceAreSetBeforeInsertionAndOnlyTheViewerSeesTheDisplay() {
        Fixture f = new Fixture();
        try {
            assertTrue(f.show());
            Actor display = f.lastDisplay();
            assertTrue(display.checkedBeforeInsertion);
            assertFalse(display.visibleByDefault);
            assertFalse(display.persistent);
            assertTrue(HorseInspectionManager.isInspectionDisplay(display.entity));
            assertTrue(display.properties.get("setText") instanceof String);
            assertTrue(((String) display.properties.get("setText")).contains("\u00a7x"));
            assertEquals(1, f.viewer.reveals);
            assertEquals(0, f.otherViewer.reveals);
            assertTrue(display.vehicle == f.horse.entity);
            assertEquals(200L, f.scheduler.expiration().delay);
            assertEquals(0.25D, (Float) display.properties.get("setViewRange"), 1e-6);
            assertEquals(2, f.scheduler.activeCount());
            f.scheduler.expiration().fire();
            assertFalse(display.valid);
            assertTrue(f.horse.passengers.isEmpty());
            assertEquals(0, f.scheduler.activeCount());
        } finally { f.close(); }
    }

    @Test void repeatedPeekReplacesTheOldDisplayAndOldExpirationCannotRemoveTheReplacement() {
        Fixture f = new Fixture();
        try {
            assertTrue(f.show());
            Actor first = f.lastDisplay();
            Task oldExpiration = f.scheduler.expiration();
            assertTrue(f.show());
            Actor replacement = f.lastDisplay();
            assertFalse(first.valid);
            assertTrue(oldExpiration.cancelled);
            oldExpiration.fire();
            assertTrue(replacement.valid);
            assertEquals(1, f.horse.passengers.size());
            f.scheduler.expiration().fire();
            assertFalse(replacement.valid);
            assertEquals(0, f.scheduler.activeCount());
        } finally { f.close(); }
    }

    @Test void partialSpawnAttachmentRevealAndSchedulingFailuresLeaveNoLiveDisplay() {
        for (String failure : new String[]{"configure", "spawn", "passenger", "reveal", "expiration", "monitor"}) {
            Fixture f = new Fixture();
            try {
                f.failure = failure;
                assertFalse(f.show(), failure);
                assertFalse(f.lastDisplay().valid, failure);
                assertFalse(f.lastDisplay().persistent, failure);
                assertFalse(f.lastDisplay().visibleByDefault, failure);
                assertTrue(f.horse.passengers.isEmpty(), failure);
                assertEquals(0, f.scheduler.activeCount());
            } finally { f.close(); }
        }
    }

    @Test void failedRemovalIsRetriedAndDoesNotPreventOtherViewersFromBeingCleaned() {
        Fixture f = new Fixture();
        try {
            assertTrue(f.show());
            Actor first = f.lastDisplay();
            assertTrue(f.manager.show(f.otherViewer.player(), f.horse.horse(), Component.text("Other viewer")));
            Actor second = f.lastDisplay();
            first.removalFailures = 1;
            f.manager.onConfigurationReload();
            assertFalse(first.valid);
            assertFalse(second.valid);
            assertEquals(0, f.scheduler.activeCount());

            assertTrue(f.show());
            Actor retry = f.lastDisplay();
            retry.removalFailures = 1;
            f.manager.hide(f.viewer.player());
            assertTrue(retry.valid);
            assertTrue(f.viewer.hides > 0);
            assertEquals(1, f.scheduler.activeCount());
            f.scheduler.monitor().fire();
            assertFalse(retry.valid);
            assertEquals(0, f.scheduler.activeCount());
        } finally { f.close(); }
    }

    @Test void removalSurvivesAThrowingTaskCancellationAndShutdownStillCleansEverySession() {
        Fixture f = new Fixture();
        try {
            assertTrue(f.show());
            f.scheduler.expiration().cancelFailures = 1;
            assertTrue(f.manager.show(f.otherViewer.player(), f.horse.horse(), Component.text("Other viewer")));
            f.manager.shutdown();
            assertTrue(f.displays.stream().noneMatch(actor -> actor.valid));
            assertEquals(0, f.scheduler.activeCount());
            assertFalse(f.show());
        } finally { f.close(); }
    }

    @Test void lifecycleSignalsRemoveTheDisplayAndCancelAllOwnedTasks() {
        List<Consumer<Fixture>> signals = List.of(
                f -> f.manager.onQuit(new PlayerQuitEvent(f.viewer.player(), "")),
                f -> f.manager.onKick(new PlayerKickEvent(f.viewer.player(), "", "")),
                f -> f.manager.onWorldChange(new PlayerChangedWorldEvent(f.viewer.player(), f.world.world)),
                f -> f.manager.onDeath(new EntityDeathEvent(f.horse.horse(), null, new ArrayList<>())),
                f -> f.manager.onLoveMode(new EntityEnterLoveModeEvent(f.horse.horse(), f.viewer.player(), 600)),
                f -> f.manager.onTeleport(new EntityTeleportEvent(f.horse.entity, f.horse.location(), f.horse.location())),
                f -> f.manager.onEntitiesUnload(new EntitiesUnloadEvent(f.chunk(), List.of(f.horse.entity))),
                f -> f.manager.onChunkUnload(new ChunkUnloadEvent(f.chunk())),
                f -> f.manager.onWorldUnload(new WorldUnloadEvent(f.world.world)),
                f -> f.manager.onMount(new EntityMountEvent(f.viewer.entity, f.horse.entity)),
                f -> f.manager.onDismount(new EntityDismountEvent(f.lastDisplay().entity, f.horse.entity)),
                f -> f.manager.hideMount(f.horse.horse()),
                f -> f.manager.onConfigurationReload(),
                f -> f.manager.shutdown());
        for (Consumer<Fixture> signal : signals) {
            Fixture f = new Fixture();
            try {
                assertTrue(f.show());
                signal.accept(f);
                assertFalse(f.lastDisplay().valid);
                assertEquals(0, f.scheduler.activeCount());
            } finally { f.close(); }
        }
    }

    @Test void activeMonitorRemovesInvalidDistantDetachedOrBreedingDisplays() {
        List<Consumer<Fixture>> changes = List.of(
                f -> f.viewer.online = false,
                f -> f.viewer.world = new TestWorld(f, "other_world"),
                f -> f.viewer.x = 40,
                f -> f.horse.valid = false,
                f -> f.horse.inLove = true,
                f -> f.lastDisplay().vehicle = null,
                f -> f.config.set("horse-inspection.enabled", false));
        for (Consumer<Fixture> change : changes) {
            Fixture f = new Fixture();
            try {
                assertTrue(f.show());
                change.accept(f);
                f.scheduler.monitor().fire();
                assertFalse(f.lastDisplay().valid);
                assertEquals(0, f.scheduler.activeCount());
            } finally { f.close(); }
        }
    }

    @Test void startupAndEntityLoadRemoveOnlyStaleTaggedDisplays() throws Exception {
        Fixture f = new Fixture();
        try {
            Actor stale = f.newDisplay();
            f.tag(stale);
            Actor unrelated = f.newDisplay();
            f.manager.start();
            assertFalse(stale.valid);
            assertTrue(unrelated.valid);
            assertEquals(0, f.scheduler.activeCount());
            Actor loaded = f.newDisplay();
            f.tag(loaded);
            f.manager.onEntitiesLoad(new EntitiesLoadEvent(f.chunk(), List.of(loaded.entity, unrelated.entity)));
            assertFalse(loaded.valid);
            assertTrue(unrelated.valid);
            assertTrue(f.show());
            Actor active = f.lastDisplay();
            f.manager.onEntitiesLoad(new EntitiesLoadEvent(f.chunk(), List.of(active.entity)));
            assertTrue(active.valid);
            Event event = (Event) Class.forName("com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent")
                    .getConstructor(Entity.class, World.class).newInstance(f.horse.entity, f.world.world);
            f.removalExecutor.execute(null, event);
            assertFalse(active.valid);
            assertEquals(0, f.scheduler.activeCount());
        } finally { f.close(); }
    }

    @Test void configurationBoundsAndBreedingGuardsAreAppliedBeforeSpawning() {
        Fixture f = new Fixture();
        try {
            f.config.set("horse-inspection.duration-seconds", Double.NaN);
            f.config.set("horse-inspection.height-offset", Double.POSITIVE_INFINITY);
            f.config.set("horse-inspection.view-range", Double.NaN);
            assertTrue(f.show());
            assertEquals(200L, f.scheduler.expiration().delay);
            Transformation transform = (Transformation) f.lastDisplay().properties.get("setTransformation");
            assertEquals(0.5D, transform.getTranslation().y(), 1e-6);
            assertEquals(0.25D, (Float) f.lastDisplay().properties.get("setViewRange"), 1e-6);
            f.manager.hide(f.viewer.player());
            f.horse.inLove = true;
            assertFalse(f.show());
            assertEquals(1, f.displays.size());
            f.horse.inLove = false;
            f.config.set("horse-inspection.duration-seconds", 0.25D);
            f.config.set("horse-inspection.height-offset", 999.0D);
            f.config.set("horse-inspection.view-range", 999.0D);
            assertTrue(f.show());
            assertEquals(5L, f.scheduler.expiration().delay);
            transform = (Transformation) f.lastDisplay().properties.get("setTransformation");
            assertEquals(8.0D, transform.getTranslation().y(), 1e-6);
            assertEquals(1.0D, (Float) f.lastDisplay().properties.get("setViewRange"), 1e-6);
        } finally { f.close(); }
    }

    private static Fixture current;
    private static final Logger LOGGER = Logger.getLogger("inspection-tests");
    private static final Server SERVER = proxy(Server.class, (self, method, args) -> switch (method.getName()) {
        case "getLogger" -> LOGGER;
        case "getName" -> "InspectionTestServer";
        case "getVersion", "getBukkitVersion", "getMinecraftVersion" -> "1.20.6";
        case "getScheduler" -> current.scheduler.scheduler;
        case "getWorlds" -> List.of(current.world.world);
        case "getPluginManager" -> current.pluginManager;
        default -> defaultValue(method.getReturnType());
    });

    private static final class Fixture {
        final YamlConfiguration config = new YamlConfiguration();
        final TestWorld world;
        final Actor viewer;
        final Actor otherViewer;
        final Actor horse;
        final List<Actor> displays = new ArrayList<>();
        final Scheduler scheduler = new Scheduler(this);
        final PluginManager pluginManager = proxy(PluginManager.class, (self, method, args) -> {
            if (method.getName().equals("registerEvent")) removalExecutor = (EventExecutor) args[3];
            return defaultValue(method.getReturnType());
        });
        final Plugin plugin = proxy(Plugin.class, (self, method, args) -> switch (method.getName()) {
            case "getName" -> "BetterHorses";
            case "getLogger" -> LOGGER;
            case "getServer" -> SERVER;
            case "isEnabled" -> true;
            default -> defaultValue(method.getReturnType());
        });
        final HorseInspectionManager manager;
        final Server previousServer;
        EventExecutor removalExecutor;
        String failure = "";

        Fixture() {
            current = this;
            previousServer = Bukkit.getServer();
            setServer(SERVER);
            world = new TestWorld(this, "world");
            viewer = new Actor(this, Player.class, world);
            otherViewer = new Actor(this, Player.class, world);
            horse = new Actor(this, AbstractHorse.class, world);
            manager = new HorseInspectionManager(plugin, () -> config);
        }

        boolean show() { return manager.show(viewer.player(), horse.horse(), Component.text("Horse health", TextColor.color(0x11AAEE))); }
        void close() { manager.shutdown(); setServer(previousServer); }
        Actor lastDisplay() { return displays.getLast(); }
        Actor newDisplay() { Actor display = new Actor(this, TextDisplay.class, world); displays.add(display); return display; }
        void tag(Actor display) { display.data.put(new NamespacedKey("betterhorses", "inspection_display"), (byte) 1); }
        Chunk chunk() { return proxy(Chunk.class, (self, method, args) -> switch (method.getName()) {
            case "getWorld" -> world.world;
            case "getX", "getZ" -> 0;
            default -> defaultValue(method.getReturnType());
        }); }
    }

    private static final class TestWorld {
        final UUID id = UUID.randomUUID();
        final World world;
        TestWorld(Fixture fixture, String name) {
            world = proxy(World.class, (self, method, args) -> switch (method.getName()) {
                case "getName" -> name;
                case "getUID" -> id;
                case "getEntitiesByClass" -> fixture.displays.stream().filter(actor -> actor.valid).map(actor -> actor.entity).toList();
                case "spawn" -> {
                    Actor display = fixture.newDisplay();
                    ((Consumer<TextDisplay>) args[2]).accept((TextDisplay) display.entity);
                    display.checkedBeforeInsertion = !display.persistent && !display.visibleByDefault
                            && HorseInspectionManager.isInspectionDisplay(display.entity);
                    if (fixture.failure.equals("spawn")) throw new IllegalStateException("spawn failed after preparation");
                    yield display.entity;
                }
                default -> defaultValue(method.getReturnType());
            });
        }
    }

    private static final class Actor {
        final UUID id = UUID.randomUUID();
        final Entity entity;
        final Map<NamespacedKey, Object> data = new HashMap<>();
        final Map<String, Object> properties = new HashMap<>();
        final List<Entity> passengers = new ArrayList<>();
        TestWorld world;
        Entity vehicle;
        boolean valid = true, online = true, persistent = true, visibleByDefault = true, checkedBeforeInsertion, inLove;
        int reveals, hides, removalFailures;
        double x;

        Actor(Fixture f, Class<? extends Entity> type, TestWorld world) {
            this.world = world;
            PersistentDataContainer container = proxy(PersistentDataContainer.class, (self, method, args) -> switch (method.getName()) {
                case "set" -> { data.put((NamespacedKey) args[0], args[2]); yield null; }
                case "has" -> data.containsKey(args[0]);
                case "get" -> data.get(args[0]);
                case "getKeys" -> data.keySet();
                default -> defaultValue(method.getReturnType());
            });
            entity = proxy(type, (self, method, args) -> {
                String name = method.getName();
                if (name.startsWith("set") && args != null && args.length == 1) properties.put(name, args[0]);
                return switch (name) {
                    case "getUniqueId" -> id;
                    case "getWorld" -> this.world.world;
                    case "getLocation" -> location();
                    case "isValid" -> valid;
                    case "isDead" -> !valid;
                    case "isOnline" -> online;
                    case "isLoveMode" -> inLove;
                    case "getPersistentDataContainer" -> container;
                    case "setPersistent" -> { persistent = (boolean) args[0]; yield null; }
                    case "setVisibleByDefault" -> { visibleByDefault = (boolean) args[0]; yield null; }
                    case "setText" -> { if (f.failure.equals("configure")) throw new IllegalStateException("configuration failed"); yield null; }
                    case "text" -> throw new AssertionError("Server Adventure signature must not be used");
                    case "setInvulnerable" -> throw new AssertionError("Inspection must not change entity invulnerability");
                    case "getVehicle" -> vehicle;
                    case "getPassengers" -> passengers;
                    case "addPassenger" -> {
                        if (f.failure.equals("passenger")) yield false;
                        Actor passenger = f.displays.stream().filter(actor -> actor.entity == args[0]).findFirst().orElseThrow();
                        passenger.vehicle = (Entity) self;
                        passengers.add(passenger.entity);
                        yield true;
                    }
                    case "showEntity" -> { if (f.failure.equals("reveal")) throw new IllegalStateException("reveal failed"); reveals++; yield null; }
                    case "hideEntity" -> { hides++; yield null; }
                    case "remove" -> {
                        if (removalFailures-- > 0) throw new IllegalStateException("transient removal failure");
                        valid = false;
                        if (vehicle != null) f.horse.passengers.remove(self);
                        vehicle = null;
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                };
            });
        }
        Location location() { return new Location(world.world, x, 64, 0); }
        Player player() { return (Player) entity; }
        AbstractHorse horse() { return (AbstractHorse) entity; }
    }

    private static final class Scheduler {
        final List<Task> tasks = new ArrayList<>();
        final BukkitScheduler scheduler;
        Scheduler(Fixture f) {
            scheduler = proxy(BukkitScheduler.class, (self, method, args) -> {
                if (method.getName().equals("cancelTask")) {
                    tasks.stream().filter(task -> task.id == (int) args[0]).forEach(task -> task.cancelled = true);
                }
                if (method.getName().equals("runTaskLater") || method.getName().equals("runTaskTimer")) {
                    boolean repeating = method.getName().equals("runTaskTimer");
                    if (f.failure.equals(repeating ? "monitor" : "expiration")) throw new IllegalStateException("scheduler rejected task");
                    Task task = new Task(tasks.size() + 1, (Runnable) args[1], (long) args[2], repeating);
                    tasks.add(task);
                    return task.task;
                }
                return defaultValue(method.getReturnType());
            });
        }
        Task expiration() { return tasks.stream().filter(task -> !task.repeating && !task.cancelled).reduce((a, b) -> b).orElseThrow(); }
        Task monitor() { return tasks.stream().filter(task -> task.repeating && !task.cancelled).reduce((a, b) -> b).orElseThrow(); }
        int activeCount() { return (int) tasks.stream().filter(task -> !task.cancelled).count(); }
    }

    private static final class Task {
        final int id;
        final Runnable action;
        final long delay;
        final boolean repeating;
        final BukkitTask task;
        boolean cancelled;
        int cancelFailures;
        Task(int id, Runnable action, long delay, boolean repeating) {
            this.id = id; this.action = action; this.delay = delay; this.repeating = repeating;
            task = proxy(BukkitTask.class, (self, method, args) -> switch (method.getName()) {
                case "getTaskId" -> id;
                case "isCancelled" -> cancelled;
                case "cancel" -> { if (cancelFailures-- > 0) throw new IllegalStateException("cancel failed"); cancelled = true; yield null; }
                default -> defaultValue(method.getReturnType());
            });
        }
        void fire() { action.run(); }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "equals" -> self == args[0];
                case "hashCode" -> System.identityHashCode(self);
                default -> type.getSimpleName() + "TestProxy";
            };
            return handler.invoke(self, method, args);
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        return 0;
    }

    private static void setServer(Server server) {
        try {
            java.lang.reflect.Field field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, server);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not install isolated test server", failure);
        }
    }
}
