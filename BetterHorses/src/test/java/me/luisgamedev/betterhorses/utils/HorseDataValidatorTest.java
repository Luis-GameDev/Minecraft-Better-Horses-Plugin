package me.luisgamedev.betterhorses.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HorseDataValidatorTest {
    @Test void acceptsValidData() {
        assertTrue(HorseDataValidator.isValid(20, 10, .2, .7, "female", 5));
    }
    @Test void rejectsNonFiniteAndOutOfRangeData() {
        assertFalse(HorseDataValidator.isValid(Double.NaN, 1, .2, .7, "male", 5));
        assertFalse(HorseDataValidator.isValid(20, 21, .2, .7, "male", 5));
        assertFalse(HorseDataValidator.isValid(20, 20, Double.POSITIVE_INFINITY, .7, "male", 5));
        assertFalse(HorseDataValidator.isValid(20, 20, .2, -.1, "male", 5));
        assertFalse(HorseDataValidator.isValid(20, 20, .2, .7, "unknown", 5));
        assertFalse(HorseDataValidator.isValid(20, 20, .2, .7, "male", 0));
    }

    @Test void restoresFullHealthAfterFeedingTrainingAndFloatStorage() {
        double trainedMaxHealth = 20.0 * (1.0 + (0.5 * 0.2 / 100.0));
        double storedCurrentHealth = (double) (float) trainedMaxHealth;
        assertTrue(storedCurrentHealth > trainedMaxHealth);
        assertFalse(HorseDataValidator.isValid(trainedMaxHealth, storedCurrentHealth, .2, .7, "male", 10));

        double restoredHealth = HorseDataValidator.normalizeCurrentHealth(trainedMaxHealth, storedCurrentHealth);
        assertEquals(trainedMaxHealth, restoredHealth);
        assertTrue(HorseDataValidator.isValid(trainedMaxHealth, restoredHealth, .2, .7, "male", 10));
    }

    @Test void preservesDamagedHealthAndFloatRoundingDown() {
        assertEquals(12.5, HorseDataValidator.normalizeCurrentHealth(20.02, 12.5));
        double maxHealth = 20.06;
        double roundedHealth = (double) (float) maxHealth;
        assertTrue(roundedHealth < maxHealth);
        assertEquals(roundedHealth, HorseDataValidator.normalizeCurrentHealth(maxHealth, roundedHealth));
        assertEquals(maxHealth, HorseDataValidator.normalizeCurrentHealth(maxHealth, maxHealth));
    }

    @Test void rejectsOverflowBeyondTheFloatRoundedMaximum() {
        double maxHealth = 20.02;
        double overflow = Math.nextUp((double) (float) maxHealth);
        double restoredHealth = HorseDataValidator.normalizeCurrentHealth(maxHealth, overflow);
        assertEquals(overflow, restoredHealth);
        assertFalse(HorseDataValidator.isValid(maxHealth, restoredHealth, .2, .7, "male", 10));
        assertEquals(21.0, HorseDataValidator.normalizeCurrentHealth(maxHealth, 21.0));
    }

    @Test void doesNotRepairInvalidHealthValues() {
        for (double currentHealth : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -1.0}) {
            double restoredHealth = HorseDataValidator.normalizeCurrentHealth(20.02, currentHealth);
            assertEquals(currentHealth, restoredHealth);
            assertFalse(HorseDataValidator.isValid(20.02, restoredHealth, .2, .7, "male", 10));
        }
        assertEquals(1.0, HorseDataValidator.normalizeCurrentHealth(Double.NaN, 1.0));
        assertEquals(1.0, HorseDataValidator.normalizeCurrentHealth(0.0, 1.0));
        assertEquals(Double.MAX_VALUE, HorseDataValidator.normalizeCurrentHealth(Double.MAX_VALUE / 2.0, Double.MAX_VALUE));
    }
}
