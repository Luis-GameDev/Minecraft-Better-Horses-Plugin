package me.luisgamedev.betterhorses.growing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrowthScaleTest {
    @Test void tenStagesRepresentTenPercentStepsOfConfiguredMaximum() {
        for (double maximum : new double[]{0.5D, 1.0D, 1.3D, 2.0D}) {
            double previous = 0.0D;
            for (int stage = 1; stage <= GrowthScale.MAX_STAGE; stage++) {
                double scale = GrowthScale.forStage(stage, maximum);
                assertEquals(maximum * stage / GrowthScale.MAX_STAGE, scale, 1e-12);
                assertEquals(maximum / GrowthScale.MAX_STAGE, scale - previous, 1e-12);
                assertTrue(scale > previous);
                previous = scale;
            }
            assertEquals(maximum, previous, 1e-12);
        }
    }

    @Test void babyAttributeCompensatesForVanillaSizeMultiplier() {
        for (double maximum : new double[]{0.5D, 1.0D, 1.3D, 2.0D}) {
            for (int stage = 1; stage <= GrowthScale.MAX_STAGE; stage++) {
                double target = GrowthScale.forStage(stage, maximum);
                assertEquals(target, GrowthScale.forStage(stage, maximum, false) * 0.5D, 1e-12);
                assertEquals(Math.max(0.0625D, target), GrowthScale.forStage(stage, maximum, true), 1e-12);
            }
        }
    }

    @Test void becomingAdultKeepsVisibleStageSizeWhenWithinAttributeBounds() {
        for (double maximum : new double[]{0.5D, 1.0D, 1.3D, 2.0D}) {
            for (int threshold : new int[]{1, 7, 10}) {
                for (int stage = 1; stage <= GrowthScale.MAX_STAGE; stage++) {
                    boolean adult = stage >= threshold;
                    double actualVisualSize = GrowthScale.forStage(stage, maximum, adult) * (adult ? 1.0D : 0.5D);
                    double minimumVisualSize = adult ? 0.0625D : 0.03125D;
                    assertEquals(Math.max(minimumVisualSize, GrowthScale.forStage(stage, maximum)), actualVisualSize, 1e-12);
                }
                double babyVisualSize = GrowthScale.forStage(threshold, maximum, false) * 0.5D;
                double adultVisualSize = GrowthScale.forStage(threshold, maximum, true);
                assertEquals(Math.max(0.0625D, babyVisualSize), adultVisualSize, 1e-12);
            }
        }
    }

    @Test void stagesOutsideRangeUseNearestStage() {
        for (int stage : new int[]{Integer.MIN_VALUE, -4, 0}) {
            assertEquals(GrowthScale.forStage(1, 1.3D), GrowthScale.forStage(stage, 1.3D));
        }
        for (int stage : new int[]{11, 100, Integer.MAX_VALUE}) {
            assertEquals(GrowthScale.forStage(10, 1.3D), GrowthScale.forStage(stage, 1.3D));
        }
    }

    @Test void invalidMaximumUsesVanillaSizedAdult() {
        for (double invalid : new double[]{0.0D, -1.0D, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            for (int stage = 1; stage <= GrowthScale.MAX_STAGE; stage++) {
                assertEquals(GrowthScale.forStage(stage, 1.0D), GrowthScale.forStage(stage, invalid));
                for (boolean adult : new boolean[]{false, true}) {
                    assertEquals(GrowthScale.forStage(stage, 1.0D, adult), GrowthScale.forStage(stage, invalid, adult));
                }
            }
        }
    }

    @Test void attributeScaleStaysWithinVanillaBoundsForExtremeMaximums() {
        for (double maximum : new double[]{Double.MIN_VALUE, 0.001D, 0.5D, 16.0D, Double.MAX_VALUE}) {
            for (int stage = 1; stage <= GrowthScale.MAX_STAGE; stage++) {
                assertTrue(Double.isFinite(GrowthScale.forStage(stage, maximum)));
                assertTrue(GrowthScale.forStage(stage, maximum) > 0.0D);
                for (boolean adult : new boolean[]{false, true}) {
                    double scale = GrowthScale.forStage(stage, maximum, adult);
                    assertTrue(Double.isFinite(scale));
                    assertTrue(scale >= 0.0625D && scale <= 16.0D);
                }
            }
        }
        assertEquals(0.0625D, GrowthScale.forStage(1, 0.01D, true));
        assertEquals(16.0D, GrowthScale.forStage(10, 16.0D, false));
        assertEquals(16.0D, GrowthScale.forStage(10, Double.MAX_VALUE, true));
    }
}
