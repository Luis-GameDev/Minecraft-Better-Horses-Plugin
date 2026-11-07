package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.events.HorseTrampleEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static me.luisgamedev.betterhorses.listeners.TraitLifecycleRegressionTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtectionActionRegressionTest {
    @Test void cancelledNativeDamageCannotPushOrConsumeTheCooldown() throws Exception {
        try (Hit hit = new Hit()) {
            hit.nativeCancelled = true;
            hit.apply();
            assertEquals(1, hit.nativeEvents);
            assertEquals(20, hit.victim.health);
            assertEquals(0, hit.victim.velocity.lengthSquared());
            assertTrue(hit.cooldowns().isEmpty());
            hit.nativeCancelled = false;
            hit.apply();
            assertEquals(2, hit.nativeEvents); // The veto did not consume the next permitted hit.
            assertEquals(16, hit.victim.health);
            assertTrue(hit.victim.velocity.lengthSquared() > 0);
            assertEquals(1, hit.cooldowns().size());
        }
    }

    @Test void aLaterNativeMonitorCancellationIsStillObservedAfterDamageReturns() throws Exception {
        try (Hit hit = new Hit()) {
            hit.lateCancelled = true;
            hit.apply();
            assertEquals(1, hit.nativeEvents);
            assertEquals(0, hit.victim.velocity.lengthSquared());
            assertTrue(hit.cooldowns().isEmpty());
        }
    }

    @Test void acceptedNativeDamageAppliesExactlyOneHitAndItsCooldown() throws Exception {
        try (Hit hit = new Hit()) {
            hit.apply();
            hit.apply();
            assertEquals(1, hit.nativeEvents);
            assertEquals(16, hit.victim.health);
            assertEquals(1.2, hit.victim.velocity.getX(), 1e-10);
            assertEquals(1, hit.cooldowns().size());
        }
    }

    @Test void eventModifiedZeroDamageStillAsksProtectionBeforeKnockback() throws Exception {
        try (Hit hit = new Hit()) {
            hit.customDamage = 0;
            hit.apply();
            assertEquals(1, hit.nativeEvents);
            assertEquals(0, hit.nativeAmount);
            assertEquals(20, hit.victim.health);
            assertTrue(hit.victim.velocity.lengthSquared() > 0);
        }
        try (Hit hit = new Hit()) {
            hit.customDamage = 0;
            hit.nativeCancelled = true;
            hit.apply();
            assertEquals(1, hit.nativeEvents);
            assertEquals(0, hit.victim.velocity.lengthSquared());
            assertTrue(hit.cooldowns().isEmpty());
        }
    }

    @Test void customTrampleVetoDoesNotDispatchNativeDamage() throws Exception {
        try (Hit hit = new Hit()) {
            hit.customCancelled = true;
            hit.apply();
            assertEquals(0, hit.nativeEvents);
            assertEquals(0, hit.victim.velocity.lengthSquared());
            assertTrue(hit.cooldowns().isEmpty());
        }
    }

    @Test void missingNativeEventFailsClosedWithoutLeavingAHitContext() throws Exception {
        try (Hit hit = new Hit()) {
            hit.emitNativeEvent = false;
            hit.apply();
            assertEquals(0, hit.victim.velocity.lengthSquared());
            assertTrue(hit.cooldowns().isEmpty());
            hit.emitNativeEvent = true;
            hit.apply();
            assertEquals(1, hit.nativeEvents);
            assertTrue(hit.victim.velocity.lengthSquared() > 0);
        }
    }

    @Test void brushWaitsUntilAfterNormalProtectionPriorities() throws Exception {
        EventHandler handler = HorseTrainingBrushingListener.class
                .getMethod("onBrush", PlayerInteractEntityEvent.class).getAnnotation(EventHandler.class);
        assertEquals(EventPriority.MONITOR, handler.priority());
        assertTrue(handler.ignoreCancelled());
        try (Fixture f = brushFixture()) {
            PlayerInteractEntityEvent event = brush(f);
            assertFalse(event.isCancelled()); // HIGH/HIGHEST protections see the untouched gesture.
            event.setCancelled(true);
            new HorseTrainingBrushingListener().onBrush(event);
            f.advance(1);
            assertFalse(f.horse.values.containsKey(BetterHorseKeys.TRAINING_BRUSHING_UNITS));
            assertFalse(f.horse.values.containsKey(BetterHorseKeys.TRAINING_BRUSH_COOLDOWN));
        }
    }

    @Test void acceptedBrushSuppressesNativeUseAndAwardsOnceOnTheNextTick() throws Exception {
        try (Fixture f = brushFixture()) {
            PlayerInteractEntityEvent event = brush(f);
            new HorseTrainingBrushingListener().onBrush(event);
            assertTrue(event.isCancelled());
            assertFalse(f.horse.values.containsKey(BetterHorseKeys.TRAINING_BRUSHING_UNITS));
            f.advance(1);
            assertEquals(2d, f.horse.values.get(BetterHorseKeys.TRAINING_BRUSHING_UNITS));
            assertTrue(f.horse.values.containsKey(BetterHorseKeys.TRAINING_BRUSH_COOLDOWN));
            new HorseTrainingBrushingListener().onBrush(brush(f));
            f.advance(1);
            assertEquals(2d, f.horse.values.get(BetterHorseKeys.TRAINING_BRUSHING_UNITS));
        }
    }

    @Test void deferredBrushCannotTrainAfterAnItemOrFeatureChange() throws Exception {
        try (Fixture f = brushFixture()) {
            new HorseTrainingBrushingListener().onBrush(brush(f));
            f.held = new ItemStack(Material.DIAMOND);
            f.advance(1);
            assertFalse(f.horse.values.containsKey(BetterHorseKeys.TRAINING_BRUSHING_UNITS));
        }
        try (Fixture f = brushFixture()) {
            new HorseTrainingBrushingListener().onBrush(brush(f));
            f.config.set("training.categories.brushing.enabled", false);
            f.advance(1);
            assertFalse(f.horse.values.containsKey(BetterHorseKeys.TRAINING_BRUSHING_UNITS));
        }
    }

    @Test void inactiveOrExpiredTrampleTrackingIsEvicted() throws Exception {
        try (Fixture f = new Fixture()) {
            f.config.set("trample.enabled", true);
            TrampleListener listener = new TrampleListener(f.plugin);
            try {
                try {
                    TrampleListener.class.getDeclaredField("previousHorseLocations");
                    @SuppressWarnings("unchecked") Map<UUID, Location> positions =
                            (Map<UUID, Location>) field(TrampleListener.class, "previousHorseLocations").get(listener);
                    positions.put(UUID.randomUUID(), f.horse.handle.getLocation());
                    f.advance(1); // No online riders remain in the fixture's world.
                    assertTrue(positions.isEmpty());
                } catch (NoSuchFieldException folia) {
                    @SuppressWarnings("unchecked") Map<UUID, Long> cooldowns =
                            (Map<UUID, Long>) field(TrampleListener.class, "targetCooldowns").get(listener);
                    UUID expired = UUID.randomUUID(), current = UUID.randomUUID();
                    cooldowns.put(expired, 1L);
                    cooldowns.put(current, Long.MAX_VALUE);
                    listener.getClass().getMethod("onMount", EntityMountEvent.class)
                            .invoke(listener, new EntityMountEvent(f.player.handle, f.horse.handle));
                    f.advance(1);
                    assertFalse(cooldowns.containsKey(expired));
                    assertTrue(cooldowns.containsKey(current));
                }
            } finally { listener.shutdown(); }
        }
    }

    private static Fixture brushFixture() throws Exception {
        Fixture f = new Fixture();
        f.config.set("training.enabled", true);
        f.config.set("training.categories.brushing.enabled", true);
        f.config.set("training.categories.brushing.units-per-use", 2.0);
        f.held = new ItemStack(Material.BRUSH);
        return f;
    }

    private static PlayerInteractEntityEvent brush(Fixture f) {
        return new PlayerInteractEntityEvent(f.player.handle, f.horse.handle, EquipmentSlot.HAND);
    }

    private static void hook(Fixture f, Consumer<Event> action) throws Exception {
        Server original = Bukkit.getServer();
        PluginManager manager = proxy(PluginManager.class, (target, method, args) -> {
            if (method.getName().equals("callEvent")) action.accept((Event) args[0]);
            return zero(method.getReturnType());
        });
        field(Bukkit.class, "server").set(null, proxy(Server.class, (target, method, args) ->
                method.getName().equals("getPluginManager") ? manager : method.invoke(original, args)));
    }

    private static final class Hit implements AutoCloseable {
        final Fixture f = new Fixture();
        final Actor<Zombie> victim = f.actor(Zombie.class);
        final Horse horse;
        final Zombie target;
        final TrampleListener listener;
        boolean nativeCancelled, lateCancelled, customCancelled;
        boolean emitNativeEvent = true;
        double customDamage = -1, nativeAmount;
        int nativeEvents;

        Hit() throws Exception {
            f.config.set("trample.enabled", true);
            f.config.set("trample.damage", 4d);
            f.config.set("trample.knockback", 1.2d);
            f.config.set("trample.radius", 1.2d);
            f.config.set("trample.hit-angle-degrees", 360d);
            victim.x = 1;
            horse = proxy(Horse.class, (p, m, a) -> m.getName().equals("getBoundingBox")
                    ? new BoundingBox(-.5, 0, -.5, .5, 1.5, .5) : f.horse.invoke(p, m, a));
            f.player.vehicle = horse;
            target = proxy(Zombie.class, (p, m, a) -> {
                if (m.getName().equals("getBoundingBox")) return new BoundingBox(.7, 0, -.3, 1.3, 1, .3);
                if (m.getName().equals("damage")) {
                    if (!emitNativeEvent) return null;
                    nativeAmount = (Double) a[0];
                    EntityDamageByEntityEvent event = new EntityDamageByEntityEvent((Entity) a[1], (Entity) p,
                            EntityDamageEvent.DamageCause.ENTITY_ATTACK, nativeAmount);
                    Bukkit.getPluginManager().callEvent(event);
                    if (!event.isCancelled()) victim.health -= event.getFinalDamage();
                    return null;
                }
                return victim.invoke(p, m, a);
            });
            listener = new TrampleListener(f.plugin);
            hook(f, event -> {
                if (event instanceof HorseTrampleEvent trample) {
                    trample.setCancelled(customCancelled);
                    if (customDamage >= 0) trample.setDamage(customDamage);
                }
                if (event instanceof EntityDamageByEntityEvent nativeHit) {
                    nativeEvents++;
                    nativeHit.setCancelled(nativeCancelled);
                    listener.onNativeDamage(nativeHit);
                    if (lateCancelled) nativeHit.setCancelled(true);
                }
            });
        }

        void apply() throws Exception {
            try {
                Method method = TrampleListener.class.getDeclaredMethod("applyTrample", Player.class,
                        AbstractHorse.class, Location.class, LivingEntity.class, FileConfiguration.class, long.class);
                method.setAccessible(true);
                method.invoke(listener, f.player.handle, horse, horse.getLocation(), target, f.config, 100L);
            } catch (NoSuchMethodException folia) {
                Method method = TrampleListener.class.getDeclaredMethod("apply", Player.class, AbstractHorse.class,
                        LivingEntity.class, Location.class, Vector.class, double.class, double.class, double.class, int.class);
                method.setAccessible(true);
                method.invoke(listener, f.player.handle, horse, target, horse.getLocation(), new Vector(1, 0, 0),
                        -1d, 4d, 1.2d, 20);
            }
        }

        Map<?, ?> cooldowns() throws Exception {
            return (Map<?, ?>) field(TrampleListener.class, "targetCooldowns").get(listener);
        }

        @Override public void close() throws Exception { listener.shutdown(); f.close(); }
    }
}
