package me.luisgamedev.betterhorses.listeners;

import org.bukkit.util.Vector;

/** Temporary water movement; never changes a horse's persistent attributes. */
final class HorseSwimPhysics {
    private HorseSwimPhysics() {
    }

    static Vector velocity(Vector observedMovement, double surfaceError) {
        double x = observedMovement.getX();
        double z = observedMovement.getZ();
        double horizontalSpeed = Math.hypot(x, z);
        if (horizontalSpeed > 0.10) {
            double scale = 0.10 / horizontalSpeed;
            x *= scale;
            z *= scale;
        }

        double desiredY = clamp(surfaceError * 0.35, -0.08, 0.22);
        // Retain an initial dip after a fall, then brake the descent and rise.
        double y = clamp(observedMovement.getY() * 0.55 + desiredY * 0.45, -0.55, 0.22);
        return new Vector(x, y, z);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
