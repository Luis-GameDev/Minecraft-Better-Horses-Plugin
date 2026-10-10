package me.luisgamedev.betterhorses.growing;

public final class GrowthScale {
    public static final int MAX_STAGE = 10;
    private GrowthScale() {}
    public static int normalize(int stage) { return Math.max(1, Math.min(MAX_STAGE, stage)); }
    public static double forStage(int stage, double maxScale) {
        int normalized = normalize(stage);
        return 0.7D + (Math.max(0.7D, maxScale) - 0.7D) * normalized / MAX_STAGE;
    }
}
