package me.luisgamedev.betterhorses.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorseStatsTest {
    @Test void legacyTrainedHealthKeepsItsDamageRatio() {
        assertEquals(10.0D, HorseStats.projectHealth(12.0D, 24.0D, 20.0D));
        assertEquals(20.0D, HorseStats.projectHealth(24.0D, 24.0D, 20.0D));
        assertEquals(10.0D, HorseStats.projectHealth(10.0D, 20.0D, 20.0D));
    }

    @Test void deadOrInvalidHealthDoesNotBecomeAlive() {
        for (double value : new double[]{0.0D, -1.0D, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertEquals(0.0D, HorseStats.projectHealth(value, 24.0D, 20.0D));
        }
        for (double value : new double[]{0.0D, -1.0D, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(0.0D, HorseStats.projectHealth(12.0D, 24.0D, value));
        }
    }

    @Test void incompleteMaximumUsesABoundedFiniteFallback() {
        assertEquals(12.0D, HorseStats.projectHealth(12.0D, 0.0D, 20.0D));
        assertEquals(20.0D, HorseStats.projectHealth(24.0D, Double.NaN, 20.0D));
        assertEquals(20.0D, HorseStats.projectHealth(25.0D, 24.0D, 20.0D));
        assertEquals(20.0D, HorseStats.projectHealth((double) (float) 20.02D, 20.02D, 20.0D));
    }

    @Test void extremeFiniteValuesRemainFiniteAndBounded() {
        for (double current : new double[]{Double.MIN_VALUE, 1.0D, Double.MAX_VALUE / 2.0D, Double.MAX_VALUE}) {
            for (double maximum : new double[]{Double.MIN_VALUE, 1.0D, Double.MAX_VALUE}) {
                double result = HorseStats.projectHealth(current, maximum, Double.MAX_VALUE);
                assertTrue(Double.isFinite(result));
                assertTrue(result >= 0.0D && result <= Double.MAX_VALUE);
            }
        }
    }
}
