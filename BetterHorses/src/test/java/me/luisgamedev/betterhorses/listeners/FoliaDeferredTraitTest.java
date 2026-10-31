package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.traits.TraitRegistry;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Horse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FoliaDeferredTraitTest {
    @Test void aTargetTeleportOutsideTheCastRadiusCancelsTheDeferredHit() throws Exception {
        try (TraitLifecycleRegressionTest.Fixture f = new TraitLifecycleRegressionTest.Fixture()) {
            var target = f.actor(Horse.class);
            f.targets = List.of(target.handle);
            TraitRegistry.activateSkyburst(f.player.handle, f.horse.handle);
            target.x = 100;
            f.advance(1);
            assertTrue(f.events.isEmpty());
            assertEquals(0, target.velocity.getY());
        }
    }

    @Test void aRiderMovingToAnotherRegionIsNotExposedToSyntheticDamageListeners() throws Exception {
        try (TraitLifecycleRegressionTest.Fixture f = new TraitLifecycleRegressionTest.Fixture()) {
            var target = f.actor(Horse.class);
            f.targets = List.of(target.handle);
            TraitRegistry.activateSkyburst(f.player.handle, f.horse.handle);
            f.foreign.add(f.player.id);
            f.advance(1);
            assertTrue(f.events.isEmpty());
            assertEquals(0, target.velocity.getY());
        }
    }

    @Test void anOwnedValidDeferredHitStillDispatchesAndLaunchesTheTarget() throws Exception {
        try (TraitLifecycleRegressionTest.Fixture f = new TraitLifecycleRegressionTest.Fixture()) {
            var target = f.actor(Horse.class);
            f.targets = List.of(target.handle);
            TraitRegistry.activateSkyburst(f.player.handle, f.horse.handle);
            f.advance(1);
            assertEquals(1, f.events.size());
            assertEquals(1, target.velocity.getY());
        }
    }

    @Test void anUndeadReplacementCannotRemountAForeignPassenger() throws Exception {
        try (TraitLifecycleRegressionTest.Fixture f = new TraitLifecycleRegressionTest.Fixture()) {
            f.player.vehicle = null;
            TraitLifecycleRegressionTest.invoke(new UndeadTraitListener(), "remountNextTick",
                    new Class<?>[]{AbstractHorse.class, List.class}, f.horse.handle, List.of(f.player.handle));
            f.foreign.add(f.player.id);
            f.advance(1);
            assertNull(f.player.vehicle);
        }
    }
}
