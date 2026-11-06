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
    @Test void passiveHeavenHoovesRefreshKeepsSlowFallingWithoutLaunching() throws Exception {
        try (Fixture f = new Fixture()) {
            TraitRegistry.activateHeavenHooves(f.player.handle, f.horse.handle, null);
            assertNotNull(f.horse.effect(PotionEffectType.SLOW_FALLING));
            assertEquals(0, f.horse.velocity.lengthSquared());
            assertTrue(f.tasks.isEmpty());
        }
    }
}
