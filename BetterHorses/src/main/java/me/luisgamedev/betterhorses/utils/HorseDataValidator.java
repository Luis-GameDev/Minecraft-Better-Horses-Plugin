package me.luisgamedev.betterhorses.utils;

/** Shared validation for command input and untrusted/legacy item metadata. */
public final class HorseDataValidator {
    public static final int MIN_GROWTH_STAGE = 1;
    public static final int MAX_GROWTH_STAGE = 10;
    private HorseDataValidator() {}

    public static boolean isValid(double health, double currentHealth, double speed, double jump,
                                  String gender, int growth) {
        return finitePositive(health) && Double.isFinite(currentHealth) && currentHealth > 0 && currentHealth <= health
                && finitePositive(speed) && finitePositive(jump)
                && ("male".equalsIgnoreCase(gender) || "female".equalsIgnoreCase(gender))
                && growth >= MIN_GROWTH_STAGE && growth <= MAX_GROWTH_STAGE;
    }

    public static boolean finitePositive(double value) {
        return Double.isFinite(value) && value > 0.0;
    }
}
