package me.luisgamedev.betterhorses.growing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GrowthProgressTest {
    @Test void configuredDurationCoversAllNineStepsFromFoalToFullSize() {
        long interval = GrowthProgress.intervalMillis(3);
        assertEquals(20000L, interval);
        assertEquals(9, GrowthProgress.advance(1, 0L, 179999L, interval, false).stage());
        assertEquals(10, GrowthProgress.advance(1, 0L, 180000L, interval, false).stage());
        assertTrue(GrowthProgress.intervalMillis(0) > 0);
    }

    @Test void missingTimestampIsRetainedAndGrowthAdvancesOnLaterTicks() {
        GrowthProgress first = GrowthProgress.advance(1, null, 1000L, 18000L, false);
        assertEquals(1, first.stage());
        assertEquals(1000L, first.updatedAt());
        GrowthProgress later = GrowthProgress.advance(first.stage(), first.updatedAt(), 19000L, 18000L, false);
        assertEquals(2, later.stage());
        assertEquals(19000L, later.updatedAt());
    }

    @Test void retainsPartialIntervalsAndCatchesUpWithoutExceedingMaximum() {
        GrowthProgress progress = GrowthProgress.advance(1, 1000L, 39000L, 18000L, false);
        assertEquals(3, progress.stage());
        assertEquals(37000L, progress.updatedAt());
        assertEquals(3, GrowthProgress.advance(3, progress.updatedAt(), 54999L, 18000L, false).stage());
        assertEquals(4, GrowthProgress.advance(3, progress.updatedAt(), 55000L, 18000L, false).stage());
        assertEquals(10, GrowthProgress.advance(9, 0L, 1000000L, 18000L, false).stage());
    }

    @Test void mountedHorsesPauseAndUseTheConfiguredIntervalAfterDismounting() {
        GrowthProgress mounted = GrowthProgress.advance(5, 1000L, 60000L, 6000L, true);
        assertEquals(5, mounted.stage());
        assertEquals(60000L, mounted.updatedAt());
        assertEquals(5, GrowthProgress.advance(5, mounted.updatedAt(), 65999L, 6000L, false).stage());
        assertEquals(6, GrowthProgress.advance(5, mounted.updatedAt(), 66000L, 6000L, false).stage());
    }

    @Test void normalizesLegacyStagesAndRecoversFromClockRollback() {
        assertEquals(1, GrowthProgress.advance(0, null, 1000L, 6000L, false).stage());
        assertEquals(10, GrowthProgress.advance(99, null, 1000L, 6000L, false).stage());
        GrowthProgress rollback = GrowthProgress.advance(1, 100000L, 1000L, 6000L, false);
        assertEquals(1000L, rollback.updatedAt());
        assertEquals(2, GrowthProgress.advance(1, rollback.updatedAt(), 7000L, 6000L, false).stage());
    }
}
