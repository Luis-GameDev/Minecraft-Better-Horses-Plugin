package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.events.BetterHorseAbilityUseEvent;
import me.luisgamedev.betterhorses.traits.OwnedPotionEffects;
import me.luisgamedev.betterhorses.traits.TraitParticleResolver;
import me.luisgamedev.betterhorses.traits.TraitRegistry;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.entity.Horse;
import org.bukkit.event.entity.HorseJumpEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static me.luisgamedev.betterhorses.listeners.TraitLifecycleRegressionTest.*;
import static org.junit.jupiter.api.Assertions.*;

class TraitSafetyRegressionTest {
    @Test void passivePotionCallbackCannotResumeReadingATransferredPlayer() throws Exception {
        try (Fixture f = new Fixture()) {
            f.horse.values.put(BetterHorseKeys.TRAIT, "fireheart");
            java.util.concurrent.atomic.AtomicInteger foreignVehicleReads = new java.util.concurrent.atomic.AtomicInteger();
            org.bukkit.entity.Player player = proxy(org.bukkit.entity.Player.class, (target, method, args) -> {
                if (method.getName().equals("getVehicle") && f.foreign.contains(f.player.id))
                    foreignVehicleReads.incrementAndGet();
                Object result = f.player.invoke(target, method, args);
                if (method.getName().equals("addPotionEffect")) f.foreign.add(f.player.id);
                return result;
            });
            field(Actor.class, "handle").set(f.player, player);
            PassiveTraitListener listener = new PassiveTraitListener();
            var from = player.getLocation();
            listener.onPlayerMove(new org.bukkit.event.player.PlayerMoveEvent(player, from, from.clone().add(1, 0, 0)));
            assertTrue(f.foreign.contains(f.player.id));
            assertEquals(0, foreignVehicleReads.get());
            // EntityScheduler delivers cleanup on the new owning region.
            f.foreign.remove(f.player.id);
            f.advance(1);
            assertNull(f.player.effect(PotionEffectType.FIRE_RESISTANCE));
            listener.shutdown();
        }
    }

    @Test void oldSameHorseExpiryCannotRemoveAReactivatedGhostSession() throws Exception {
        try (Fixture f = new Fixture()) {
            f.config.set("traits.ghosthorse.cooldown", 0);
            TraitRegistry.activateGhostHorse(f.player.handle, f.horse.handle);
            var oldExpiry = f.tasks.get(0);
            f.advance(50);
            TraitRegistry.activateGhostHorse(f.player.handle, f.horse.handle);
            // Even an already-delivered old callback must not clear the replacement.
            oldExpiry.action.run();
            f.advance(50);
            assertNotNull(f.player.effect(PotionEffectType.INVISIBILITY));
            assertNotNull(f.horse.effect(PotionEffectType.INVISIBILITY));
            f.advance(50);
            assertNull(f.player.effect(PotionEffectType.INVISIBILITY));
            assertNull(f.horse.effect(PotionEffectType.INVISIBILITY));
        }
    }

    @Test void oldHorseExpiryCannotClearNewHorseRiderInvisibilityWithDefaults() throws Exception {
        try (Fixture f = new Fixture()) {
            TraitRegistry.activateGhostHorse(f.player.handle, f.horse.handle);
            f.advance(50);
            var second = f.actor(Horse.class);
            f.player.vehicle = second.handle;
            TraitRegistry.activateGhostHorse(f.player.handle, second.handle);
            f.advance(50);
            assertNotNull(f.player.effect(PotionEffectType.INVISIBILITY));
            assertNotNull(second.effect(PotionEffectType.INVISIBILITY));
            f.advance(50);
            assertNull(f.player.effect(PotionEffectType.INVISIBILITY));
            assertNull(second.effect(PotionEffectType.INVISIBILITY));
        }
    }

    @Test void ghostExpiryPreservesAnExternalReplacementAndClearsSessionTracking() throws Exception {
        try (Fixture f = new Fixture()) {
            TraitRegistry.activateGhostHorse(f.player.handle, f.horse.handle);
            f.player.put(new PotionEffect(PotionEffectType.INVISIBILITY, 500, 1, false, true, true));
            f.advance(100);
            assertNotNull(f.player.effect(PotionEffectType.INVISIBILITY));
            assertEquals(1, f.player.effect(PotionEffectType.INVISIBILITY).getAmplifier());
            assertNull(f.horse.effect(PotionEffectType.INVISIBILITY));
            for (String name : List.of("ghostSessions", "ghostPlayerSessions", "ghostPlayers", "ghostHorses", "ghostHorseTasks"))
                assertTrue(((Map<?, ?>) field(TraitRegistry.class, name).get(null)).isEmpty(), name);
        }
    }

