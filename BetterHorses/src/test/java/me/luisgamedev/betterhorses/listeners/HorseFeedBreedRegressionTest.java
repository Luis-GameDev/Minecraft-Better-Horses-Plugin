package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.api.events.BetterHorseBreedEvent;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class HorseFeedBreedRegressionTest {
    @Test void plainCarrotsAreNotHorseFoodAndDoNotAwardTraining() throws Exception {
        try (Fixture f = new Fixture()) {
            f.food = new ItemStack(Material.CARROT, 2);
            f.listener.onHorseFeed(f.feed());
            f.listener.observeFeeding(f.feed());
            assertTrue(f.listener.tasks.isEmpty());
            assertNull(f.horse.values.get(BetterHorseKeys.TRAINING_FEEDING_UNITS));
            assertFalse(HorseFeedListener.isFood(SupportedMountType.HORSE, Material.CARROT));
            assertTrue(HorseFeedListener.isFood(SupportedMountType.CAMEL, Material.CACTUS));
            assertFalse(HorseFeedListener.isFood(SupportedMountType.LLAMA, Material.APPLE));
        }
    }

    @Test void cancelledMaleExemptionDoesNotMutateAgeOrAwardTraining() throws Exception {
        try (Fixture f = new Fixture()) {
            f.config.set("settings.breeding-cooldown", 300L);
            f.config.set("settings.male-ignore-cooldown", true);
            f.horse.data.set(BetterHorseKeys.GENDER, PersistentDataType.STRING, "male");
            f.horse.age = 200;
            PlayerInteractEntityEvent event = f.feed();
            event.setCancelled(true);
            f.listener.onHorseFeed(event);
            f.listener.observeFeeding(event);
            assertEquals(200, f.horse.age);
            assertTrue(f.listener.tasks.isEmpty());
            assertFalse(f.horse.values.containsKey(BetterHorseKeys.TRAINING_FEED_COOLDOWN));
        }
    }

    @Test void unusedFoodAndAnUnrelatedStackDecreaseCannotFarmTraining() throws Exception {
        try (Fixture f = new Fixture()) {
            f.listener.observeFeeding(f.feed());
            f.listener.runTasks();
            assertFalse(f.horse.values.containsKey(BetterHorseKeys.TRAINING_FEEDING_UNITS));
            f.listener.observeFeeding(f.feed());
            f.food.setAmount(1); // Dropping an item without a native feeding effect is insufficient.
            f.listener.runTasks();
            assertFalse(f.horse.values.containsKey(BetterHorseKeys.TRAINING_FEEDING_UNITS));
            assertFalse(f.horse.values.containsKey(BetterHorseKeys.TRAINING_FEED_COOLDOWN));
        }
    }

    @Test void actualSuccessfulFeedingAwardsOnlyOnceForDuplicateInteractionEvents() throws Exception {
        try (Fixture f = new Fixture()) {
            f.horse.health = 10.0;
            f.listener.observeFeeding(f.feed());
            f.listener.observeFeeding(f.feed());
            f.horse.health = 11.0;
            f.food.setAmount(1);
            f.listener.runTasks();
            assertEquals(1.0D, f.horse.values.get(BetterHorseKeys.TRAINING_FEEDING_UNITS));
            assertTrue(f.horse.values.containsKey(BetterHorseKeys.TRAINING_FEED_COOLDOWN));
        }
    }

    @Test void lateCancellationAndEntityRetirementPreventQueuedAwards() throws Exception {
        try (Fixture f = new Fixture()) {
            f.horse.health = 10;
            PlayerInteractEntityEvent event = f.feed();
            f.listener.observeFeeding(event);
            event.setCancelled(true);
            f.horse.health = 11;
            f.food.setAmount(1);
            f.listener.runTasks();
            assertFalse(f.horse.values.containsKey(BetterHorseKeys.TRAINING_FEEDING_UNITS));

            f.food.setAmount(2);
            f.listener.observeFeeding(f.feed());
            f.horse.health = 12;
            f.food.setAmount(1);
            f.listener.owning = false;
            f.listener.runTasks();
            assertFalse(f.horse.values.containsKey(BetterHorseKeys.TRAINING_FEEDING_UNITS));
        }
    }

    @Test void creativeRequiresAnActualFeedingEffectAndNaturalAgingIsNotOne() throws Exception {
        try (Fixture f = new Fixture()) {
            f.mode = GameMode.CREATIVE;
            f.horse.age = -1000;
            f.listener.observeFeeding(f.feed());
            f.horse.age++;
            f.horse.ticks++;
            f.listener.runTasks();
            assertFalse(f.horse.values.containsKey(BetterHorseKeys.TRAINING_FEEDING_UNITS));
            f.listener.observeFeeding(f.feed());
            f.horse.age += 200; // Accepted native growth food, no Creative item consumption.
            f.listener.runTasks();
            assertEquals(1.0D, f.horse.values.get(BetterHorseKeys.TRAINING_FEEDING_UNITS));
            assertEquals(2, f.food.getAmount());
        }
    }

    @Test void successfulFeedingStillRespectsTheTrainingCooldown() throws Exception {
        try (Fixture f = new Fixture()) {
            f.horse.health = 10;
            for (int i = 0; i < 2; i++) {
                f.food.setAmount(2);
                f.listener.observeFeeding(f.feed());
                f.horse.health++;
                f.food.setAmount(1);
                f.listener.runTasks();
            }
            assertEquals(1.0D, f.horse.values.get(BetterHorseKeys.TRAINING_FEEDING_UNITS));
        }
    }

    @Test void cancelledBreedDoesNotTouchParentsOrDispatchTheApiEvent() throws Exception {
        try (Fixture f = new Fixture()) {
            Mount father = f.mount("male"), mother = f.mount("female"), child = f.mount(null);
            Map<NamespacedKey, Object> beforeFather = new HashMap<>(father.values), beforeMother = new HashMap<>(mother.values);
            EntityBreedEvent event = new EntityBreedEvent(child.handle, father.handle, mother.handle, null, null, 0);
            event.setCancelled(true);
            new HorseBreedListener().onHorseBreed(event);
            assertEquals(beforeFather, father.values);
            assertEquals(beforeMother, mother.values);
            assertTrue(child.values.isEmpty());
            assertTrue(f.events.isEmpty());
        }
    }

    @Test void bredFoalImmediatelyHasApiRecognizedGeneticStatsAndMountType() throws Exception {
        try (Fixture f = new Fixture()) {
            Mount father = f.mount("male"), mother = f.mount("female"), child = f.mount(null);
            EntityBreedEvent event = new EntityBreedEvent(child.handle, father.handle, mother.handle, null, null, 0);
            new HorseBreedListener().onHorseBreed(event);
            assertFalse(event.isCancelled());
            assertTrue(BetterHorsesAPI.isBetterHorse(child.handle));
            assertNotNull(BetterHorsesAPI.getBetterHorse(child.handle));
            assertEquals("HORSE", child.values.get(BetterHorseKeys.MOUNT_TYPE));
            assertEquals(20.0D, child.values.get(BetterHorseKeys.HEALTH));
            assertEquals(.2D, child.values.get(BetterHorseKeys.SPEED));
            assertEquals(.7D, child.values.get(BetterHorseKeys.JUMP));
            assertEquals(child.values.get(BetterHorseKeys.HEALTH), child.values.get(BetterHorseKeys.BASE_HEALTH));
            assertTrue(father.values.containsKey(BetterHorseKeys.COOLDOWN));
        }
    }

    @Test void cancelledOrInvalidApiBreedResultCannotCommitParentCooldowns() throws Exception {
        try (Fixture f = new Fixture()) {
            Mount father = f.mount("male"), mother = f.mount("female"), child = f.mount(null);
            f.eventAction = event -> { if (event instanceof BetterHorseBreedEvent breed) breed.setCancelled(true); };
            EntityBreedEvent event = new EntityBreedEvent(child.handle, father.handle, mother.handle, null, null, 0);
            new HorseBreedListener().onHorseBreed(event);
            assertTrue(event.isCancelled());
            assertFalse(father.values.containsKey(BetterHorseKeys.COOLDOWN));
            assertFalse(mother.values.containsKey(BetterHorseKeys.COOLDOWN));
            f.eventAction = dispatched -> { if (dispatched instanceof BetterHorseBreedEvent breed) breed.setSpeed(Double.NaN); };
            event = new EntityBreedEvent(child.handle, father.handle, mother.handle, null, null, 0);
            new HorseBreedListener().onHorseBreed(event);
            assertTrue(event.isCancelled());
            assertFalse(father.values.containsKey(BetterHorseKeys.COOLDOWN));
            assertFalse(child.values.containsKey(BetterHorseKeys.SPEED));
        }
    }

    private static final class RecordingListener extends HorseFeedListener {
        final List<Runnable> tasks = new ArrayList<>();
        boolean owning = true;
        @Override protected void scheduleConfirmation(Player player, AbstractHorse horse, Runnable action, Runnable retired) { tasks.add(action); }
        @Override protected boolean ownsHorse(AbstractHorse horse) { return owning; }
        void runTasks() { List<Runnable> ready = List.copyOf(tasks); tasks.clear(); ready.forEach(Runnable::run); }
    }

    public static class TestPlugin extends BetterHorses {
        FileConfiguration configuration;
        @Override public FileConfiguration getConfig() { return configuration; }
    }

    static final class Fixture implements AutoCloseable {
        final Field pluginField = field(BetterHorses.class, "instance"), serverField = field(Bukkit.class, "server");
        final Object oldPlugin = pluginField.get(null), oldServer = serverField.get(null);
        final FileConfiguration config = new YamlConfiguration();
        final List<Event> events = new ArrayList<>();
        Consumer<Event> eventAction = ignored -> { };
        final UUID worldId = UUID.randomUUID(), playerId = UUID.randomUUID();
        final World world;
        final Mount horse;
        final Player player;
        final RecordingListener listener = new RecordingListener();
        ItemStack food = new ItemStack(Material.SUGAR, 2);
        GameMode mode = GameMode.SURVIVAL;

        Fixture() throws Exception {
            try {
                ItemFactory itemFactory = proxy(ItemFactory.class, (p, method, args) -> method.getName().equals("equals")
                        ? Objects.equals(args[0], args[1]) : zero(method.getReturnType()));
                PluginManager pluginManager = proxy(PluginManager.class, (p, method, args) -> {
                    if (method.getName().equals("callEvent")) { events.add((Event) args[0]); eventAction.accept((Event) args[0]); }
                    return zero(method.getReturnType());
                });
                Server server = proxy(Server.class, (p, method, args) -> switch (method.getName()) {
                    case "getBukkitVersion", "getMinecraftVersion" -> "1.20.6-R0.1-SNAPSHOT";
                    case "getName" -> "Paper";
                    case "isOwnedByCurrentRegion" -> true;
                    case "getItemFactory" -> itemFactory;
                    case "getPluginManager" -> pluginManager;
                    case "getLogger" -> java.util.logging.Logger.getLogger("HorseFeedBreedRegressionTest");
                    default -> zero(method.getReturnType());
                });
                serverField.set(null, server);
                TestPlugin plugin = (TestPlugin) ((Unsafe) field(Unsafe.class, "theUnsafe").get(null)).allocateInstance(TestPlugin.class);
                plugin.configuration = config;
                PluginDescriptionFile descriptor = new PluginDescriptionFile("BetterHorses", "test", TestPlugin.class.getName());
                field(JavaPlugin.class, "description").set(plugin, descriptor);
                field(JavaPlugin.class, "pluginMeta").set(plugin, descriptor);
                pluginField.set(null, plugin);
                config.set("training.enabled", true);
                config.set("training.categories.feeding.enabled", true);
                config.set("training.categories.feeding.food-values.SUGAR", 1.0D);
                config.set("training.categories.feeding.food-values.CARROT", 1.0D);
                config.set("training.categories.feeding.cooldown-seconds", 180L);
                config.set("horse-growth-settings.enabled", false);
                config.set("max-stats.health", 100.0D);
                config.set("max-stats.speed", 1.0D);
                config.set("max-stats.jump", 1.0D);
                config.set("mutation-factor.health", 0.0D);
                config.set("mutation-factor.speed", 0.0D);
                config.set("mutation-factor.jump", 0.0D);
                world = proxy(World.class, (p, method, args) -> switch (method.getName()) {
                    case "getName" -> "world";
                    case "getUID" -> worldId;
                    case "equals" -> p == args[0];
                    case "hashCode" -> System.identityHashCode(p);
                    default -> zero(method.getReturnType());
                });
                horse = mount("female");
                PlayerInventory inventory = proxy(PlayerInventory.class, (p, method, args) -> method.getName().equals("getItem") ? food : zero(method.getReturnType()));
                player = proxy(Player.class, (p, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "getInventory" -> inventory;
                    case "getGameMode" -> mode;
                    case "hasPermission", "isOnline" -> true;
                    case "getWorld" -> world;
                    case "getLocation" -> new Location(world, 0, 0, 0);
                    default -> zero(method.getReturnType());
                });
            } catch (Exception | Error error) { close(); throw error; }
        }
        Mount mount(String gender) { return new Mount(world, gender); }
        PlayerInteractEntityEvent feed() { return new PlayerInteractEntityEvent(player, horse.handle, EquipmentSlot.HAND); }
        @Override public void close() throws Exception { pluginField.set(null, oldPlugin); serverField.set(null, oldServer); }
    }

    static final class Mount {
        final Map<NamespacedKey, Object> values = new HashMap<>();
        final PersistentDataContainer data;
        final Horse handle;
        final Map<Attribute, FakeAttribute> attributes = new HashMap<>();
        final UUID id = UUID.randomUUID();
        double health = 20;
        int age, ticks = 100, temper = 100, loveTicks;
        Mount(World world, String gender) {
            data = proxy(PersistentDataContainer.class, (p, method, args) -> {
                NamespacedKey key = args != null && args.length > 0 && args[0] instanceof NamespacedKey k ? k : null;
                return switch (method.getName()) {
                    case "set" -> { values.put(key, args[2]); yield null; }
                    case "get" -> values.get(key);
                    case "getOrDefault" -> values.getOrDefault(key, args[2]);
                    case "has" -> values.containsKey(key);
                    case "remove" -> { values.remove(key); yield null; }
                    case "getKeys" -> new HashSet<>(values.keySet());
                    case "isEmpty" -> values.isEmpty();
                    default -> zero(method.getReturnType());
                };
            });
            if (gender != null) data.set(BetterHorseKeys.GENDER, PersistentDataType.STRING, gender);
            attributes.put(AttributeResolver.generic("MAX_HEALTH"), new FakeAttribute(AttributeResolver.generic("MAX_HEALTH"), 20.0));
            attributes.put(AttributeResolver.generic("MOVEMENT_SPEED"), new FakeAttribute(AttributeResolver.generic("MOVEMENT_SPEED"), .2));
            attributes.put(AttributeResolver.horseJumpStrength(), new FakeAttribute(AttributeResolver.horseJumpStrength(), .7));
            handle = proxy(Horse.class, (p, method, args) -> switch (method.getName()) {
                case "getPersistentDataContainer" -> data;
                case "getUniqueId" -> id;
                case "getType" -> EntityType.HORSE;
                case "isTamed", "isValid" -> true;
                case "isAdult" -> age >= 0;
                case "getAge" -> age;
                case "setAge" -> { age = (int) args[0]; yield null; }
                case "setBaby" -> { age = -24000; yield null; }
                case "setAdult" -> { age = 0; yield null; }
                case "getTicksLived" -> ticks;
                case "getDomestication" -> temper;
                case "getLoveModeTicks" -> loveTicks;
                case "getHealth" -> health;
                case "setHealth" -> { health = (double) args[0]; yield null; }
                case "getAttribute" -> attributes.get(args[0]);
                case "getWorld" -> world;
                case "getLocation", "getEyeLocation" -> new Location(world, 0, 0, 0);
                case "getPassengers" -> List.of();
                default -> zero(method.getReturnType());
            });
        }
    }

    private static final class FakeAttribute implements AttributeInstance {
        final Attribute type;
        final Map<UUID, AttributeModifier> modifiers = new HashMap<>();
        double base;
        FakeAttribute(Attribute type, double base) { this.type = type; this.base = base; }
        public Attribute getAttribute() { return type; }
        public double getBaseValue() { return base; }
        public void setBaseValue(double value) { base = value; }
        public Collection<AttributeModifier> getModifiers() { return List.copyOf(modifiers.values()); }
        public AttributeModifier getModifier(UUID id) { return modifiers.get(id); }
        public void addModifier(AttributeModifier modifier) { modifiers.put(modifier.getUniqueId(), modifier); }
        public void addTransientModifier(AttributeModifier modifier) { addModifier(modifier); }
        public void removeModifier(AttributeModifier modifier) { modifiers.remove(modifier.getUniqueId()); }
        public void removeModifier(UUID id) { modifiers.remove(id); }
        public double getDefaultValue() { return base; }
        public double getValue() { return base; }
    }

    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    private static Field field(Class<?> owner, String name) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Object zero(Class<?> type) {
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
