package me.luisgamedev.betterhorses.growing;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.listeners.HorseBreedListener;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Donkey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Mule;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.bukkit.plugin.PluginManager;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HorseSizeTest {
    @Test void defaultVariationIncludesTenPercentEitherSideOfNormal() throws Exception {
        try (Fixture f = new Fixture()) {
            assertEquals(0.9D, HorseSize.ensure(f.data(), f.config, () -> 0.0D), 1e-12);
            assertEquals(1.0D, HorseSize.ensure(f.data(), f.config, () -> 0.5D), 1e-12);
            assertEquals(1.1D, HorseSize.ensure(f.data(), f.config, () -> 1.0D), 1e-12);
            f.config.set("random-size-factor.factor", 0.25D);
            assertEquals(0.75D, HorseSize.ensure(f.data(), f.config, () -> 0.0D), 1e-12);
            assertEquals(1.25D, HorseSize.ensure(f.data(), f.config, () -> 1.0D), 1e-12);
        }
    }

    @Test void establishedSizeSurvivesRepeatedUpdatesAndConfigurationChanges() throws Exception {
        try (Fixture f = new Fixture()) {
            PersistentDataContainer horse = f.data();
            AtomicInteger draws = new AtomicInteger();
            double established = HorseSize.ensure(horse, f.config, () -> { draws.incrementAndGet(); return 0.15D; });
            assertEquals(0.93D, established, 1e-12);
            for (int i = 0; i < 20; i++) {
                f.config.set("random-size-factor.factor", i / 20.0D);
                f.config.set("random-size-factor.enabled", i % 2 == 0);
                assertEquals(established, HorseSize.ensure(horse, f.config, () -> { draws.incrementAndGet(); return 0.95D; }));
            }
            assertEquals(1, draws.get());
            assertEquals(established, horse.get(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE));
        }
    }

    @Test void captureAndRespawnCopyExactValueOverTheTemporarySpawnAssignment() throws Exception {
        try (Fixture f = new Fixture()) {
            PersistentDataContainer original = f.data(), item = f.data(), respawned = f.data();
            double size = HorseSize.ensure(original, f.config, () -> 0.173D);
            HorseSize.copy(original, item, f.config);
            HorseSize.ensure(respawned, f.config, () -> 0.99D);
            f.config.set("random-size-factor.factor", 0.8D);
            HorseSize.copy(item, respawned, f.config);
            assertEquals(size, original.get(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE));
            assertEquals(size, item.get(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE));
            assertEquals(size, respawned.get(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE));
        }
    }

    @Test void previouslyUnmarkedHorseIsAssignedOnceWhenCaptured() throws Exception {
        try (Fixture f = new Fixture()) {
            PersistentDataContainer oldHorse = f.data(), firstItem = f.data(), secondItem = f.data();
            HorseSize.copy(oldHorse, firstItem, f.config);
            Double established = oldHorse.get(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE);
            assertNotNull(established);
            assertTrue(established >= 0.9D && established <= 1.1D);
            HorseSize.copy(oldHorse, secondItem, f.config);
            assertEquals(established, firstItem.get(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE));
            assertEquals(established, secondItem.get(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE));
        }
    }

    @Test void newbornAssignmentsUseTheirOwnDrawWithoutMutatingEitherParent() throws Exception {
        try (Fixture f = new Fixture()) {
            PersistentDataContainer father = f.data(), mother = f.data(), firstFoal = f.data(), secondFoal = f.data();
            HorseSize.ensure(father, f.config, () -> 1.0D);
            HorseSize.ensure(mother, f.config, () -> 1.0D);
            assertEquals(0.9D, HorseSize.ensure(firstFoal, f.config, () -> 0.0D), 1e-12);
            assertEquals(1.0D, HorseSize.ensure(secondFoal, f.config, () -> 0.5D), 1e-12);
            assertEquals(1.1D, father.get(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE), 1e-12);
            assertEquals(1.1D, mother.get(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE), 1e-12);
        }
    }

    @Test void actualMuleBirthReplacesCopiedParentalSizeBeforeCrossbreedHandlingReturns() throws Exception {
        try (Fixture f = new Fixture()) {
            f.config.set("settings.mount-types.mules", true);
            f.config.set("settings.mount-types.donkeys", true);
            PersistentDataContainer childData = f.data(), motherData = f.data(), fatherData = f.data();
            childData.set(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE, 4.0D);
            motherData.set(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE, 0.9D);
            fatherData.set(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE, 1.1D);
            motherData.set(BetterHorseKeys.GENDER, PersistentDataType.STRING, "female");
            fatherData.set(BetterHorseKeys.GENDER, PersistentDataType.STRING, "male");
            Mule child = f.mount(Mule.class, EntityType.MULE, childData, new double[]{1.0D});
            Horse mother = f.mount(Horse.class, EntityType.HORSE, motherData, new double[]{1.0D});
            Donkey father = f.mount(Donkey.class, EntityType.DONKEY, fatherData, new double[]{1.0D});
            EntityBreedEvent birth = new EntityBreedEvent(child, mother, father, null, null, 0);
            new HorseBreedListener().onHorseBreed(birth);
            Double assigned = childData.get(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE);
            assertNotNull(assigned);
            assertTrue(assigned >= 0.9D && assigned <= 1.1D);
            assertNotEquals(4.0D, assigned);
            assertEquals(0.9D, motherData.get(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE));
            assertEquals(1.1D, fatherData.get(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE));
            assertFalse(birth.isCancelled());
        }
    }

    @Test void corruptStoredNumbersAreRepairedThenKeptStable() throws Exception {
        try (Fixture f = new Fixture()) {
            for (double corrupt : new double[]{0.0D, -1.0D, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
                PersistentDataContainer data = f.data();
                data.set(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE, corrupt);
                assertEquals(1.04D, HorseSize.ensure(data, f.config, () -> 0.7D), 1e-12);
                assertEquals(1.04D, HorseSize.ensure(data, f.config, () -> { fail("A repaired value must not be rerolled"); return 0.0D; }), 1e-12);
            }
        }
    }

    @Test void invalidConfigurationAndRandomInputsCannotProduceInvalidScaleData() throws Exception {
        try (Fixture f = new Fixture()) {
            f.config.set("random-size-factor.factor", -1.0D);
            assertEquals(1.0D, HorseSize.ensure(f.data(), f.config, () -> 0.0D));
            f.config.set("random-size-factor.factor", 2.0D);
            assertEquals(0.01D, HorseSize.ensure(f.data(), f.config, () -> 0.0D), 1e-12);
            assertEquals(1.99D, HorseSize.ensure(f.data(), f.config, () -> 1.0D), 1e-12);
            for (double nonfinite : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                f.config.set("random-size-factor.factor", nonfinite);
                assertEquals(0.9D, HorseSize.ensure(f.data(), f.config, () -> 0.0D), 1e-12);
                assertEquals(1.0D, HorseSize.ensure(f.data(), f.config, () -> nonfinite), 1e-12);
            }
        }
    }

    @Test void sizeVariationIsAppliedAfterGrowthWithoutChangingStageProgression() {
        for (double multiplier : new double[]{0.9D, 1.0D, 1.1D}) {
            double previous = 0.0D;
            for (int stage = 1; stage <= GrowthScale.MAX_STAGE; stage++) {
                boolean adult = stage >= 7;
                double modelScale = adult ? 1.0D : 0.7D;
                double growthScale = GrowthScale.forStage(stage, 1.3D, adult, 0.7D);
                double visualSize = HorseSize.compose(growthScale, multiplier, true) * modelScale;
                assertEquals(GrowthScale.forStage(stage, 1.3D) * multiplier, visualSize, 1e-12);
                if (stage > 1) assertEquals(1.3D / 18.0D * multiplier, visualSize - previous, 1e-12);
                previous = visualSize;
            }
        }
    }

    @Test void repeatedApplicationDoesNotCompoundAndDisablingPreservesStoredSize() throws Exception {
        try (Fixture f = new Fixture()) {
            PersistentDataContainer data = f.data();
            data.set(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE, 1.08D);
            double[] actualScale = {1.0D};
            AbstractHorse horse = f.horse(data, actualScale);
            for (int i = 0; i < 20; i++) HorseSize.apply(horse, 1.3D, f.config);
            assertEquals(1.404D, actualScale[0], 1e-12);
            f.config.set("random-size-factor.enabled", false);
            HorseSize.apply(horse, 1.3D, f.config);
            assertEquals(1.3D, actualScale[0], 1e-12);
            assertEquals(1.08D, data.get(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE));
            f.config.set("random-size-factor.enabled", true);
            HorseSize.apply(horse, 1.3D, f.config);
            assertEquals(1.404D, actualScale[0], 1e-12);
        }
    }

    @Test void disabledVariationWithoutGrowthPreservesExternalScaleAndRemovesItsOwnEffectOnlyOnce() throws Exception {
        try (Fixture f = new Fixture()) {
            PersistentDataContainer data = f.data();
            data.set(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE, 1.08D);
            double[] scale = {2.0D};
            AbstractHorse horse = f.horse(data, scale);
            f.config.set("random-size-factor.enabled", false);
            HorseSize.applyWithoutGrowth(horse, f.config);
            assertEquals(2.0D, scale[0]);
            assertFalse(data.has(BetterHorseKeys.RANDOM_SIZE_APPLIED_SCALE));

            f.config.set("random-size-factor.enabled", true);
            HorseSize.applyWithoutGrowth(horse, f.config);
            assertEquals(1.08D, scale[0]);
            assertEquals(1.08D, data.get(BetterHorseKeys.RANDOM_SIZE_APPLIED_SCALE, PersistentDataType.DOUBLE));
            f.config.set("random-size-factor.enabled", false);
            HorseSize.applyWithoutGrowth(horse, f.config);
            assertEquals(1.0D, scale[0]);
            assertFalse(data.has(BetterHorseKeys.RANDOM_SIZE_APPLIED_SCALE));

            scale[0] = 2.0D;
            for (int i = 0; i < 20; i++) HorseSize.applyWithoutGrowth(horse, f.config);
            assertEquals(2.0D, scale[0]);
            assertEquals(1.08D, data.get(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE));
        }
    }

    @Test void disablingVariationWithoutGrowthRetainsExternalChangesMadeWhileVariationWasActive() throws Exception {
        try (Fixture f = new Fixture()) {
            PersistentDataContainer data = f.data();
            data.set(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE, 1.08D);
            double[] scale = {1.0D};
            AbstractHorse horse = f.horse(data, scale);
            HorseSize.applyWithoutGrowth(horse, f.config);
            assertEquals(1.08D, scale[0]);
            scale[0] = 2.0D;
            f.config.set("random-size-factor.enabled", false);
            HorseSize.applyWithoutGrowth(horse, f.config);
            assertEquals(2.0D, scale[0]);
            assertFalse(data.has(BetterHorseKeys.RANDOM_SIZE_APPLIED_SCALE));
            HorseSize.applyWithoutGrowth(horse, f.config);
            assertEquals(2.0D, scale[0]);
            assertEquals(1.08D, data.get(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER, PersistentDataType.DOUBLE));
        }
    }

    @Test void compositionKeepsExtremeValuesInsideTheServerAttributeBounds() {
        assertEquals(0.0625D, HorseSize.compose(0.001D, 0.1D, true));
        assertEquals(16.0D, HorseSize.compose(16.0D, 1.1D, true));
        assertEquals(16.0D, HorseSize.compose(Double.MAX_VALUE, Double.MAX_VALUE, true));
        assertEquals(1.3D, HorseSize.compose(1.3D, 0.9D, false));
        for (double invalid : new double[]{0.0D, -1.0D, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertEquals(1.0D, HorseSize.compose(invalid, 1.0D, true));
            assertEquals(1.3D, HorseSize.compose(1.3D, invalid, true));
        }
    }

    @Test void attributeBoundsAreAppliedAfterIndividualVariationRatherThanBeforeIt() {
        double oversizedGrowth = GrowthScale.forStageUnclamped(10, 20.0D, true, 0.7D);
        assertEquals(10.0D, HorseSize.compose(oversizedGrowth, 0.5D, true));
        double undersizedGrowth = GrowthScale.forStageUnclamped(10, 0.05D, true, 0.7D);
        assertEquals(0.075D, HorseSize.compose(undersizedGrowth, 1.5D, true), 1e-12);
        double overflowingBabyGrowth = GrowthScale.forStageUnclamped(10, Double.MAX_VALUE, false, 0.5D);
        assertTrue(Double.isFinite(overflowingBabyGrowth));
        assertEquals(16.0D, HorseSize.compose(overflowingBabyGrowth, 0.9D, true));
        double tinyGrowth = GrowthScale.forStageUnclamped(1, Double.MIN_VALUE, true, 0.7D);
        assertTrue(tinyGrowth > 0.0D);
        assertEquals(0.0625D, HorseSize.compose(tinyGrowth, 0.01D, true));
    }

    public static final class TestPlugin extends BetterHorses {
        FileConfiguration testConfig;
        @Override public FileConfiguration getConfig() { return testConfig; }
    }

    private static final class Fixture implements AutoCloseable {
        final Field pluginField = field(BetterHorses.class, "instance"), serverField = field(Bukkit.class, "server");
        final Object previousPlugin = pluginField.get(null), previousServer = serverField.get(null);
        final FileConfiguration config = new YamlConfiguration();

        Fixture() throws Exception {
            try {
                Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getBukkitVersion", "getMinecraftVersion" -> "1.20.6-R0.1-SNAPSHOT";
                    case "getName" -> "Paper";
                    case "isOwnedByCurrentRegion" -> true;
                    case "getPluginManager" -> Proxy.newProxyInstance(PluginManager.class.getClassLoader(), new Class<?>[]{PluginManager.class},
                            (target, callback, values) -> defaultValue(callback.getReturnType()));
                    default -> defaultValue(method.getReturnType());
                });
                serverField.set(null, server);
                TestPlugin plugin = (TestPlugin) ((Unsafe) field(Unsafe.class, "theUnsafe").get(null)).allocateInstance(TestPlugin.class);
                plugin.testConfig = config;
                PluginDescriptionFile description = new PluginDescriptionFile("BetterHorses", "test", TestPlugin.class.getName());
                field(JavaPlugin.class, "description").set(plugin, description);
                field(JavaPlugin.class, "pluginMeta").set(plugin, description);
                pluginField.set(null, plugin);
            } catch (Exception | Error failure) { close(); throw failure; }
        }

        PersistentDataContainer data() {
            Map<NamespacedKey, Object> stored = new HashMap<>();
            return (PersistentDataContainer) Proxy.newProxyInstance(PersistentDataContainer.class.getClassLoader(), new Class<?>[]{PersistentDataContainer.class}, (proxy, method, args) -> {
                NamespacedKey key = args != null && args.length > 0 && args[0] instanceof NamespacedKey value ? value : null;
                return switch (method.getName()) {
                    case "set" -> { stored.put(key, args[2]); yield null; }
                    case "get" -> stored.get(key);
                    case "getOrDefault" -> stored.getOrDefault(key, args[2]);
                    case "has" -> stored.containsKey(key);
                    case "remove" -> { stored.remove(key); yield null; }
                    case "getKeys" -> new HashSet<>(stored.keySet());
                    case "isEmpty" -> stored.isEmpty();
                    default -> defaultValue(method.getReturnType());
                };
            });
        }

        AbstractHorse horse(PersistentDataContainer data, double[] scale) {
            return mount(AbstractHorse.class, EntityType.HORSE, data, scale);
        }

        <T extends AbstractHorse> T mount(Class<T> mountClass, EntityType type, PersistentDataContainer data, double[] scale) {
            AttributeInstance attribute = (AttributeInstance) Proxy.newProxyInstance(AttributeInstance.class.getClassLoader(), new Class<?>[]{AttributeInstance.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getBaseValue", "getValue" -> scale[0];
                case "setBaseValue" -> { scale[0] = (double) args[0]; yield null; }
                case "getModifiers" -> List.of();
                default -> defaultValue(method.getReturnType());
            });
            UUID id = UUID.randomUUID();
            return mountClass.cast(Proxy.newProxyInstance(mountClass.getClassLoader(), new Class<?>[]{mountClass}, (proxy, method, args) -> switch (method.getName()) {
                case "getPersistentDataContainer" -> data;
                case "getAttribute" -> attribute;
                case "getType" -> type;
                case "getUniqueId" -> id;
                case "isValid" -> true;
                default -> defaultValue(method.getReturnType());
            }));
        }

        @Override public void close() throws Exception { pluginField.set(null, previousPlugin); serverField.set(null, previousServer); }
    }

    private static Field field(Class<?> owner, String name) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
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
