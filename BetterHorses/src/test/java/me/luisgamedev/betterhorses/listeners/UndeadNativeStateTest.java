package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static me.luisgamedev.betterhorses.listeners.TraitLifecycleRegressionTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises actual transformations, including their native properties and legacy snapshots. */
class UndeadNativeStateTest {
    @Test void llamaStrengthAndColorAreRestoredBeforeAnEmptyLargeChest() throws Exception {
        try (Fixture f = new Fixture()) {
            f.config.set("settings.mount-types.llamas", true);
            NativeState<Llama> original = new NativeState<>(f, Llama.class);
            original.strength = 5; original.color = Llama.Color.GRAY;
            original.chest = true; original.actor.slots = new ItemStack[17];
            NativeState<SkeletonHorse> skeleton = new NativeState<>(f, SkeletonHorse.class);
            f.spawnResult = skeleton.actor;
            UndeadTraitListener listener = new UndeadTraitListener();
            assertEquals(true, invoke(listener, "transformToSkeleton", new Class<?>[]{AbstractHorse.class}, original.actor.handle));
            assertEquals(5, skeleton.actor.values.get(BetterHorseKeys.UNDEAD_ORIGINAL_LLAMA_STRENGTH));
            assertEquals("GRAY", skeleton.actor.values.get(BetterHorseKeys.UNDEAD_ORIGINAL_LLAMA_COLOR));

            NativeState<Llama> restored = new NativeState<>(f, Llama.class);
            restored.strength = 1; restored.color = Llama.Color.WHITE;
            f.spawnResult = restored.actor;
            assertEquals(true, invoke(listener, "transformBack", new Class<?>[]{SkeletonHorse.class}, skeleton.actor.handle));
            assertEquals(5, restored.strength);
            assertEquals(Llama.Color.GRAY, restored.color);
            assertTrue(restored.chest);
            assertEquals(17, restored.actor.slots.length);
            assertFalse(skeleton.actor.valid);
            assertFalse(restored.actor.values.containsKey(BetterHorseKeys.UNDEAD_ORIGINAL_LLAMA_STRENGTH));
        }
    }

    @Test void legacyLlamaChestInfersItsRequiredCapacityWithoutRerolling() throws Exception {
        try (Fixture f = new Fixture()) {
            NativeState<SkeletonHorse> skeleton = new NativeState<>(f, SkeletonHorse.class);
            skeleton.actor.values.put(BetterHorseKeys.UNDEAD_SKELETON, (byte) 1);
            skeleton.actor.values.put(BetterHorseKeys.UNDEAD_ORIGINAL_TYPE, "LLAMA");
            skeleton.actor.values.put(BetterHorseKeys.UNDEAD_CHESTED, (byte) 1);
            UndeadTraitListener listener = new UndeadTraitListener();
            skeleton.actor.values.put(BetterHorseKeys.UNDEAD_CHEST_CONTENTS,
                    invoke(listener, "serializeStorageContents", new Class<?>[]{ItemStack[].class}, (Object) new ItemStack[17]));
            NativeState<Llama> restored = new NativeState<>(f, Llama.class);
            f.spawnResult = restored.actor;
            assertEquals(true, invoke(listener, "transformBack", new Class<?>[]{SkeletonHorse.class}, skeleton.actor.handle));
            assertEquals(5, restored.strength);
            assertEquals(17, restored.actor.slots.length);
        }
    }

    @Test void vanillaBabyKeepsNativeAgeAndContinuesAgingWhileUndead() throws Exception {
        try (Fixture f = new Fixture()) {
            NativeState<Horse> original = new NativeState<>(f, Horse.class);
            original.age = -12000;
            NativeState<SkeletonHorse> skeleton = new NativeState<>(f, SkeletonHorse.class);
            f.spawnResult = skeleton.actor;
            UndeadTraitListener listener = new UndeadTraitListener();
            assertEquals(true, invoke(listener, "transformToSkeleton", new Class<?>[]{AbstractHorse.class}, original.actor.handle));
            assertEquals(-12000, skeleton.age);
            assertFalse(skeleton.ageLock);
            skeleton.age = -11980; // Native age progressed for twenty server ticks.
            NativeState<Horse> restored = new NativeState<>(f, Horse.class);
            f.spawnResult = restored.actor;
            assertEquals(true, invoke(listener, "transformBack", new Class<?>[]{SkeletonHorse.class}, skeleton.actor.handle));
            assertEquals(-11980, restored.age);
            assertFalse(restored.ageLock);
        }
    }

    @Test void ageLockedVanillaMountKeepsItsOriginalAgeAndLock() throws Exception {
        try (Fixture f = new Fixture()) {
            NativeState<Horse> original = new NativeState<>(f, Horse.class);
            original.age = -12000; original.ageLock = true;
            NativeState<SkeletonHorse> skeleton = new NativeState<>(f, SkeletonHorse.class);
            f.spawnResult = skeleton.actor;
            UndeadTraitListener listener = new UndeadTraitListener();
            assertEquals(true, invoke(listener, "transformToSkeleton", new Class<?>[]{AbstractHorse.class}, original.actor.handle));
            assertTrue(skeleton.ageLock);
            NativeState<Horse> restored = new NativeState<>(f, Horse.class);
            f.spawnResult = restored.actor;
            assertEquals(true, invoke(listener, "transformBack", new Class<?>[]{SkeletonHorse.class}, skeleton.actor.handle));
            assertEquals(-12000, restored.age);
            assertTrue(restored.ageLock);
        }
    }

