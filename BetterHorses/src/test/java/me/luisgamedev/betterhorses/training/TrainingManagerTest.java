package me.luisgamedev.betterhorses.training;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.traits.TraitRegistry;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TrainingManagerTest {
    @Test void migratesLegacyBonusesWithoutChangingGeneticStatsOrHealing() throws Exception {
        try (Fixture f = new Fixture()) {
            f.fullTraining(); f.originalBases();
            f.health.base = 24; f.speed.base = .24; f.jump.base = .84; f.currentHealth = 12;
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertTrue(TrainingManager.hasStatModel(f.horse));
            assertEquals(20, f.health.getBaseValue(), 1e-12);
            assertEquals(.2, f.speed.getBaseValue(), 1e-12);
            assertEquals(.7, f.jump.getBaseValue(), 1e-12);
            assertEquals(24, f.health.getValue(), 1e-12);
            assertEquals(.24, f.speed.getValue(), 1e-12);
            assertEquals(.84, f.jump.getValue(), 1e-12);
            assertEquals(12, f.currentHealth, 1e-12);
            assertEquals(20, f.number(BetterHorseKeys.HEALTH), 1e-12);
            assertEquals(.2, f.number(BetterHorseKeys.SPEED), 1e-12);
            assertEquals(.7, f.number(BetterHorseKeys.JUMP), 1e-12);
        }
    }

    @Test void repeatedRecalculationKeepsOneOwnedModifierAndPreservesFullHealth() throws Exception {
        try (Fixture f = new Fixture()) {
            f.fullTraining(); f.originalBases(); f.health.base = 24; f.currentHealth = 24;
            for (int i = 0; i < 20; i++) TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertEquals(24, f.currentHealth, 1e-12);
            for (FakeAttribute attribute : List.of(f.health, f.speed, f.jump)) {
                assertEquals(1, attribute.modifiers.size());
                assertTrue(attribute.transientIds.isEmpty());
                assertEquals(AttributeModifier.Operation.ADD_SCALAR, attribute.modifiers.values().iterator().next().getOperation());
            }
        }
    }

    @Test void nativeEntityReloadPreservesWoundedAndFullHealthBeforeReconciliation() throws Exception {
        try (Fixture f = new Fixture()) {
            f.fullTraining();
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            f.currentHealth = 12;
            f.simulateNativeReload();
            assertEquals(20, f.health.getBaseValue(), 1e-12);
            assertEquals(24, f.health.getValue(), 1e-12);
            assertEquals(12, f.currentHealth, 1e-12);
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertEquals(12, f.currentHealth, 1e-12);
            assertEquals(1, f.health.modifiers.size());
            f.currentHealth = 24;
            f.simulateNativeReload();
            assertEquals(24, f.currentHealth, 1e-12);
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertEquals(24, f.currentHealth, 1e-12);
        }
    }

    @Test void configurationChangesRemoveOnlyOwnedBonusesAndRetainProgress() throws Exception {
        try (Fixture f = new Fixture()) {
            f.fullTraining();
            AttributeModifier foreign = new AttributeModifier(UUID.randomUUID(), "external", .1, AttributeModifier.Operation.ADD_NUMBER);
            f.speed.addModifier(foreign);
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            f.config.set("training.categories.riding.enabled", false);
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertEquals(.3, f.speed.getValue(), 1e-12);
            assertEquals(Set.of(foreign.getUniqueId()), f.speed.modifiers.keySet());
            assertEquals(1, f.health.modifiers.size());
            f.config.set("training.enabled", false);
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertTrue(f.health.modifiers.isEmpty()); assertTrue(f.jump.modifiers.isEmpty());
            assertEquals(Set.of(foreign.getUniqueId()), f.speed.modifiers.keySet());
            assertEquals(1000, f.number(BetterHorseKeys.TRAINING_RIDING_UNITS), 1e-12);
            assertEquals(.2, f.number(BetterHorseKeys.SPEED), 1e-12);
            f.config.set("training.enabled", true);
            f.config.set("training.categories.riding.enabled", true);
            f.config.set("training.categories.riding.bonus-percent-per-progress-percent", .4);
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertEquals(.42, f.speed.getValue(), 1e-12);
        }
    }

    @Test void healthRatioUsesEffectiveMaximumAndSurvivesGeneticHealthChanges() throws Exception {
        try (Fixture f = new Fixture()) {
            f.fullTraining();
            AttributeModifier foreign = new AttributeModifier(UUID.randomUUID(), "external-health", 4, AttributeModifier.Operation.ADD_NUMBER);
            f.health.addModifier(foreign);
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            f.currentHealth = f.health.getValue() / 2;
            f.config.set("training.enabled", false);
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertEquals(24, f.health.getValue(), 1e-12);
            assertEquals(12, f.currentHealth, 1e-12);
            assertEquals(1, f.health.modifiers.size());
            f.health.removeModifier(foreign);
            f.config.set("training.enabled", true);
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            f.currentHealth = 12;
            TrainingManager.setBaseStat(f.horse, f.health.attribute, BetterHorseKeys.HEALTH, BetterHorseKeys.BASE_HEALTH, 10);
            assertEquals(10, f.health.getBaseValue(), 1e-12);
            assertEquals(12, f.health.getValue(), 1e-12);
            assertEquals(6, f.currentHealth, 1e-12);
            assertEquals(10, f.number(BetterHorseKeys.HEALTH), 1e-12);
            assertEquals(10, f.number(BetterHorseKeys.BASE_HEALTH), 1e-12);
        }
    }

    @Test void explicitStatEditsPersistWithoutStackingBonuses() throws Exception {
        try (Fixture f = new Fixture()) {
            f.fullTraining();
            TrainingManager.setBaseStat(f.horse, f.speed.attribute, BetterHorseKeys.SPEED, BetterHorseKeys.BASE_SPEED, .3);
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertEquals(.3, f.speed.getBaseValue(), 1e-12);
            assertEquals(.36, f.speed.getValue(), 1e-12);
            assertEquals(.3, f.number(BetterHorseKeys.SPEED), 1e-12);
            assertEquals(.3, f.number(BetterHorseKeys.BASE_SPEED), 1e-12);
            assertThrows(IllegalArgumentException.class, () -> TrainingManager.setBaseStat(f.horse,
                    f.speed.attribute, BetterHorseKeys.SPEED, BetterHorseKeys.BASE_SPEED, Double.NaN));
        }
    }

    @Test void expiredDashModifierIsRetiredWithoutChangingGeneticOrTrainingBases() throws Exception {
        try (Fixture f = new Fixture()) {
            f.fullTraining(); f.originalBases();
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            UUID dashId = UUID.fromString("ef1d94d8-a92c-4bc7-8c27-a18275d60e24");
            f.speed.addModifier(new AttributeModifier(dashId, "BetterHorses.dashboost", .5,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            NamespacedKey expiry = new NamespacedKey("betterhorses", "dashboost_expires_at");
            f.data.set(expiry, PersistentDataType.LONG, 0L);
            assertEquals(.36, f.speed.getValue(), 1e-12);
            TraitRegistry.reconcileTemporaryState(f.horse);
            assertNull(f.speed.getModifier(dashId));
            assertFalse(f.data.has(expiry));
            assertEquals(.2, f.speed.getBaseValue(), 1e-12);
            assertEquals(.24, f.speed.getValue(), 1e-12);
            assertEquals(.2, f.number(BetterHorseKeys.SPEED), 1e-12);
            f.speed.setBaseValue(.3);
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertEquals(.3, f.speed.getBaseValue(), 1e-12);
            assertEquals(.36, f.speed.getValue(), 1e-12);
            assertEquals(.2, f.number(BetterHorseKeys.BASE_SPEED), 1e-12);
        }
    }

    @Test void explicitSpeedEditRetiresOwnedDashWithoutOverwritingTheNewBase() throws Exception {
        try (Fixture f = new Fixture()) {
            f.fullTraining();
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            UUID dashId = UUID.fromString("ef1d94d8-a92c-4bc7-8c27-a18275d60e24");
            f.speed.addModifier(new AttributeModifier(dashId, "BetterHorses.dashboost", .5,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            NamespacedKey expiry = new NamespacedKey("betterhorses", "dashboost_expires_at");
            f.data.set(expiry, PersistentDataType.LONG, System.currentTimeMillis() + 5000L);
            TrainingManager.setBaseStat(f.horse, f.speed.attribute, BetterHorseKeys.SPEED, BetterHorseKeys.BASE_SPEED, .4);
            TraitRegistry.revertDashBoostIfActive(f.horse);
            assertNull(f.speed.getModifier(dashId));
            assertFalse(f.data.has(expiry));
            assertEquals(.4, f.speed.getBaseValue(), 1e-12);
            assertEquals(.48, f.speed.getValue(), 1e-12);
            assertEquals(.4, f.number(BetterHorseKeys.BASE_SPEED), 1e-12);
        }
    }

    @Test void configuredNegativeBonusesRemainSeparateAndAreRemovedWhenDisabled() throws Exception {
        try (Fixture f = new Fixture()) {
            f.fullTraining();
            f.config.set("training.categories.feeding.bonus-percent-per-progress-percent", -.2);
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertEquals(20, f.health.getBaseValue(), 1e-12);
            assertEquals(16, f.health.getValue(), 1e-12);
            assertEquals(20, f.number(BetterHorseKeys.HEALTH), 1e-12);
            f.currentHealth = 8;
            f.config.set("training.categories.feeding.enabled", false);
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertEquals(20, f.health.getValue(), 1e-12);
            assertEquals(10, f.currentHealth, 1e-12);
            assertTrue(f.health.modifiers.isEmpty());
            f.config.set("training.categories.feeding.enabled", true);
            f.config.set("training.categories.feeding.bonus-percent-per-progress-percent", -100);
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertTrue(f.health.getValue() > 0);
            f.config.set("training.enabled", false);
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertTrue(f.health.modifiers.isEmpty());
        }
    }

    @Test void injuredHorsesAreNotRaisedToOneHealthAndZeroHealthIsNotRevived() throws Exception {
        try (Fixture f = new Fixture()) {
            f.fullTraining(); f.currentHealth = .5;
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertEquals(.6, f.currentHealth, 1e-12);
            f.currentHealth = 0;
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertEquals(0, f.currentHealth, 1e-12);
        }
    }

    @Test void invalidProgressAndBonusSettingsNeverCreateNonfiniteModifiers() throws Exception {
        try (Fixture f = new Fixture()) {
            f.data.set(BetterHorseKeys.TRAINING_RIDING_UNITS, PersistentDataType.DOUBLE, Double.NaN);
            f.data.set(BetterHorseKeys.TRAINING_BRUSHING_UNITS, PersistentDataType.DOUBLE, Double.POSITIVE_INFINITY);
            f.data.set(BetterHorseKeys.TRAINING_FEEDING_UNITS, PersistentDataType.DOUBLE, -1.0);
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertTrue(f.speed.modifiers.isEmpty()); assertTrue(f.jump.modifiers.isEmpty()); assertTrue(f.health.modifiers.isEmpty());
            f.fullTraining();
            f.config.set("training.categories.riding.bonus-percent-per-progress-percent", Double.NaN);
            f.config.set("training.categories.brushing.bonus-percent-per-progress-percent", Double.POSITIVE_INFINITY);
            f.config.set("training.categories.feeding.bonus-percent-per-progress-percent", Double.NEGATIVE_INFINITY);
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertTrue(f.speed.modifiers.isEmpty()); assertTrue(f.jump.modifiers.isEmpty()); assertTrue(f.health.modifiers.isEmpty());
            f.config.set("training.categories.riding.units-per-percent", Double.NaN);
            assertEquals(100, TrainingManager.getProgressPercent(f.config, f.data, "riding", BetterHorseKeys.TRAINING_RIDING_UNITS), 1e-12);
        }
    }

    @Test void validStatPdcSeedsMissingGeneticBaselines() throws Exception {
        try (Fixture f = new Fixture()) {
            f.fullTraining(); f.data.set(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE, .15);
            TrainingManager.recalculateAndApplyBonuses(f.horse);
            assertEquals(.15, f.speed.getBaseValue(), 1e-12);
            assertEquals(.18, f.speed.getValue(), 1e-12);
            assertEquals(.15, f.number(BetterHorseKeys.BASE_SPEED), 1e-12);
        }
    }

    public static final class TestPlugin extends BetterHorses {
        FileConfiguration testConfig;
        @Override public FileConfiguration getConfig() { return testConfig; }
    }

    private static final class Fixture implements AutoCloseable {
        final Field serverField = field(Bukkit.class, "server");
        final Field pluginField = field(BetterHorses.class, "instance");
        final Object oldServer = serverField.get(null), oldPlugin = pluginField.get(null);
        final FileConfiguration config = new YamlConfiguration();
        final UUID id = UUID.randomUUID();
        final Map<NamespacedKey, Object> values = new HashMap<>();
        final PersistentDataContainer data;
        final FakeAttribute health, speed, jump;
        final AbstractHorse horse;
        double currentHealth = 20;

        Fixture() throws Exception {
            try {
                Object server = Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getBukkitVersion", "getMinecraftVersion" -> "1.20.6-R0.1-SNAPSHOT";
                    case "getName" -> "Paper";
                    case "getLogger" -> java.util.logging.Logger.getLogger("TrainingManagerTest");
                    default -> defaultValue(method.getReturnType());
                });
                serverField.set(null, server);
                TestPlugin plugin = (TestPlugin) ((Unsafe) field(Unsafe.class, "theUnsafe").get(null)).allocateInstance(TestPlugin.class);
                plugin.testConfig = config;
                PluginDescriptionFile description = new PluginDescriptionFile("BetterHorses", "test", TestPlugin.class.getName());
                field(JavaPlugin.class, "description").set(plugin, description);
                field(JavaPlugin.class, "pluginMeta").set(plugin, description);
                pluginField.set(null, plugin);
                config.set("training.enabled", true);
                data = (PersistentDataContainer) Proxy.newProxyInstance(PersistentDataContainer.class.getClassLoader(), new Class<?>[]{PersistentDataContainer.class}, (proxy, method, args) -> {
                    NamespacedKey key = args != null && args.length > 0 && args[0] instanceof NamespacedKey k ? k : null;
                    return switch (method.getName()) {
                        case "set" -> { values.put(key, args[2]); yield null; }
                        case "get" -> values.get(key);
                        case "getOrDefault" -> values.getOrDefault(key, args[2]);
                        case "has" -> values.containsKey(key);
                        case "remove" -> { values.remove(key); yield null; }
                        case "getKeys" -> new HashSet<>(values.keySet());
                        case "isEmpty" -> values.isEmpty();
                        default -> defaultValue(method.getReturnType());
                    };
                });
                health = new FakeAttribute(this, AttributeResolver.generic("MAX_HEALTH"), 20);
                speed = new FakeAttribute(this, AttributeResolver.generic("MOVEMENT_SPEED"), .2);
                jump = new FakeAttribute(this, AttributeResolver.horseJumpStrength(), .7);
                horse = (AbstractHorse) Proxy.newProxyInstance(AbstractHorse.class.getClassLoader(), new Class<?>[]{AbstractHorse.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getPersistentDataContainer" -> data;
                    case "getUniqueId" -> id;
                    case "getAttribute" -> args[0] == health.attribute ? health : args[0] == speed.attribute ? speed : args[0] == jump.attribute ? jump : null;
                    case "getHealth" -> currentHealth;
                    case "setHealth" -> { currentHealth = (double) args[0]; if (currentHealth < 0 || currentHealth > health.getValue() + 1e-9) throw new IllegalArgumentException("Invalid health"); yield null; }
                    case "isValid" -> true;
                    case "isDead" -> currentHealth <= 0;
                    case "getType" -> EntityType.HORSE;
                    default -> defaultValue(method.getReturnType());
                });
            } catch (Exception | Error failure) { close(); throw failure; }
        }
        void originalBases() {
            data.set(BetterHorseKeys.BASE_HEALTH, PersistentDataType.DOUBLE, 20.0);
            data.set(BetterHorseKeys.BASE_SPEED, PersistentDataType.DOUBLE, .2);
            data.set(BetterHorseKeys.BASE_JUMP, PersistentDataType.DOUBLE, .7);
        }
        void fullTraining() {
            data.set(BetterHorseKeys.TRAINING_RIDING_UNITS, PersistentDataType.DOUBLE, 1000.0);
            data.set(BetterHorseKeys.TRAINING_BRUSHING_UNITS, PersistentDataType.DOUBLE, 1000.0);
            data.set(BetterHorseKeys.TRAINING_FEEDING_UNITS, PersistentDataType.DOUBLE, 1000.0);
        }
        void simulateNativeReload() {
            double savedHealth = currentHealth;
            for (FakeAttribute attribute : List.of(health, speed, jump)) {
                List<AttributeModifier> saved = attribute.modifiers.values().stream()
                        .filter(modifier -> !attribute.transientIds.contains(modifier.getUniqueId())).toList();
                attribute.modifiers.clear();
                attribute.transientIds.clear();
                for (AttributeModifier modifier : saved) attribute.modifiers.put(modifier.getUniqueId(), modifier);
            }
            currentHealth = Math.min(savedHealth, health.getValue());
        }
        double number(NamespacedKey key) { return data.get(key, PersistentDataType.DOUBLE); }
        @Override public void close() throws Exception { pluginField.set(null, oldPlugin); serverField.set(null, oldServer); }
    }

    private static final class FakeAttribute implements AttributeInstance {
        final Fixture owner;
        final Attribute attribute;
        final Map<UUID, AttributeModifier> modifiers = new HashMap<>();
        final Set<UUID> transientIds = new HashSet<>();
        double base;
        FakeAttribute(Fixture owner, Attribute attribute, double base) { this.owner = owner; this.attribute = attribute; this.base = base; }
        @Override public Attribute getAttribute() { return attribute; }
        @Override public double getBaseValue() { return base; }
        @Override public void setBaseValue(double value) { base = value; clampHealth(); }
        @Override public Collection<AttributeModifier> getModifiers() { return new ArrayList<>(modifiers.values()); }
        @Override public AttributeModifier getModifier(UUID id) { return modifiers.get(id); }
        @Override public void removeModifier(UUID id) { modifiers.remove(id); transientIds.remove(id); clampHealth(); }
        @Override public void removeModifier(AttributeModifier modifier) { removeModifier(modifier.getUniqueId()); }
        @Override public void addModifier(AttributeModifier modifier) { if (modifiers.putIfAbsent(modifier.getUniqueId(), modifier) != null) throw new IllegalArgumentException("Duplicate modifier"); clampHealth(); }
        @Override public void addTransientModifier(AttributeModifier modifier) { addModifier(modifier); transientIds.add(modifier.getUniqueId()); }
        @Override public double getValue() {
            double value = base;
            for (AttributeModifier m : modifiers.values()) if (m.getOperation() == AttributeModifier.Operation.ADD_NUMBER) value += m.getAmount();
            double withFlatBonuses = value;
            for (AttributeModifier m : modifiers.values()) if (m.getOperation() == AttributeModifier.Operation.ADD_SCALAR) value += withFlatBonuses * m.getAmount();
            for (AttributeModifier m : modifiers.values()) if (m.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1) value *= 1 + m.getAmount();
            return value;
        }
        @Override public double getDefaultValue() { return base; }
        void clampHealth() { if (owner.health == this) owner.currentHealth = Math.min(owner.currentHealth, getValue()); }
    }

    private static Field field(Class<?> owner, String name) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0f;
        if (type == long.class) return 0L;
        if (type == int.class) return 0;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        return null;
    }
}
