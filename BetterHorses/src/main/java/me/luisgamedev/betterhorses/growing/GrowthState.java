package me.luisgamedev.betterhorses.growing;

/** Accounted growth time, including an exact partial interval toward the next stage. */
public record GrowthState(int stage, long elapsedMillis, long updatedAt) {
    public GrowthState {
        stage = GrowthScale.normalize(stage);
        elapsedMillis = stage == GrowthScale.MAX_STAGE ? 0L : Math.max(0L, elapsedMillis);
    }

    public static GrowthState initial(int stage, long now) {
        return new GrowthState(stage, 0L, now);
    }

    /**
     * Settle the clock before changing availability. Pauses move the anchor without
     * discarding earned time; changing the interval retains earned milliseconds.
     */
    public GrowthState advance(long now, long intervalMillis, boolean accrue) {
        long interval = Math.max(1L, intervalMillis);
        long elapsed = elapsedMillis;
        if (accrue && now > updatedAt) {
            long delta = now - updatedAt;
            if (delta < 0L || Long.MAX_VALUE - elapsed < delta) elapsed = Long.MAX_VALUE;
            else elapsed += delta;
        }
        long advances = Math.min(GrowthScale.MAX_STAGE - stage, elapsed / interval);
        int nextStage = stage + (int) advances;
        return new GrowthState(nextStage, nextStage == GrowthScale.MAX_STAGE ? 0L : elapsed - advances * interval, now);
    }

    public long remainingMillis(long intervalMillis) {
        if (stage == GrowthScale.MAX_STAGE) return Long.MAX_VALUE;
        return Math.max(0L, Math.max(1L, intervalMillis) - elapsedMillis);
    }
}
