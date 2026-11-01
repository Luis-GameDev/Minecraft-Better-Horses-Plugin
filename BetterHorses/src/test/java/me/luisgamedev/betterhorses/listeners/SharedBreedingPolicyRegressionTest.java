package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.events.BetterHorseBreedEvent;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class SharedBreedingPolicyRegressionTest {
    @Test void sameGenderCrossbreedIsRejectedBeforeTheApiEventOrParentCommit() throws Exception {
        try (var f = fixture()) {
            var father = f.mount("male");
            var mother = f.mount("male");
            var child = f.mount(null);
            EntityBreedEvent birth = crossbreed(f, father, mother, child);
            new HorseBreedListener().onHorseBreed(birth);
            assertTrue(birth.isCancelled());
            assertTrue(f.events.isEmpty());
            assertFalse(father.values.containsKey(BetterHorseKeys.COOLDOWN));
            assertFalse(mother.values.containsKey(BetterHorseKeys.COOLDOWN));
        }
    }

    @Test void crossbreedPreservesNativeInheritanceAndDispatchesApiBeforeCooldownCommit() throws Exception {
        try (var f = fixture()) {
            var father = f.mount("male");
            var mother = f.mount("female");
            var child = f.mount(null);
            child.handle.getAttribute(AttributeResolver.generic("MAX_HEALTH")).setBaseValue(33.0);
            child.handle.getAttribute(AttributeResolver.generic("MOVEMENT_SPEED")).setBaseValue(.17);
            child.handle.getAttribute(AttributeResolver.horseJumpStrength()).setBaseValue(.55);
            // Copied plugin metadata must not replace vanilla's already calculated child.
            child.values.put(BetterHorseKeys.BASE_HEALTH, 84.0);
            child.values.put(BetterHorseKeys.HEALTH, 84.0);
            f.config.set("mutation-factor.health", 100.0);
            f.config.set("max-stats.health", 1.0);
            f.eventAction = event -> {
                if (event instanceof BetterHorseBreedEvent breed) {
                    assertEquals(33.0, breed.getHealth());
                    assertEquals(.17, breed.getSpeed());
                    assertEquals(.55, breed.getJump());
                    assertFalse(father.values.containsKey(BetterHorseKeys.COOLDOWN));
                }
            };
            EntityBreedEvent birth = crossbreed(f, father, mother, child);
            new HorseBreedListener().onHorseBreed(birth);
            assertFalse(birth.isCancelled());
            assertEquals(1, f.events.stream().filter(BetterHorseBreedEvent.class::isInstance).count());
            assertEquals(33.0, child.values.get(BetterHorseKeys.HEALTH));
            assertEquals(.17, child.values.get(BetterHorseKeys.SPEED));
            assertEquals(.55, child.values.get(BetterHorseKeys.JUMP));
            assertEquals("MULE", child.values.get(BetterHorseKeys.MOUNT_TYPE));
            assertTrue(father.values.containsKey(BetterHorseKeys.COOLDOWN));
            assertTrue(mother.values.containsKey(BetterHorseKeys.COOLDOWN));
            father.handle.setAge(6000); // Vanilla applies this after the breed event.
            PlayerInteractEntityEvent nextFeed = new PlayerInteractEntityEvent(player(f.player, false), father.handle, EquipmentSlot.HAND);
            new HorseFeedListener().onHorseFeed(nextFeed);
            assertTrue(nextFeed.isCancelled());
            assertEquals(6000, father.handle.getAge());
        }
    }

    @Test void cancelledOrInvalidCrossbreedApiResultDoesNotCommitParents() throws Exception {
        try (var f = fixture()) {
            var father = f.mount("male");
            var mother = f.mount("female");
            for (boolean cancel : new boolean[]{true, false}) {
                f.eventAction = event -> {
                    if (event instanceof BetterHorseBreedEvent breed) {
                        if (cancel) breed.setCancelled(true);
                        else breed.setJump(Double.NaN);
                    }
                };
                var birth = crossbreed(f, father, mother, f.mount(null));
                new HorseBreedListener().onHorseBreed(birth);
                assertTrue(birth.isCancelled());
                assertFalse(father.values.containsKey(BetterHorseKeys.COOLDOWN));
                assertFalse(mother.values.containsKey(BetterHorseKeys.COOLDOWN));
            }
        }
    }

    @Test void crossbreedChecksNeuteringAndExistingParentCooldowns() throws Exception {
        try (var f = fixture()) {
            var father = f.mount("male");
            var mother = f.mount("female");
            father.values.put(BetterHorseKeys.NEUTERED, (byte) 1);
            var birth = crossbreed(f, father, mother, f.mount(null));
            new HorseBreedListener().onHorseBreed(birth);
            assertTrue(birth.isCancelled());
            father.values.remove(BetterHorseKeys.NEUTERED);
            mother.values.put(BetterHorseKeys.COOLDOWN, System.currentTimeMillis());
            birth = crossbreed(f, father, mother, f.mount(null));
            new HorseBreedListener().onHorseBreed(birth);
            assertTrue(birth.isCancelled());
            assertTrue(f.events.isEmpty());
        }
    }

    @Test void crossbreedRequiresBreederPermissionAndRechecksNeuteringAfterTheApiCallback() throws Exception {
        try (var f = fixture()) {
            var father = f.mount("male");
            var mother = f.mount("female");
            var child = f.mount(null);
            Player denied = proxy(Player.class, (target, method, args) ->
                    method.getName().equals("hasPermission") && PermissionUtils.BREED.equals(args[0])
                            ? false : invoke(f.player, method, args));
            var birth = new EntityBreedEvent(species(child.handle, Mule.class, EntityType.MULE, false), father.handle,
                    species(mother.handle, Donkey.class, EntityType.DONKEY, false), denied, null, 0);
            new HorseBreedListener().onHorseBreed(birth);
            assertTrue(birth.isCancelled());
            assertTrue(f.events.isEmpty());
            f.eventAction = event -> {
                if (event instanceof BetterHorseBreedEvent) mother.values.put(BetterHorseKeys.NEUTERED, (byte) 1);
            };
            birth = crossbreed(f, father, mother, child);
            new HorseBreedListener().onHorseBreed(birth);
            assertTrue(birth.isCancelled());
            assertFalse(father.values.containsKey(BetterHorseKeys.COOLDOWN));
            assertFalse(mother.values.containsKey(BetterHorseKeys.COOLDOWN));
        }
    }

    @Test void crossbreedRejectsCallbackOwnershipTransferBeforeCommittingStatsOrParents() throws Exception {
        try (var f = fixture()) {
            var father = f.mount("male");
            var mother = f.mount("female");
            var child = f.mount(null);
            java.util.concurrent.atomic.AtomicBoolean transferred = new java.util.concurrent.atomic.AtomicBoolean();
            org.bukkit.Server original = org.bukkit.Bukkit.getServer();
            org.bukkit.Server checked = proxy(org.bukkit.Server.class, (target, method, args) ->
                    method.getName().equals("isOwnedByCurrentRegion") ? !transferred.get() : invoke(original, method, args));
            f.serverField.set(null, checked);
            f.eventAction = event -> {
                if (event instanceof BetterHorseBreedEvent) transferred.set(true);
            };
            var birth = crossbreed(f, father, mother, child);
            new HorseBreedListener().onHorseBreed(birth);
            assertTrue(birth.isCancelled());
            assertFalse(father.values.containsKey(BetterHorseKeys.COOLDOWN));
            assertFalse(mother.values.containsKey(BetterHorseKeys.COOLDOWN));
            assertFalse(child.values.containsKey(BetterHorseKeys.HEALTH));
        }
    }

    @Test void apiCanEditCrossbreedStatsAndAChildNeedNotBeInsertedYet() throws Exception {
        try (var f = fixture()) {
            var father = f.mount("male");
            var mother = f.mount("female");
            var child = f.mount(null);
            AbstractHorse notInserted = species(child.handle, Mule.class, EntityType.MULE, true);
            f.eventAction = event -> {
                if (event instanceof BetterHorseBreedEvent breed) breed.setHealth(41.0);
            };
            var birth = new EntityBreedEvent(notInserted, father.handle,
                    species(mother.handle, Donkey.class, EntityType.DONKEY, false), null, null, 0);
            new HorseBreedListener().onHorseBreed(birth);
            assertFalse(birth.isCancelled());
            assertEquals(41.0, child.values.get(BetterHorseKeys.HEALTH));
        }
    }

    @Test void zeroCooldownAndPermissionBypassClearTheNativeWaitButDoNotMatureBabies() throws Exception {
        try (var f = fixture()) {
            var listener = new HorseFeedListener();
            f.config.set("settings.breeding-cooldown", 300L);
            f.horse.age = 6000;
            listener.onHorseFeed(new PlayerInteractEntityEvent(player(f.player, true), f.horse.handle, EquipmentSlot.HAND));
            assertEquals(0, f.horse.age);
            f.config.set("settings.breeding-cooldown", 0L);
            f.horse.age = 6000;
            listener.onHorseFeed(new PlayerInteractEntityEvent(player(f.player, false), f.horse.handle, EquipmentSlot.HAND));
            assertEquals(0, f.horse.age);
            f.horse.age = -12000;
            listener.onHorseFeed(new PlayerInteractEntityEvent(player(f.player, true), f.horse.handle, EquipmentSlot.HAND));
            assertEquals(-12000, f.horse.age);
        }
    }

    @Test void ridingObservesFinalMovementAfterCancellationAndUsesTheAcceptedDestination() throws Exception {
        EventHandler registration = HorseTrainingRidingListener.class.getMethod("onMove", PlayerMoveEvent.class).getAnnotation(EventHandler.class);
        assertEquals(EventPriority.MONITOR, registration.priority());
        assertTrue(registration.ignoreCancelled());
        try (var f = fixture()) {
            var horse = f.horse.handle;
            Player rider = rider(f.player, horse);
            f.config.set("training.categories.riding.enabled", true);
            var listener = new HorseTrainingRidingListener();
            Location start = new Location(f.world, 0, 0, 0);
            var move = new PlayerMoveEvent(rider, start, new Location(f.world, 1, 0, 0));
            // HIGH/HIGHEST observers run before the registered MONITOR listener.
            move.setCancelled(true);
            listener.onMove(move);
            assertFalse(f.horse.values.containsKey(BetterHorseKeys.TRAINING_RIDING_UNITS));
            move = new PlayerMoveEvent(rider, start, new Location(f.world, 1, 0, 0));
            move.setTo(new Location(f.world, .25, 0, 0));
            listener.onMove(move);
            assertEquals(.25, f.horse.values.get(BetterHorseKeys.TRAINING_RIDING_UNITS));
            listener.onMove(new PlayerTeleportEvent(rider, start, new Location(f.world, 100, 0, 0)));
            listener.onMove(new PlayerMoveEvent(rider, start, new Location(f.world, Double.NaN, 0, 0)));
            assertEquals(.25, f.horse.values.get(BetterHorseKeys.TRAINING_RIDING_UNITS));
        }
    }

    private static HorseFeedBreedRegressionTest.Fixture fixture() throws Exception {
        var f = new HorseFeedBreedRegressionTest.Fixture();
        f.config.set("settings.mount-types.donkeys", true);
        f.config.set("settings.mount-types.mules", true);
        f.config.set("settings.allow-same-gender-breeding", false);
        f.config.set("settings.breeding-cooldown", 300L);
        f.config.set("settings.male-ignore-cooldown", false);
        return f;
    }

    private static EntityBreedEvent crossbreed(HorseFeedBreedRegressionTest.Fixture f,
            HorseFeedBreedRegressionTest.Mount father, HorseFeedBreedRegressionTest.Mount mother,
            HorseFeedBreedRegressionTest.Mount child) {
        return new EntityBreedEvent(species(child.handle, Mule.class, EntityType.MULE, false), father.handle,
                species(mother.handle, Donkey.class, EntityType.DONKEY, false), null, null, 0);
    }

    private static Player player(Player original, boolean bypass) {
        return proxy(Player.class, (target, method, args) -> {
            if (method.getName().equals("hasPermission") && PermissionUtils.COOLDOWN_BYPASS.equals(args[0])) return bypass;
            return invoke(original, method, args);
        });
    }

    private static Player rider(Player original, AbstractHorse horse) {
        return proxy(Player.class, (target, method, args) -> method.getName().equals("getVehicle") ? horse : invoke(original, method, args));
    }

    private static <T extends AbstractHorse> T species(Horse original, Class<T> type, EntityType entityType, boolean notInserted) {
        return proxy(type, (target, method, args) -> switch (method.getName()) {
            case "getType" -> entityType;
            case "isValid" -> !notInserted;
            default -> invoke(original, original.getClass().getMethod(method.getName(), method.getParameterTypes()), args);
        });
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
