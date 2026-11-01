package me.luisgamedev.betterhorses.inspection;

import me.luisgamedev.betterhorses.scheduler.ScheduledTaskHandle;
import net.kyori.adventure.text.Component;
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
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class HorseInspectionManagerTest {
    @Test void defaultAndEnabledTamedOnlySettingRejectWildMountsBeforeSpawning() {
        for (boolean explicit : new boolean[]{false, true}) {
            try (Fixture f = new Fixture()) {
                if (explicit) f.config.set("horse-inspection.only-tamed", true);
                f.horse.tamed = false;
                assertFalse(f.show());
                assertTrue(f.displays.isEmpty());
                assertEquals(0, f.runtime.active());
                f.horse.tamed = true;
                assertTrue(f.show());
            }
        }
    }

    @Test void disablingTamedOnlySettingAllowsUntamedMountsToRemainInspected() {
        try (Fixture f = new Fixture()) {
            f.horse.tamed = false;
            f.config.set("horse-inspection.only-tamed", false);
            assertTrue(f.show());
            f.runtime.monitor().fire();
            assertTrue(f.lastDisplay().valid);
            assertEquals(2, f.runtime.active());
        }
    }

    @Test void anInspectedMountBecomingUntamedClosesTheDisplay() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.show());
            f.horse.tamed = false;
            f.runtime.monitor().fire();
            assertFalse(f.lastDisplay().valid);
            assertEquals(0, f.runtime.active());
        }
    }

    @Test void enablingTamedOnlySettingClosesAnExistingUntamedInspection() {
        try (Fixture f = new Fixture()) {
            f.config.set("horse-inspection.only-tamed", false);
            f.horse.tamed = false;
            assertTrue(f.show());
            f.config.set("horse-inspection.only-tamed", true);
            f.runtime.monitor().fire();
            assertFalse(f.lastDisplay().valid);
            assertEquals(0, f.runtime.active());
        }
    }

    @Test void passengerCallbackUntamingTheMountAbortsBeforeRevealingItsDisplay() {
        try (Fixture f = new Fixture()) {
            f.failure = "untame";
            assertFalse(f.show());
            assertFalse(f.lastDisplay().valid);
            assertEquals(0, f.viewer.reveals);
            assertEquals(0, f.runtime.active());
        }
    }

    @Test void replacingInspectionStopsAfterHideCallbackTransfersHorseOwnership() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.show());
            Actor old = f.lastDisplay();
            f.onHide = () -> { f.horse.region = 2; old.region = 2; };
            assertFalse(f.show());
            assertEquals(1, f.displays.size(), "no replacement may spawn using the former region");
            f.onHide = () -> {};
            f.runtime.cleanup().fire();
            assertFalse(old.valid);
        }
    }

    @Test void hideMountStopsBeforeReadingPassengersAfterHideCallbackTransfersOwnership() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.show());
            Actor old = f.lastDisplay();
            f.onHide = () -> { f.horse.region = 2; old.region = 2; };
            assertDoesNotThrow(() -> f.manager.hideMount(f.horse.horse()));
            f.onHide = () -> {};
            f.runtime.cleanup().fire();
            assertFalse(old.valid);
        }
    }

    @Test void replacingInspectionRechecksEligibilityAfterHideCallback() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.show());
            f.onHide = () -> f.horse.tamed = false;
            assertFalse(f.show());
            assertEquals(1, f.displays.size());
            assertEquals(0, f.runtime.active());
        }
    }

    @Test void displayIsPrivateBeforeInsertionAndExpiresOnItsOwnEntityScheduler() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.show());
            Actor display = f.lastDisplay();
            assertTrue(display.checkedBeforeInsertion);
            assertSame(f.horse.entity, display.vehicle);
            assertEquals(1, f.viewer.reveals);
            assertEquals(0, f.other.reveals);
            Task expiration = f.runtime.expiration();
            assertSame(display.entity, expiration.owner);
            assertEquals(200L, expiration.delay);
            expiration.fire();
            assertFalse(display.valid);
            assertEquals(0, f.runtime.active());
        }
    }

    @Test void foreignPlayersAndMountsAreRejectedBeforeAnyLiveRead() {
        try (Fixture f = new Fixture()) {
            f.viewer.region = 2;
            assertFalse(f.show());
            f.viewer.region = 1;
            f.horse.region = 2;
            assertFalse(f.show());
            assertTrue(f.displays.isEmpty());
        }
    }

    @Test void viewerCrossingRegionClosesDisplayWithoutReadingTheForeignViewer() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.show());
            f.viewer.region = 2;
            f.runtime.monitor().fire();
            assertFalse(f.lastDisplay().valid);
            assertEquals(0, f.viewer.hides);
            assertEquals(0, f.runtime.active());
        }
    }

    @Test void quitInAnotherRegionSchedulesDisplayRemovalOnTheDisplayOwner() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.show());
            Actor display = f.lastDisplay();
            display.region = 2;
            f.horse.region = 2;
            f.manager.onQuit(new PlayerQuitEvent(f.viewer.player(), "quit"));
            assertTrue(display.valid);
            Task removal = f.runtime.cleanup();
            assertSame(display.entity, removal.owner);
            removal.fire();
            assertFalse(display.valid);
            assertEquals(0, f.runtime.active());
        }
    }

    @Test void retiredDisplayCallbacksOnlyDropSessionState() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.show());
            Actor display = f.lastDisplay();
            display.retired = true;
            display.region = 2;
            f.runtime.expiration().retire();
            assertEquals(0, f.runtime.active());
            assertEquals(0, display.removeCalls);
        }
    }

    @Test void staleExpirationCannotRemoveReplacementSession() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.show());
            Task stale = f.runtime.expiration();
            Actor old = f.lastDisplay();
            assertTrue(f.show());
            Actor replacement = f.lastDisplay();
            stale.forceFire();
            assertFalse(old.valid);
            assertTrue(replacement.valid);
            assertEquals(2, f.runtime.active());
            f.manager.hide(f.viewer.player());
            assertFalse(replacement.valid);
        }
    }

    @Test void reloadCleanupInOldRegionCannotRemoveANewSession() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.show());
            Actor old = f.lastDisplay();
            old.region = 2;
            f.runtime.thread = 0;
            f.manager.onConfigurationReload();
            Task oldRemoval = f.runtime.cleanup();
            f.runtime.thread = 1;
            assertTrue(f.show());
            Actor replacement = f.lastDisplay();
            oldRemoval.fire();
            assertFalse(old.valid);
            assertTrue(replacement.valid);
            f.runtime.thread = 1;
            f.manager.hide(f.viewer.player());
            assertFalse(replacement.valid);
        }
    }

    @Test void globalShutdownCancelsTasksAndNextStartupRecoversMarkedNonPersistentDisplays() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.show());
            Actor old = f.lastDisplay();
            int submissions = f.runtime.tasks.size();
            f.enabled = false;
            f.runtime.thread = 0;
            f.manager.shutdown();
            assertEquals(submissions, f.runtime.tasks.size(), "disabled plugin must submit no cleanup work");
            assertEquals(0, f.runtime.active());
            assertTrue(old.valid, "foreign live entities cannot be mutated on the global shutdown thread");
            assertFalse(old.persistent);
            f.enabled = true;
            f.manager.start();
            f.runtime.global().fire();
            f.runtime.regionTask().fire();
            assertFalse(old.valid);
        }
    }

    @Test void startupAndEntityLoadRemoveOnlyStaleMarkedDisplaysOnTheirRegion() {
        try (Fixture f = new Fixture()) {
            Actor stale = f.newDisplay();
            f.tag(stale);
            Actor unrelated = f.newDisplay();
            f.runtime.thread = 0;
            f.manager.start();
            f.runtime.global().fire();
            assertTrue(stale.valid);
            f.runtime.regionTask().fire();
            assertFalse(stale.valid);
            assertTrue(unrelated.valid);
            f.runtime.thread = 1;
            assertTrue(f.show());
            Actor active = f.lastDisplay();
            f.manager.onEntitiesLoad(new EntitiesLoadEvent(f.chunk, List.of(active.entity)));
            assertTrue(active.valid);
            Actor loaded = f.newDisplay();
            f.tag(loaded);
            f.manager.onEntitiesLoad(new EntitiesLoadEvent(f.chunk, List.of(loaded.entity)));
            assertFalse(loaded.valid);
        }
    }

    @Test void worldUnloadFromGlobalThreadUsesCapturedWorldIdentityAndOwnerCleanup() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.show());
            f.runtime.thread = 0;
            f.manager.onWorldUnload(new WorldUnloadEvent(f.world));
            assertTrue(f.lastDisplay().valid);
            f.runtime.cleanup().fire();
            assertFalse(f.lastDisplay().valid);
        }
    }

    @Test void transientRemovalFailureRetriesWithoutLeavingAnActiveInspection() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.show());
            Actor display = f.lastDisplay();
            display.removalFailures = 1;
            f.manager.hide(f.viewer.player());
            assertTrue(display.valid);
            f.runtime.cleanup().fire();
            assertFalse(display.valid);
            assertEquals(0, f.runtime.active());
        }
    }

    @Test void rejectedCleanupSubmissionCanBeRecoveredByEntityLoadWithoutDuplicateRetries() {
        try (Fixture f = new Fixture()) {
            assertTrue(f.show());
            Actor display = f.lastDisplay();
            display.removalFailures = 1;
            f.failure = "cleanup";
            f.manager.hide(f.viewer.player());
            assertTrue(display.valid);
            assertEquals(0, f.runtime.active());
            f.failure = "";
            f.manager.onEntitiesLoad(new EntitiesLoadEvent(f.chunk, List.of(display.entity)));
            assertFalse(display.valid);
            assertEquals(0, f.runtime.active());
        }
    }

    @Test void failuresDuringEachCreationStageCleanTheirPrivateEntityAndTasks() {
        for (String stage : List.of("configure", "spawn", "passenger", "expiration", "monitor", "reveal")) {
            try (Fixture f = new Fixture()) {
                f.failure = stage;
                assertFalse(f.show(), stage);
                assertTrue(f.displays.stream().noneMatch(display -> display.valid), stage);
                assertEquals(0, f.runtime.active(), stage);
            }
        }
    }

    @Test void passengerCallbackOwnershipTransferAbortsBeforeFurtherLiveReads() {
        try (Fixture f = new Fixture()) {
            f.failure = "transfer";
            assertFalse(f.show());
            assertTrue(f.lastDisplay().valid);
            f.runtime.cleanup().fire();
            assertFalse(f.lastDisplay().valid);
        }
    }

    @Test void boundsRangeAndBreedingGuardsKeepTheirStandardBehavior() {
        try (Fixture f = new Fixture()) {
            f.horse.inLove = true;
            assertFalse(f.show());
            f.horse.inLove = false;
            f.viewer.x = 17;
            assertFalse(f.show());
            f.viewer.x = 0;
            f.config.set("horse-inspection.duration-seconds", Double.NaN);
            assertTrue(f.show());
            assertEquals(200, f.runtime.expiration().delay);
            f.horse.inLove = true;
            f.runtime.monitor().fire();
            assertFalse(f.lastDisplay().valid);
        }
    }

    private static Fixture current;
    private static final Logger LOGGER = Logger.getLogger("folia-inspection-tests");
    private static final Server SERVER = proxy(Server.class, (self, method, args) -> switch (method.getName()) {
        case "getLogger" -> LOGGER;
        case "getName" -> "FoliaInspectionTestServer";
        case "getVersion", "getBukkitVersion", "getMinecraftVersion" -> "1.20.6";
        case "getWorlds" -> List.of(current.world);
        case "getPluginManager" -> current.pluginManager;
        default -> defaultValue(method.getReturnType());
    });

    private static final class Fixture implements AutoCloseable {
        final YamlConfiguration config = new YamlConfiguration();
        final UUID worldId = UUID.randomUUID();
        final Map<Entity, Actor> actors = new IdentityHashMap<>();
        final List<Actor> displays = new ArrayList<>();
        final FakeRuntime runtime = new FakeRuntime(this);
        final PluginManager pluginManager = proxy(PluginManager.class, (self, method, args) -> defaultValue(method.getReturnType()));
        final Plugin plugin = proxy(Plugin.class, (self, method, args) -> switch (method.getName()) {
            case "getName" -> "BetterHorses";
            case "getLogger" -> LOGGER;
            case "getServer" -> SERVER;
            case "isEnabled" -> this.enabled;
            default -> defaultValue(method.getReturnType());
        });
        final World world;
        final Chunk chunk;
        final Actor viewer, other, horse;
        final HorseInspectionManager manager;
        final Server previous;
        boolean enabled = true;
        String failure = "";
        Runnable onHide = () -> {};
        Fixture() {
            current = this;
            previous = Bukkit.getServer();
            setServer(SERVER);
            world = proxy(World.class, (self, method, args) -> switch (method.getName()) {
                case "getName" -> "world";
                case "getUID" -> worldId;
                case "getLoadedChunks" -> loadedChunks();
                case "spawn" -> {
                    assertEquals(1, runtime.thread, "spawn must run on the mount region");
                    Actor display = newDisplay();
                    ((Consumer<TextDisplay>) args[2]).accept((TextDisplay) display.entity);
                    display.checkedBeforeInsertion = !display.persistent && !display.visibleByDefault
                            && HorseInspectionManager.isInspectionDisplay(display.entity);
                    if (failure.equals("spawn")) throw new IllegalStateException("spawn failed");
                    yield display.entity;
                }
                default -> defaultValue(method.getReturnType());
            });
            chunk = proxy(Chunk.class, (self, method, args) -> switch (method.getName()) {
                case "getWorld" -> world;
                case "getX", "getZ" -> 0;
                case "isLoaded", "isEntitiesLoaded" -> { assertEquals(1, runtime.thread); yield true; }
                case "getEntities" -> { assertEquals(1, runtime.thread); yield displays.stream().filter(a -> a.valid).map(a -> a.entity).toArray(Entity[]::new); }
                default -> defaultValue(method.getReturnType());
            });
            viewer = new Actor(this, Player.class);
            other = new Actor(this, Player.class);
            horse = new Actor(this, AbstractHorse.class);
            manager = new HorseInspectionManager(plugin, () -> config, runtime);
        }
        boolean show() { return manager.show(viewer.player(), horse.horse(), Component.text("Horse health")); }
        Chunk[] loadedChunks() { return new Chunk[]{chunk}; }
        Actor newDisplay() { Actor display = new Actor(this, TextDisplay.class); displays.add(display); return display; }
        Actor lastDisplay() { return displays.getLast(); }
        void tag(Actor display) { display.data.put(new NamespacedKey("betterhorses", "inspection_display"), (byte) 1); }
        @Override public void close() { manager.shutdown(); setServer(previous); }
    }

    private static final class Actor {
        final UUID id = UUID.randomUUID();
        final Entity entity;
        final Map<NamespacedKey, Object> data = new HashMap<>();
        final List<Entity> passengers = new ArrayList<>();
        Entity vehicle;
        int region = 1, reveals, hides, removeCalls, removalFailures;
        boolean valid = true, online = true, persistent = true, visibleByDefault = true, tamed = true, checkedBeforeInsertion, inLove, retired;
        double x;
        Actor(Fixture f, Class<? extends Entity> type) {
            PersistentDataContainer pdc = proxy(PersistentDataContainer.class, (self, method, args) -> {
                assertEquals(region, f.runtime.thread, "foreign PDC access");
                return switch (method.getName()) {
                    case "set" -> { data.put((NamespacedKey) args[0], args[2]); yield null; }
                    case "has" -> data.containsKey(args[0]);
                    case "get" -> data.get(args[0]);
                    case "getKeys" -> data.keySet();
                    default -> defaultValue(method.getReturnType());
                };
            });
            entity = proxy(type, (self, method, args) -> {
                if (method.getName().equals("getUniqueId")) return id;
                assertFalse(retired, "retired entity access: " + method.getName());
                assertEquals(region, f.runtime.thread, "foreign entity access: " + method.getName());
                return switch (method.getName()) {
                    case "getWorld" -> f.world;
                    case "getLocation" -> new Location(f.world, x, 64, 0);
                    case "isValid" -> valid;
                    case "isDead" -> !valid;
                    case "isOnline" -> online;
                    case "isLoveMode" -> inLove;
                    case "isTamed" -> tamed;
                    case "getPersistentDataContainer" -> pdc;
                    case "setPersistent" -> { persistent = (boolean) args[0]; yield null; }
                    case "setVisibleByDefault" -> { visibleByDefault = (boolean) args[0]; yield null; }
                    case "setText" -> { if (f.failure.equals("configure")) throw new IllegalStateException("configuration failed"); yield null; }
                    case "text" -> throw new AssertionError("Native Adventure signatures must not be used");
                    case "getVehicle" -> vehicle;
                    case "getPassengers" -> passengers;
                    case "addPassenger" -> {
                        if (f.failure.equals("passenger")) yield false;
                        Actor passenger = f.actors.get(args[0]);
                        passenger.vehicle = (Entity) self;
                        passengers.add(passenger.entity);
                        if (f.failure.equals("transfer")) { region = 2; passenger.region = 2; }
                        if (f.failure.equals("untame")) tamed = false;
                        yield true;
                    }
                    case "showEntity" -> { if (f.failure.equals("reveal")) throw new IllegalStateException("reveal failed"); reveals++; yield null; }
                    case "hideEntity" -> { hides++; f.onHide.run(); yield null; }
                    case "remove" -> {
                        removeCalls++;
                        if (removalFailures-- > 0) throw new IllegalStateException("transient removal failure");
                        valid = false;
                        if (vehicle != null) f.actors.get(vehicle).passengers.remove(self);
                        vehicle = null;
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                };
            });
            f.actors.put(entity, this);
        }
        Player player() { return (Player) entity; }
        AbstractHorse horse() { return (AbstractHorse) entity; }
    }

    private static final class FakeRuntime implements HorseInspectionManager.Runtime {
        final Fixture fixture;
        final List<Task> tasks = new ArrayList<>();
        int thread = 1;
        FakeRuntime(Fixture fixture) { this.fixture = fixture; }
        @Override public boolean owned(Entity entity) {
            Actor actor = fixture.actors.get(entity);
            return actor != null && !actor.retired && actor.region == thread;
        }
        @Override public boolean owned(Location location) { return thread == 1; }
        @Override public ScheduledTaskHandle later(Entity entity, long ticks, Runnable action, Runnable retired) {
            if (fixture.failure.equals("cleanup") && ticks == 10) throw new IllegalStateException("cleanup rejected");
            if (fixture.failure.equals("expiration") && ticks != 10) throw new IllegalStateException("expiration rejected");
            return add(entity, ticks, false, 0, action, retired);
        }
        @Override public ScheduledTaskHandle repeating(Entity entity, long delay, long period, Runnable action, Runnable retired) {
            if (fixture.failure.equals("monitor")) throw new IllegalStateException("monitor rejected");
            return add(entity, delay, true, 0, action, retired);
        }
        @Override public ScheduledTaskHandle global(Runnable action) { return add(null, 1, false, 0, action, () -> {}); }
        @Override public ScheduledTaskHandle region(Location location, Runnable action) { return add(null, 1, false, 1, action, () -> {}); }
        Task add(Entity owner, long ticks, boolean repeating, int region, Runnable action, Runnable retired) {
            assertTrue(fixture.enabled, "disabled plugin must not schedule work");
            Task task = new Task(this, owner, ticks, repeating, region, action, retired);
            tasks.add(task);
            return task;
        }
        Task expiration() { return tasks.stream().filter(t -> t.owner != null && !t.repeating && t.delay != 10 && !t.isDone()).reduce((a,b) -> b).orElseThrow(); }
        Task monitor() { return tasks.stream().filter(t -> t.repeating && !t.isDone()).reduce((a,b) -> b).orElseThrow(); }
        Task cleanup() { return tasks.stream().filter(t -> t.owner != null && !t.repeating && t.delay == 10 && !t.isDone()).reduce((a,b) -> b).orElseThrow(); }
        Task global() { return tasks.stream().filter(t -> t.owner == null && t.region == 0 && !t.isDone()).reduce((a,b) -> b).orElseThrow(); }
        Task regionTask() { return tasks.stream().filter(t -> t.owner == null && t.region == 1 && !t.isDone()).reduce((a,b) -> b).orElseThrow(); }
        int active() { return (int) tasks.stream().filter(t -> !t.isDone()).count(); }
    }

    private static final class Task implements ScheduledTaskHandle {
        final FakeRuntime runtime;
        final Entity owner;
        final long delay;
        final boolean repeating;
        final int region;
        final Runnable action, retired;
        boolean cancelled, done;
        Task(FakeRuntime runtime, Entity owner, long delay, boolean repeating, int region, Runnable action, Runnable retired) {
            this.runtime = runtime; this.owner = owner; this.delay = delay; this.repeating = repeating;
            this.region = region; this.action = action; this.retired = retired;
        }
        void fire() { if (!isDone()) forceFire(); }
        void forceFire() {
            int previous = runtime.thread;
            runtime.thread = owner == null ? region : runtime.fixture.actors.get(owner).region;
            if (!repeating) done = true;
            try { action.run(); } finally { runtime.thread = previous; }
        }
        void retire() {
            done = true;
            int previous = runtime.thread;
            runtime.thread = 0;
            try { retired.run(); } finally { runtime.thread = previous; }
        }
        @Override public void cancel() { cancelled = true; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public boolean isDone() { return cancelled || done; }
    }

    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "equals" -> self == args[0];
                case "hashCode" -> System.identityHashCode(self);
                default -> type.getSimpleName() + "InspectionProxy";
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
            var field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, server);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
