package me.luisgamedev.betterhorses.listeners;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HorseSwimPhysicsTest {
    @Test void fallingHorseDipsBeforeRisingQuickly() {
        Vector movement = new Vector(0, -1.0, 0);
        movement = HorseSwimPhysics.velocity(movement, 1.0);
        assertTrue(movement.getY() < 0, "Water entry must retain downward momentum");
        for (int tick = 0; tick < 5; tick++) {
            movement = HorseSwimPhysics.velocity(movement, 1.0);
        }
        assertTrue(movement.getY() > 0, "A submerged horse must soon rise");
        assertTrue(movement.getY() <= 0.22);
    }

    @Test void swimmingSpeedIsBoundedAndPreservesHorizontalDirection() {
        Vector observed = new Vector(3, -20, 4);
        Vector result = HorseSwimPhysics.velocity(observed, 1.0);
        assertEquals(0.10, Math.hypot(result.getX(), result.getZ()), 1.0E-12);
        assertEquals(3.0 / 4.0, result.getX() / result.getZ(), 1.0E-12);
        assertEquals(-0.55, result.getY(), 1.0E-12);
        assertEquals(new Vector(3, -20, 4), observed, "Do not alter the sampled movement");
        assertEquals(0.22, HorseSwimPhysics.velocity(new Vector(0, 20, 0), 1.0).getY(), 1.0E-12);
    }

    @Test void surfaceEquilibriumIsStillAndSlowSteeringIsPreserved() {
        assertEquals(new Vector(), HorseSwimPhysics.velocity(new Vector(), 0));
        Vector slow = new Vector(0.03, 0, -0.04);
        assertEquals(slow, HorseSwimPhysics.velocity(slow, 0));
        assertTrue(HorseSwimPhysics.velocity(new Vector(), -1).getY() < 0,
                "A horse above the desired waterline should settle");
        assertTrue(HorseSwimPhysics.velocity(new Vector(), 1).getY() > 0);
    }
}
