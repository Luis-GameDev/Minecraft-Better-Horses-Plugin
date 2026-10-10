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
}