    @Test void cancelledHeavenHoovesAndSkyburstJumpsDoNoWork() throws Exception {
        try (Fixture f = new Fixture()) {
            HorseJumpListener listener = new HorseJumpListener();
            for (String trait : List.of("heavenhooves", "skyburst")) {
                f.horse.values.put(BetterHorseKeys.TRAIT, trait);
                HorseJumpEvent jump = new HorseJumpEvent(f.horse.handle, 1);
                jump.setCancelled(true);
                listener.onHorseJump(jump);
                assertEquals(0, f.horse.velocity.lengthSquared());
                assertNull(f.horse.effect(PotionEffectType.SLOW_FALLING));
                assertTrue(f.tasks.isEmpty());
            }
            listener.shutdown();
        }
    }

    @Test void anAcceptedHeavenHoovesJumpStillAppliesItsBoost() throws Exception {
        try (Fixture f = new Fixture()) {
            f.horse.values.put(BetterHorseKeys.TRAIT, "heavenhooves");
            HorseJumpEvent jump = new HorseJumpEvent(f.horse.handle, 1);
            new HorseJumpListener().onHorseJump(jump);
            assertTrue(jump.isCancelled());
            assertTrue(f.horse.velocity.getY() > 0);
            assertNotNull(f.horse.effect(PotionEffectType.SLOW_FALLING));
        }
    }

    @Test void kickbackSkipsCoincidentTargetsAndStillPushesOtherTargets() throws Exception {
        try (Fixture f = new Fixture()) {
            f.config.set("traits.kickback.enabled", true);
            f.config.set("traits.kickback.radius", 4);
            f.config.set("traits.kickback.strength", 6);
            var coincident = f.actor(Horse.class);
            var distant = f.actor(Horse.class);
            distant.x = 2;
            f.targets = List.of(coincident.handle, distant.handle);
            TraitRegistry.activateKickback(f.player.handle, f.horse.handle);
            f.advance(1);
            assertDoesNotThrow(() -> coincident.velocity.checkFinite());
            assertEquals(0, coincident.velocity.lengthSquared());
            assertDoesNotThrow(() -> distant.velocity.checkFinite());
            assertEquals(6, distant.velocity.getX(), 1.0E-10);
        }
    }

    @Test void dataBearingAndUnknownParticlesUseTheSafeFallback() throws Exception {
        try (Fixture f = new Fixture()) {
            for (String name : List.of("DUST", "REDSTONE", "BLOCK", "ITEM", "NOT_A_PARTICLE")) {
                f.config.set("traits.particle_safety.particle", name);
                Particle result = TraitParticleResolver.getTraitParticle("particle_safety", Particle.FLAME);
                assertSame(Particle.FLAME, result, name);
                assertEquals(Void.class, result.getDataType());
            }
            f.config.set("traits.particle_safety.particle", "SWEEP_ATTACK");
            assertSame(Particle.SWEEP_ATTACK, TraitParticleResolver.getTraitParticle("particle_safety", Particle.FLAME));
            f.config.set("traits.particle_safety.particle", "");
            assertSame(Particle.CLOUD, TraitParticleResolver.getTraitParticle("particle_safety", Particle.DUST));
        }
    }

