package me.luisgamedev.betterhorses.growing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrowthStateTest {
    private static final long INTERVAL = 20_000L;

    @Test void unloadedAndDespawnedPausesPreserveExactlyHalfAnInterval() {
        GrowthState loaded = GrowthState.initial(5, 1000L).advance(11_000L, INTERVAL, true);
        assertEquals(new GrowthState(5, 10_000L, 11_000L), loaded);

        GrowthState unloaded = loaded.advance(9_000_000L, INTERVAL, false);
        GrowthState despawned = unloaded.advance(10_000_000L, INTERVAL, false);
        assertEquals(5, despawned.stage());
        assertEquals(10_000L, despawned.elapsedMillis());
        assertEquals(10_000L, despawned.remainingMillis(INTERVAL));
        assertEquals(5, despawned.advance(10_009_999L, INTERVAL, true).stage());
        assertEquals(new GrowthState(6, 0L, 10_010_000L), despawned.advance(10_010_000L, INTERVAL, true));
    }

    @Test void enabledAwayGrowthCatchesUpSeveralStagesAndRetainsRemainder() {
        GrowthState state = new GrowthState(5, 10_000L, 1000L).advance(52_000L, INTERVAL, true);
        assertEquals(new GrowthState(8, 1000L, 52_000L), state);
        assertEquals(19_000L, state.remainingMillis(INTERVAL));
    }

    @Test void alternatingLoadedRiddenAndUnavailablePeriodsNeverCountsPausedTimeTwice() {
        GrowthState state = GrowthState.initial(5, 0L)
                .advance(9000L, INTERVAL, true)
                .advance(90_000L, INTERVAL, false)
                .advance(96_000L, INTERVAL, true)
                .advance(1_000_000L, INTERVAL, false)
                .advance(1_005_000L, INTERVAL, true);
        assertEquals(new GrowthState(6, 0L, 1_005_000L), state);
    }

    @Test void maturityDiscardsUnusedTimeAndDoesNotScheduleAnotherStage() {
        GrowthState mature = new GrowthState(9, 10_000L, 1000L).advance(1_000_000L, INTERVAL, true);
        assertEquals(10, mature.stage());
        assertEquals(0L, mature.elapsedMillis());
        assertEquals(Long.MAX_VALUE, mature.remainingMillis(INTERVAL));
        assertEquals(10, mature.advance(Long.MAX_VALUE, INTERVAL, true).stage());
    }

    @Test void intervalChangesRetainEarnedMillisecondsAndUseTheNewDuration() {
        GrowthState state = new GrowthState(5, 15_000L, 1000L);
        assertEquals(new GrowthState(6, 5000L, 1000L), state.advance(1000L, 10_000L, false));
        assertEquals(new GrowthState(5, 15_000L, 1000L), state.advance(1000L, 40_000L, false));
    }

    @Test void clockRollbackRetainsProgressAndStartsANewAnchor() {
        GrowthState rollback = new GrowthState(5, 10_000L, 100_000L).advance(1000L, INTERVAL, true);
        assertEquals(new GrowthState(5, 10_000L, 1000L), rollback);
        assertEquals(6, rollback.advance(11_000L, INTERVAL, true).stage());
    }

    @Test void malformedAndExtremeValuesCannotOverflowOrExceedStageBounds() {
        assertEquals(1, new GrowthState(0, -1L, 0L).stage());
        assertEquals(0L, new GrowthState(5, -1L, 0L).elapsedMillis());
        assertEquals(new GrowthState(10, 0L, 0L), new GrowthState(99, Long.MAX_VALUE, 0L));
        assertEquals(10, new GrowthState(1, Long.MAX_VALUE, Long.MIN_VALUE).advance(Long.MAX_VALUE, INTERVAL, true).stage());
        assertEquals(2, GrowthState.initial(1, 0L).advance(1L, 0L, true).stage());
    }

    @Test void legacyBoundaryMigrationRetainsPartialProgressAndDoesNotCountTimeTwice() {
        GrowthState migrated = HorseGrowthData.fromLegacy(5, 1000L, 52_000L, INTERVAL);
        assertEquals(new GrowthState(7, 11_000L, 52_000L), migrated);
        assertEquals(migrated, migrated.advance(52_000L, INTERVAL, true));
        assertEquals(new GrowthState(5, 0L, 52_000L), HorseGrowthData.fromLegacy(5, null, 52_000L, INTERVAL));
        assertEquals(new GrowthState(5, 0L, 52_000L), HorseGrowthData.fromLegacy(5, 60_000L, 52_000L, INTERVAL));
    }
}
