package me.luisgamedev.betterhorses.growing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrowthScaleTest {
    @Test void newbornStartsAtHalfMaximumAndNineEqualIncrementsReachFullSize() {
        for (double maximum : new double[]{0.5D, 1.0D, 1.3D, 2.0D}) {
            double previous = GrowthScale.forStage(1, maximum);
            assertEquals(maximum * 0.5D, previous, 1e-12);
            for (int stage = 2; stage <= GrowthScale.MAX_STAGE; stage++) {
                double scale = GrowthScale.forStage(stage, maximum);
                assertEquals(maximum / 18.0D, scale - previous, 1e-12);
                assertTrue(scale > previous);
                previous = scale;
            }
            assertEquals(maximum, previous, 1e-12);
        }
    }

    @Test void defaultNewbornIsVisibleAndAdultCrossoverAddsOneNormalGrowthStep() {
        assertEquals(0.65D, GrowthScale.forStage(1, 1.3D), 1e-12);
        double stageSixBaby = GrowthScale.forStage(6, 1.3D, false) * 0.5D;
        double stageSevenAdult = GrowthScale.forStage(7, 1.3D, true);
        assertEquals(1.011111111111111D, stageSixBaby, 1e-12);
        assertEquals(1.083333333333333D, stageSevenAdult, 1e-12);
        assertEquals(1.3D / 18.0D, stageSevenAdult - stageSixBaby, 1e-12);
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
            for (int threshold = 1; threshold <= GrowthScale.MAX_STAGE; threshold++) {
                double previousVisualSize = 0.0D;
                for (int stage = 1; stage <= GrowthScale.MAX_STAGE; stage++) {
                    boolean adult = stage >= threshold;
                    double actualVisualSize = GrowthScale.forStage(stage, maximum, adult) * (adult ? 1.0D : 0.5D);
                    double minimumVisualSize = adult ? 0.0625D : 0.03125D;
                    assertEquals(Math.max(minimumVisualSize, GrowthScale.forStage(stage, maximum)), actualVisualSize, 1e-12);
                    if (stage > 1) assertEquals(maximum / 18.0D, actualVisualSize - previousVisualSize, 1e-12);
                    previousVisualSize = actualVisualSize;
                }
                double babyVisualSize = GrowthScale.forStage(threshold, maximum, false) * 0.5D;
                double adultVisualSize = GrowthScale.forStage(threshold, maximum, true);
                assertEquals(Math.max(0.0625D, babyVisualSize), adultVisualSize, 1e-12);
            }
        }
    }

    @Test void modelSpecificBabyFactorKeepsEveryGrowthIncrementConsistentAcrossAdultThresholds() {
        for (double maximum : new double[]{0.5D, 1.0D, 1.3D, 2.0D}) {
            for (double babyFactor : new double[]{0.5D, 0.7D, 0.775D, 0.88D, 0.83D}) {
                for (int threshold = 1; threshold <= GrowthScale.MAX_STAGE; threshold++) {
                    double previousVisualSize = maximum * 0.5D;
                    for (int stage = 1; stage <= GrowthScale.MAX_STAGE; stage++) {
                        boolean adult = stage >= threshold;
                        double visualSize = GrowthScale.forStage(stage, maximum, adult, babyFactor) * (adult ? 1.0D : babyFactor);
                        assertEquals(GrowthScale.forStage(stage, maximum), visualSize, 1e-12);
                        if (stage > 1) assertEquals(maximum / 18.0D, visualSize - previousVisualSize, 1e-12);
                        previousVisualSize = visualSize;
                    }
                }
            }
        }
        double modernStageSixBaby = GrowthScale.forStage(6, 1.3D, false, 0.7D) * 0.7D;
        double modernStageSevenAdult = GrowthScale.forStage(7, 1.3D, true, 0.7D);
        assertEquals(1.3D / 18.0D, modernStageSevenAdult - modernStageSixBaby, 1e-12);
    }

    @Test void legacyOverloadAndInvalidBabyFactorsRetainLegacyCompensation() {
        for (int stage = 1; stage <= GrowthScale.MAX_STAGE; stage++) {
            for (boolean adult : new boolean[]{false, true}) {
                assertEquals(GrowthScale.forStage(stage, 1.3D, adult), GrowthScale.forStage(stage, 1.3D, adult, 0.5D));
                for (double invalid : new double[]{0.0D, -1.0D, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                    assertEquals(GrowthScale.forStage(stage, 1.3D, adult), GrowthScale.forStage(stage, 1.3D, adult, invalid));
                }
            }
        }
        assertEquals(16.0D, GrowthScale.forStage(1, 1.3D, false, Double.MIN_VALUE));
        assertEquals(0.0625D, GrowthScale.forStage(1, 1.3D, false, Double.MAX_VALUE));
    }

    @Test void modernHorseModelFactorStartsAtMinecraftTwentySixPointOne() {
        for (String version : new String[]{"26.1", "26.1-R0.1-SNAPSHOT", "26.3.build.157-beta", "26.3.1", "27.0", " 26.1+build.42 "}) {
            for (String type : new String[]{"HORSE", "horse", " Horse "}) {
                assertEquals(0.7D, GrowthScale.babyScale(type, version));
            }
            for (String type : new String[]{"SKELETON_HORSE", "ZOMBIE_HORSE", "skeleton-horse", " zombie horse "}) {
                assertEquals(0.775D, GrowthScale.babyScale(type, version));
            }
            for (String type : new String[]{"DONKEY", " donkey "}) {
                assertEquals(0.88D, GrowthScale.babyScale(type, version));
            }
            for (String type : new String[]{"MULE", " mule "}) {
                assertEquals(0.83D, GrowthScale.babyScale(type, version));
            }
        }
        for (String version : new String[]{"1.20.6", "1.21.11", "1.21.11-R0.1-SNAPSHOT", "25.9", "26.0"}) {
            for (String type : new String[]{"HORSE", "SKELETON_HORSE", "ZOMBIE_HORSE", "DONKEY", "MULE"}) {
                assertEquals(0.5D, GrowthScale.babyScale(type, version));
            }
        }
    }

    @Test void invalidVersionsAndUnrecognizedModelsUseLegacyFactor() {
        for (String version : new String[]{null, "", "unknown", "26", "26.x", "26.1beta", "Minecraft 26.1", "999999999999999999.1"}) {
            assertEquals(0.5D, GrowthScale.babyScale("HORSE", version));
        }
        for (String type : new String[]{null, "", "unknown", "CAMEL", "LLAMA"}) {
            assertEquals(0.5D, GrowthScale.babyScale(type, "26.3.build.157-beta"));
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