    @Test void ordinaryAbilityEventStillActivatesDash() throws Exception {
        try (Fixture f = new Fixture()) {
            f.horse.values.put(BetterHorseKeys.TRAIT, "dashboost");
            PlayerSwapHandItemsEvent event = new PlayerSwapHandItemsEvent(f.player.handle, null, null);
            new TraitActivationListener().onTraitKeyPressed(event);
            assertTrue(event.isCancelled());
            assertEquals(.3, f.horse.attributes.get(AttributeResolver.generic("MOVEMENT_SPEED")).getValue(), 1.0E-10);
        }
    }
    @Test void abilityOwnershipTransferStopsBeforeAnyForeignNativeAccess() throws Exception {
        try (Fixture f = new Fixture()) {
            Horse checked = proxy(Horse.class, (target, method, args) -> {
                if (f.foreign.contains(f.horse.id) && method.getName().equals("getAttribute"))
                    fail("Read foreign horse attribute after event callback");
                return method.invoke(f.horse.handle, args);
            });
            f.player.vehicle = checked;
            f.horse.values.put(BetterHorseKeys.TRAIT, "dashboost");
            Server previous = Bukkit.getServer();
            PluginManager callbacks = proxy(PluginManager.class, (target, method, args) -> {
                if (method.getName().equals("callEvent") && args[0] instanceof BetterHorseAbilityUseEvent)
                    f.foreign.add(f.horse.id);
                return method.invoke(previous.getPluginManager(), args);
            });
            f.serverField.set(null, proxy(Server.class, (target, method, args) ->
                    method.getName().equals("getPluginManager") ? callbacks : method.invoke(previous, args)));
            PlayerSwapHandItemsEvent event = new PlayerSwapHandItemsEvent(f.player.handle, null, null);
            assertDoesNotThrow(() -> new TraitActivationListener().onTraitKeyPressed(event));
            assertTrue(event.isCancelled());
            assertEquals(.2, f.horse.attributes.get(AttributeResolver.generic("MOVEMENT_SPEED")).getValue(), 1.0E-10);
            assertTrue(f.tasks.isEmpty());
        }
    }

    @Test void nativePotionCallbackDoesNotCauseAForeignExpiryRead() throws Exception {
        try (Fixture f = new Fixture()) {
            Horse checked = proxy(Horse.class, (target, method, args) -> {
                if (f.foreign.contains(f.horse.id) && method.getName().equals("getTicksLived"))
                    fail("Read native expiry after potion callback transferred ownership");
                Object result = method.invoke(f.horse.handle, args);
                if (method.getName().equals("addPotionEffect")) f.foreign.add(f.horse.id);
                return result;
            });
            assertDoesNotThrow(() -> OwnedPotionEffects.apply(checked, PotionEffectType.INVISIBILITY, 100));
            assertNotNull(f.horse.effect(PotionEffectType.INVISIBILITY));
        }
    }

    @Test void languageCallbackOwnershipTransferPreventsHellmareContinuation() throws Exception {
        try (Fixture f = new Fixture()) {
            f.config.set("traits.hellmare.enabled", true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null);
            CallbackLanguage language = (CallbackLanguage) unsafe.allocateInstance(CallbackLanguage.class);
            language.action = () -> f.foreign.add(f.horse.id);
            f.plugin.language = language;
            assertDoesNotThrow(() -> TraitRegistry.activateHellmare(f.player.handle, f.horse.handle));
            assertTrue(f.horse.potions.isEmpty());
            assertTrue(f.player.potions.isEmpty());
            assertTrue(f.tasks.isEmpty());
        }
    }

    public static final class CallbackLanguage extends me.luisgamedev.betterhorses.language.LanguageManager {
        Runnable action;
        public CallbackLanguage() { super(null, null); }
        @Override public void send(org.bukkit.entity.Player player, String key) { action.run(); }
    }
    @Test void passiveHeavenHoovesRefreshKeepsSlowFallingWithoutLaunching() throws Exception {
        try (Fixture f = new Fixture()) {
            TraitRegistry.activateHeavenHooves(f.player.handle, f.horse.handle, null);
            assertNotNull(f.horse.effect(PotionEffectType.SLOW_FALLING));
            assertEquals(0, f.horse.velocity.lengthSquared());
            assertTrue(f.tasks.isEmpty());
        }
    }
    @Test void jumpPotionOwnershipTransferStopsTheListenersRemainingReads() throws Exception {
        try (Fixture f = new Fixture()) {
            f.horse.values.put(BetterHorseKeys.TRAIT, "heavenhooves");
            Horse checked = proxy(Horse.class, (target, method, args) -> {
                if (f.foreign.contains(f.horse.id) && method.getName().equals("getPersistentDataContainer"))
                    fail("Read trait metadata after a jump potion callback transferred ownership");
                if (method.getName().equals("getPassengers")) return List.of(f.player.handle);
                Object result = method.invoke(f.horse.handle, args);
                if (method.getName().equals("addPotionEffect")) f.foreign.add(f.horse.id);
                return result;
            });
            f.player.vehicle = checked;
            assertDoesNotThrow(() -> new HorseJumpListener().onHorseJump(new HorseJumpEvent(checked, 1)));
            assertEquals(0, f.horse.velocity.lengthSquared());
            assertTrue(f.tasks.isEmpty());
        }
    }
}
