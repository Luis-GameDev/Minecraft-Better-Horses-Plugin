package me.luisgamedev.betterhorses.listeners;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.traits.OwnedPotionEffects;
import me.luisgamedev.betterhorses.traits.TraitRegistry;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.*;
import org.bukkit.util.Vector;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class TraitLifecycleRegressionTest {
    @Test void dashNeverMutatesTheNativeBaseAndIsRecoveredAfterUnloading() throws Exception {
        try (Fixture f = new Fixture()) {
            TraitRegistry.activateDashBoost(f.player.handle, f.horse.handle);
            NativeAttribute speed = f.horse.attributes.get(AttributeResolver.generic("MOVEMENT_SPEED"));
            assertEquals(.2, speed.base, 1e-12);
            assertEquals(.3, speed.getValue(), 1e-12);
            f.horse.valid = false;
            f.advance(101);
            f.horse.valid = true;
            TraitRegistry.reconcileTemporaryState(f.horse.handle);
            assertEquals(.2, speed.base, 1e-12);
            assertEquals(.2, speed.getValue(), 1e-12);
        }
    }

    @Test void cooldownSurvivesAStoredItemAndANewEntityUuid() throws Exception {
        try (Fixture f = new Fixture()) {
            TraitRegistry.activateDashBoost(f.player.handle, f.horse.handle);
            Actor<Horse> restored = f.actor(Horse.class);
            Map<NamespacedKey, Object> values = new HashMap<>();
            PersistentDataContainer item = data(values);
            TraitRegistry.copyCooldowns(f.horse.data, item);
            TraitRegistry.copyCooldowns(item, restored.data);
            assertNotEquals(f.horse.id, restored.id);
            Method cooldown = TraitRegistry.class.getDeclaredMethod("isOnCooldown", AbstractHorse.class, String.class);
            cooldown.setAccessible(true);
            assertEquals(true, cooldown.invoke(null, restored.handle, "dashboost"));
            assertEquals(f.horse.values.get(new NamespacedKey("betterhorses", "ability_cooldown_dashboost")),
                    restored.values.get(new NamespacedKey("betterhorses", "ability_cooldown_dashboost")));
        }
    }

    @Test void idleRidersKeepFireheartAndRevokingPermissionClearsOwnedEffects() throws Exception {
        try (Fixture f = new Fixture()) {
            f.horse.values.put(BetterHorseKeys.TRAIT, "fireheart");
            PassiveTraitListener listener = new PassiveTraitListener();
            listener.onMount(new EntityMountEvent(f.player.handle, f.horse.handle));
            f.advance(100); // No PlayerMoveEvent is emitted.
            assertNotNull(f.player.effect(PotionEffectType.FIRE_RESISTANCE));
            assertNotNull(f.horse.effect(PotionEffectType.FIRE_RESISTANCE));
            f.player.permission = false;
            f.advance(10);
            assertNull(f.player.effect(PotionEffectType.FIRE_RESISTANCE));
            assertNull(f.horse.effect(PotionEffectType.FIRE_RESISTANCE));
            listener.shutdown();
        }
    }

    @Test void ghostReloadClearsTrackingAndPreservesExternalInvisibility() throws Exception {
        try (Fixture f = new Fixture()) {
            TraitRegistry.activateGhostHorse(f.player.handle, f.horse.handle);
            PotionEffect foreign = new PotionEffect(PotionEffectType.INVISIBILITY, 500, 0, false, true, true);
            f.player.put(foreign);
            TraitRegistry.cleanupAll();
            assertEquals(500, f.player.effect(PotionEffectType.INVISIBILITY).getDuration());
            assertNull(f.horse.effect(PotionEffectType.INVISIBILITY));
            for (String name : List.of("ghostPlayers", "ghostHorses", "ghostHorseTasks")) {
                assertTrue(((Map<?, ?>) field(TraitRegistry.class, name).get(null)).isEmpty());
            }
        }
    }

    @Test void cancelledDamageCannotTriggerCurseRetaliation() throws Exception {
        try (Fixture f = new Fixture()) {
            f.horse.values.put(new NamespacedKey("betterhorses", "revenantcurse_active"), System.currentTimeMillis() + 10000);
            EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(f.player.handle, f.horse.handle,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                    proxy(org.bukkit.damage.DamageSource.class, (source, method, args) -> zero(method.getReturnType())), 2);
            event.setCancelled(true);
            new RevenantCurseListener().onHorseOrRiderHit(event);
            assertTrue(f.player.potions.isEmpty());
        }
    }

    @Test void cancelledSkeletonSpawnRetainsTheOriginalMountAndItsInventory() throws Exception {
        try (Fixture f = new Fixture()) {
            Actor<SkeletonHorse> cancelled = f.actor(SkeletonHorse.class);
            cancelled.valid = false;
            f.spawnResult = cancelled;
            ItemStack saddle = new ItemStack(Material.SADDLE);
            f.horse.slots[0] = saddle;
            boolean result = (boolean) invoke(new UndeadTraitListener(), "transformToSkeleton",
                    new Class<?>[]{AbstractHorse.class}, f.horse.handle);
            assertFalse(result);
            assertTrue(f.horse.valid);
            assertSame(saddle, f.horse.slots[0]);
        }
    }

    @Test void cancelledReturnSpawnRetainsTheRestorationFoodAndSkeleton() throws Exception {
        try (Fixture f = new Fixture()) {
            Actor<SkeletonHorse> skeleton = f.actor(SkeletonHorse.class);
            skeleton.values.put(BetterHorseKeys.UNDEAD_SKELETON, (byte) 1);
            skeleton.values.put(BetterHorseKeys.UNDEAD_ORIGINAL_TYPE, "HORSE");
            Actor<Horse> cancelled = f.actor(Horse.class);
            cancelled.valid = false;
            f.spawnResult = cancelled;
            f.held = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 2);
            new UndeadTraitListener().onFeed(new PlayerInteractEntityEvent(f.player.handle, skeleton.handle, EquipmentSlot.HAND));
            assertTrue(skeleton.valid);
            assertEquals(2, f.held.getAmount());
        }
    }

    @Test void postCommitSchedulingFailureCannotEraseTheReplacement() throws Exception {
        try (Fixture f = new Fixture()) {
            Actor<SkeletonHorse> skeleton = f.actor(SkeletonHorse.class);
            f.spawnResult = skeleton;
            f.failScheduling = true;
            boolean result = (boolean) invoke(new UndeadTraitListener(), "transformToSkeleton",
                    new Class<?>[]{AbstractHorse.class}, f.horse.handle);
            assertTrue(result);
            assertFalse(f.horse.valid);
            assertTrue(skeleton.valid);
        }
    }

    @Test void legacyCargoCannotRecreateASaddleOrItsEquipmentSlot() throws Exception {
        try (Fixture f = new Fixture()) {
            Actor<Donkey> restored = f.actor(Donkey.class);
            restored.slots = new ItemStack[17];
            ItemStack currentSaddle = new ItemStack(Material.SADDLE);
            restored.slots[0] = currentSaddle;
            Map<NamespacedKey, Object> values = new HashMap<>();
            PersistentDataContainer stored = data(values);
            values.put(BetterHorseKeys.UNDEAD_CHESTED, (byte) 1);
            // Legacy full inventories included two equipment slots. Slot0 is NOT cargo.
            values.put(BetterHorseKeys.UNDEAD_CHEST_CONTENTS, legacyInventory(new ItemStack(Material.SADDLE), null,
                    new ItemStack(Material.DIAMOND, 3)));
            UndeadTraitListener listener = new UndeadTraitListener();
            ItemStack[] cargo = (ItemStack[]) invoke(listener, "readStoredChestContents",
                    new Class<?>[]{PersistentDataContainer.class}, stored);
            assertEquals(1, cargo.length);
            assertEquals(Material.DIAMOND, cargo[0].getType());
            invoke(listener, "restoreChestContents", new Class<?>[]{AbstractHorse.class, PersistentDataContainer.class, ItemStack[].class},
                    restored.handle, stored, cargo);
            assertSame(currentSaddle, restored.slots[0]);
            assertEquals(Material.DIAMOND, restored.slots[2].getType());
        }
    }

    @Test void newlyStoredUndeadCargoExcludesBothEquipmentSlots() throws Exception {
        try (Fixture ignored = new Fixture()) {
            UndeadTraitListener listener = new UndeadTraitListener();
            String encoded = (String) invoke(listener, "serializeStorageContents", new Class<?>[]{ItemStack[].class},
                    (Object) new ItemStack[]{new ItemStack(Material.SADDLE), new ItemStack(Material.IRON_HORSE_ARMOR), new ItemStack(Material.GOLD_INGOT, 4)});
            Map<NamespacedKey, Object> values = new HashMap<>();
            values.put(BetterHorseKeys.UNDEAD_CHEST_CONTENTS, encoded);
            ItemStack[] cargo = (ItemStack[]) invoke(listener, "readStoredChestContents",
                    new Class<?>[]{PersistentDataContainer.class}, data(values));
            assertEquals(1, cargo.length);
            assertEquals(Material.GOLD_INGOT, cargo[0].getType());
        }
    }

    static Object invoke(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        try { return method.invoke(owner, args); }
        catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof Exception cause) throw cause;
            throw exception;
        }
    }

    private static String legacyInventory(ItemStack... items) throws Exception {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeInt(items.length);
            for (ItemStack item : items) output.writeObject(item);
            output.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        }
    }

    static final class Fixture implements AutoCloseable {
        final Field instance = field(BetterHorses.class, "instance"), serverField = field(Bukkit.class, "server");
        final Object previousPlugin = instance.get(null), previousServer = serverField.get(null);
        final TestPlugin plugin;
        final FileConfiguration config = new YamlConfiguration();
        final List<Actor<?>> actors = new ArrayList<>();
        final List<Task> tasks = new ArrayList<>();
        final List<Event> events = new ArrayList<>();
        final Set<UUID> foreign = new HashSet<>();
        final World world;
        final Actor<Player> player;
        final Actor<Horse> horse;
        Actor<?> spawnResult;
        List<Entity> targets = List.of();
        ItemStack held;
        boolean failScheduling;
        long tick;

        Fixture() throws Exception {
            Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
            plugin = (TestPlugin) unsafe.allocateInstance(TestPlugin.class);
            plugin.configuration = config;
            plugin.logger = Logger.getAnonymousLogger(); plugin.logger.setUseParentHandlers(false);
            plugin.language = (TestLanguage) unsafe.allocateInstance(TestLanguage.class);
            PluginDescriptionFile description = new PluginDescriptionFile("BetterHorses", "test", TestPlugin.class.getName());
            field(JavaPlugin.class, "description").set(plugin, description);
            field(JavaPlugin.class, "pluginMeta").set(plugin, description);
            field(JavaPlugin.class, "isEnabled").setBoolean(plugin, true);
            instance.set(null, plugin);
            world = proxy(World.class, (target, method, args) -> switch (method.getName()) {
                case "spawn" -> spawnResult == null ? null : spawnResult.handle;
                case "getName" -> "world";
                case "getUID" -> UUID.fromString("00000000-0000-0000-0000-000000000001");
                case "equals" -> target == args[0];
                case "hashCode" -> System.identityHashCode(target);
                default -> zero(method.getReturnType());
            });
            PluginManager manager = proxy(PluginManager.class, (target, method, args) -> {
                if (method.getName().equals("callEvent")) events.add((Event) args[0]);
                return zero(method.getReturnType());
            });
            BukkitScheduler scheduler = proxy(BukkitScheduler.class, (target, method, args) -> {
                if (method.getName().startsWith("runTask")) {
                    if (failScheduling) throw new IllegalPluginAccessException("test rejected scheduling");
                    long delay = args.length > 2 ? (Long) args[2] : 1;
                    long period = args.length > 3 ? (Long) args[3] : 0;
                    Task task = addTask((Runnable) args[1], delay, period);
                    return task.bukkit;
                }
                return zero(method.getReturnType());
            });
            UnsafeValues values = proxy(UnsafeValues.class, (target, method, args) -> switch (method.getName()) {
                case "getDataVersion" -> 3700;
                case "createDamageSourceBuilder" -> proxy(org.bukkit.damage.DamageSource.Builder.class, (builder, setter, arguments) -> {
                    if (!setter.getName().equals("build")) return builder;
                    return proxy(org.bukkit.damage.DamageSource.class, (source, getter, unused) ->
                            getter.getName().equals("getDamageType") ? args[0] : zero(getter.getReturnType()));
                });
                case "getMaterial" -> Material.matchMaterial((String) args[0]);
                default -> zero(method.getReturnType());
            });
            ItemFactory factory = proxy(ItemFactory.class, (target, method, args) -> switch (method.getName()) {
                case "getItemMeta" -> null;
                case "ensureServerConversions" -> args[0];
                case "equals" -> args[0] == args[1];
                default -> zero(method.getReturnType());
            });
            Server server = proxy(Server.class, (target, method, args) -> switch (method.getName()) {
                case "getScheduler" -> scheduler;
                case "getPluginManager" -> manager;
                case "getUnsafe" -> values;
                case "getItemFactory" -> factory;
                case "getOnlinePlayers" -> List.of();
                case "getWorlds" -> List.of(world);
                case "isOwnedByCurrentRegion" -> !foreign.contains(((Entity) args[0]).getUniqueId());
                case "isPrimaryThread" -> true;
                case "getBukkitVersion", "getMinecraftVersion" -> "1.20.6-R0.1-SNAPSHOT";
                default -> zero(method.getReturnType());
            });
            serverField.set(null, server); field(JavaPlugin.class, "server").set(plugin, server);
            try {
                Class<?> type = Class.forName("me.luisgamedev.betterhorses.scheduler.BetterHorsesScheduler");
                field(BetterHorses.class, "scheduler").set(plugin, type.getConstructor(Plugin.class).newInstance(plugin));
            } catch (ClassNotFoundException ignored) { }
            config.set("traits.enabled", true);
            for (String trait : List.of("dashboost", "ghosthorse", "fireheart", "featherhooves", "heavenhooves", "skyburst", "undead")) {
                config.set("traits." + trait + ".enabled", true);
                config.set("traits." + trait + ".duration", 5);
                config.set("traits." + trait + ".cooldown", 30);
            }
            config.set("traits.undead.transform-back-animation", false);
            config.set("settings.mount-types.donkeys", true);
            config.set("horse-growth-settings.enabled", false);
            player = actor(Player.class); horse = actor(Horse.class); player.vehicle = horse.handle;
        }

        <T extends LivingEntity> Actor<T> actor(Class<T> type) { Actor<T> actor = new Actor<>(this, type); actors.add(actor); return actor; }
        Task addTask(Runnable action, long delay, long period) { Task task = new Task(this, action, tick + Math.max(1, delay), period); tasks.add(task); return task; }
        void advance(int count) {
            for (int i = 0; i < count; i++) {
                tick++;
                for (Task task : List.copyOf(tasks)) if (!task.cancelled && task.due <= tick) {
                    if (task.period == 0) task.cancelled = true;
                    else task.due += task.period;
                    task.action.run();
                }
            }
        }
        @Override public void close() throws Exception {
            try { TraitRegistry.shutdown(); for (Actor<?> actor : actors) OwnedPotionEffects.forget(actor.id); }
            finally { instance.set(null, previousPlugin); serverField.set(null, previousServer); }
        }
    }

    static final class Actor<T extends LivingEntity> implements InvocationHandler {
        final Fixture f;
        final UUID id = UUID.randomUUID();
        final Map<NamespacedKey, Object> values = new HashMap<>();
        final PersistentDataContainer data = data(values);
        final Map<Attribute, NativeAttribute> attributes = new HashMap<>();
        final Map<PotionEffectType, PotionEntry> potions = new HashMap<>();
        final T handle;
        ItemStack[] slots = new ItemStack[2];
        Entity vehicle;
        boolean valid = true, permission = true;
        double x, health = 20;
        Vector velocity = new Vector();
        Actor(Fixture f, Class<T> type) {
            this.f = f; handle = proxy(type, this);
            attributes.put(AttributeResolver.generic("MOVEMENT_SPEED"), new NativeAttribute(.2));
            attributes.put(AttributeResolver.generic("MAX_HEALTH"), new NativeAttribute(20));
            attributes.put(AttributeResolver.horseJumpStrength(), new NativeAttribute(.7));
        }
        PotionEffect effect(PotionEffectType type) {
            PotionEntry entry = potions.get(type);
            if (entry == null || entry.until <= f.tick) return null;
            PotionEffect effect = entry.effect;
            return new PotionEffect(type, (int) (entry.until - f.tick), effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon());
        }
        void put(PotionEffect effect) { potions.put(effect.getType(), new PotionEntry(effect, f.tick + effect.getDuration())); }
        public Object invoke(Object target, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getPersistentDataContainer" -> data;
                case "getType" -> handle instanceof Player ? EntityType.PLAYER : handle instanceof Donkey ? EntityType.DONKEY
                        : handle instanceof SkeletonHorse ? EntityType.SKELETON_HORSE : EntityType.HORSE;
                case "isValid", "isOnline", "isTamed", "isAdult" -> valid;
                case "hasPermission" -> permission;
                case "getWorld" -> f.world;
                case "getLocation", "getEyeLocation" -> new Location(f.world, x, 0, 0);
                case "getTicksLived" -> (int) f.tick;
                case "getVehicle" -> vehicle;
                case "getNearbyEntities" -> f.targets;
                case "getPassengers" -> f.player != null && f.player.vehicle == handle ? List.of(f.player.handle) : List.of();
                case "getOwner" -> f.player.handle;
                case "getColor" -> Horse.Color.WHITE;
                case "getStyle" -> Horse.Style.WHITE;
                case "getAttribute" -> attributes.get(args[0]);
                case "getHealth" -> health;
                case "setHealth" -> { health = (Double) args[0]; yield null; }
                case "getPotionEffect" -> effect((PotionEffectType) args[0]);
                case "addPotionEffect" -> { put((PotionEffect) args[0]); yield true; }
                case "removePotionEffect" -> { potions.remove(args[0]); yield null; }
                case "getVelocity" -> velocity.clone();
                case "setVelocity" -> { velocity = ((Vector) args[0]).clone(); yield null; }
                case "remove" -> { valid = false; yield null; }
                case "eject" -> { if (f.player.vehicle == handle) f.player.vehicle = null; yield true; }
                case "addPassenger" -> { f.player.vehicle = handle; yield true; }
                case "getInventory" -> handle instanceof Player ? playerInventory() : horseInventory();
                case "getScheduler" -> proxy(EntityScheduler.class, (p, m, a) -> {
                    if (f.failScheduling) throw new IllegalPluginAccessException("test rejected scheduling");
                    @SuppressWarnings("unchecked") Consumer<ScheduledTask> action = (Consumer<ScheduledTask>) a[1];
                    Task task = f.addTask(null, (Long) a[3], a.length > 4 ? (Long) a[4] : 0);
                    task.action = () -> action.accept(task.paper);
                    return task.paper;
                });
                case "equals" -> target == args[0];
                case "hashCode" -> System.identityHashCode(target);
                default -> zero(method.getReturnType());
            };
        }
        private PlayerInventory playerInventory() {
            return proxy(PlayerInventory.class, (p, m, a) -> switch (m.getName()) {
                case "getItem", "getItemInMainHand" -> f.held;
                case "setItem", "setItemInMainHand" -> { f.held = (ItemStack) a[a.length - 1]; yield null; }
                default -> zero(m.getReturnType());
            });
        }
        private HorseInventory horseInventory() {
            return proxy(HorseInventory.class, (p, m, a) -> switch (m.getName()) {
                case "getSaddle" -> slots[0];
                case "getArmor" -> slots[1];
                case "getSize" -> slots.length;
                case "getStorageContents" -> slots.clone();
                case "getItem" -> slots[(Integer) a[0]];
                case "setItem" -> { slots[(Integer) a[0]] = (ItemStack) a[1]; yield null; }
                case "setSaddle" -> { slots[0] = (ItemStack) a[0]; yield null; }
                default -> zero(m.getReturnType());
            });
        }
    }

    record PotionEntry(PotionEffect effect, long until) { }
    static final class Task {
        Runnable action;
        long due;
        final long period;
        boolean cancelled;
        final BukkitTask bukkit;
        final ScheduledTask paper;
        Task(Fixture f, Runnable action, long due, long period) {
            this.action = action; this.due = due; this.period = period;
            bukkit = proxy(BukkitTask.class, (p, m, a) -> { if (m.getName().equals("cancel")) cancelled = true; return zero(m.getReturnType()); });
            paper = proxy(ScheduledTask.class, (p, m, a) -> switch (m.getName()) {
                case "cancel" -> { cancelled = true; yield null; }
                case "getExecutionState" -> cancelled ? ScheduledTask.ExecutionState.CANCELLED : ScheduledTask.ExecutionState.IDLE;
                default -> zero(m.getReturnType());
            });
        }
    }

    public static final class NativeAttribute implements AttributeInstance {
        double base;
        final Map<UUID, AttributeModifier> modifiers = new HashMap<>();
        NativeAttribute(double base) { this.base = base; }
        public Attribute getAttribute() { return AttributeResolver.generic("MOVEMENT_SPEED"); }
        public double getBaseValue() { return base; }
        public void setBaseValue(double value) { base = value; }
        public Collection<AttributeModifier> getModifiers() { return List.copyOf(modifiers.values()); }
        public AttributeModifier getModifier(UUID id) { return modifiers.get(id); }
        public void addModifier(AttributeModifier modifier) { modifiers.put(modifier.getUniqueId(), modifier); }
        public void addTransientModifier(AttributeModifier modifier) { addModifier(modifier); }
        public void removeModifier(AttributeModifier modifier) { modifiers.remove(modifier.getUniqueId()); }
        public void removeModifier(UUID id) { modifiers.remove(id); }
        public double getDefaultValue() { return base; }
        public double getValue() { double value = base; for (AttributeModifier m : modifiers.values()) value *= 1 + m.getAmount(); return value; }
    }

    public static final class TestPlugin extends BetterHorses {
        FileConfiguration configuration;
        Logger logger;
        LanguageManager language;
        @Override public FileConfiguration getConfig() { return configuration; }
        @Override public Logger getLogger() { return logger; }
        @Override public LanguageManager getLang() { return language; }
    }
    public static final class TestLanguage extends LanguageManager {
        public TestLanguage() { super(null, null); }
        @Override public String getRaw(OfflinePlayer player, String key) { return key; }
        @Override public void send(Player player, String key) { }
    }

    static PersistentDataContainer data(Map<NamespacedKey, Object> values) {
        return proxy(PersistentDataContainer.class, (p, m, a) -> switch (m.getName()) {
            case "has" -> values.containsKey(a[0]);
            case "get" -> values.get(a[0]);
            case "getOrDefault" -> values.getOrDefault(a[0], a[2]);
            case "set" -> { values.put((NamespacedKey) a[0], a[2]); yield null; }
            case "remove" -> values.remove(a[0]);
            case "getKeys" -> Set.copyOf(values.keySet());
            default -> zero(m.getReturnType());
        });
    }
    static <T> T proxy(Class<T> type, InvocationHandler handler) { return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler)); }
    static Field field(Class<?> type, String name) { try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f; } catch (Exception e) { throw new AssertionError(e); } }
    static Object zero(Class<?> type) {
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