    @Test void skeletonCustomGrowthDoesNotMatureAnOriginallyVanillaBaby() throws Exception {
        try (Fixture f = new Fixture()) {
            f.config.set("horse-growth-settings.enabled", true);
            f.config.set("horse-growth-settings.mount-types.donkeys", false);
            NativeState<Donkey> original = new NativeState<>(f, Donkey.class);
            original.age = -12000;
            NativeState<SkeletonHorse> skeleton = new NativeState<>(f, SkeletonHorse.class);
            f.spawnResult = skeleton.actor;
            UndeadTraitListener listener = new UndeadTraitListener();
            assertEquals(true, invoke(listener, "transformToSkeleton", new Class<?>[]{AbstractHorse.class}, original.actor.handle));
            skeleton.age = 0; // Custom skeleton stages have their own maturity threshold.
            NativeState<Donkey> restored = new NativeState<>(f, Donkey.class);
            f.spawnResult = restored.actor;
            assertEquals(true, invoke(listener, "transformBack", new Class<?>[]{SkeletonHorse.class}, skeleton.actor.handle));
            assertEquals(-12000, restored.age);
            assertFalse(restored.ageLock);
        }
    }

    @Test void unsupportedCamelHuskNeverFallsBackToAnOrdinaryHorse() throws Exception {
        // The baseline API predates camel husks: a stored newer mount must be retained,
        // not silently downgraded to an unrelated horse on an older server.
        try (Fixture f = new Fixture()) {
            NativeState<SkeletonHorse> skeleton = new NativeState<>(f, SkeletonHorse.class);
            skeleton.actor.values.put(BetterHorseKeys.UNDEAD_SKELETON, (byte) 1);
            skeleton.actor.values.put(BetterHorseKeys.UNDEAD_ORIGINAL_TYPE, "CAMEL_HUSK");
            f.spawnResult = f.actor(Horse.class);
            assertEquals(false, invoke(new UndeadTraitListener(), "transformBack", new Class<?>[]{SkeletonHorse.class}, skeleton.actor.handle));
            assertTrue(skeleton.actor.valid);
            assertTrue(f.spawnResult.valid);
        }
    }

    @Test void originalNativePropertiesSurviveTheStoredSkeletonMetadataRoundTrip() throws Exception {
        try (Fixture ignored = new Fixture()) {
            Map<NamespacedKey, Object> snapshot = new HashMap<>();
            snapshot.put(BetterHorseKeys.UNDEAD_ORIGINAL_AGE, -12000);
            snapshot.put(BetterHorseKeys.UNDEAD_ORIGINAL_AGE_LOCK, (byte) 1);
            snapshot.put(BetterHorseKeys.UNDEAD_ORIGINAL_LLAMA_STRENGTH, 5);
            snapshot.put(BetterHorseKeys.UNDEAD_ORIGINAL_LLAMA_COLOR, "GRAY");
            Map<NamespacedKey, Object> item = new HashMap<>(), restored = new HashMap<>();
            Method copy = BetterHorsesAPI.class.getDeclaredMethod("copyUndeadData", PersistentDataContainer.class, PersistentDataContainer.class);
            copy.setAccessible(true);
            copy.invoke(null, data(snapshot), data(item));
            copy.invoke(null, data(item), data(restored));
            assertEquals(snapshot, restored);
        }
    }

    private static final class NativeState<T extends AbstractHorse> {
        final Actor<T> actor;
        int age, strength = 1;
        boolean ageLock, chest;
        Llama.Color color = Llama.Color.WHITE;

        NativeState(Fixture f, Class<T> type) throws Exception {
            actor = f.actor(type);
            T wrapper = proxy(type, (target, method, args) -> switch (method.getName()) {
                case "getType" -> type == Llama.class ? EntityType.LLAMA : actor.invoke(target, method, args);
                case "getAge" -> age;
                case "setAge" -> { age = (Integer) args[0]; yield null; }
                case "isAdult" -> age >= 0;
                case "setAdult" -> { age = 0; yield null; }
                case "setBaby" -> { age = -24000; yield null; }
                case "getAgeLock" -> ageLock;
                case "setAgeLock" -> { ageLock = (Boolean) args[0]; yield null; }
                case "getStrength" -> strength;
                case "setStrength" -> { strength = (Integer) args[0]; if (chest) actor.slots = java.util.Arrays.copyOf(actor.slots, 2 + 3 * strength); yield null; }
                case "getColor" -> type == Llama.class ? color : actor.invoke(target, method, args);
                case "setColor" -> { if (type == Llama.class) color = (Llama.Color) args[0]; yield null; }
                case "isCarryingChest" -> chest;
                case "setCarryingChest" -> { chest = (Boolean) args[0]; actor.slots = java.util.Arrays.copyOf(actor.slots, chest ? 2 + 3 * strength : 2); yield null; }
                default -> actor.invoke(target, method, args);
            });
            field(Actor.class, "handle").set(actor, wrapper);
        }
    }
}
