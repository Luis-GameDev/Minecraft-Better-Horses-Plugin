package me.luisgamedev.betterhorses.growing;

/** Advances custom growth while retaining partial intervals between updates. */
public record GrowthProgress(int stage, long updatedAt) {
    public static long intervalMillis(int minutes) {
        return Math.max(1, minutes) * 60_000L / (GrowthScale.MAX_STAGE - 1);
    }

    public static GrowthProgress advance(int stage, Long previousUpdate, long now, long intervalMillis,
                                         boolean mounted) {
        stage = GrowthScale.normalize(stage);
        long updated = previousUpdate == null || previousUpdate > now ? now : previousUpdate;
        if (mounted) return new GrowthProgress(stage, now);
        long advances = Math.min(GrowthScale.MAX_STAGE - stage,
                Math.max(0L, (now - updated) / Math.max(1L, intervalMillis)));
        return new GrowthProgress(stage + (int) advances, updated + advances * Math.max(1L, intervalMillis));
    }
}
