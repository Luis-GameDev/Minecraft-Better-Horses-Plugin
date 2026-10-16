package me.luisgamedev.betterhorses.growing;

public final class GrowthScale {
    public static final int MAX_STAGE = 10;
    private static final double BABY_SCALE = 0.5D;
    private static final double MIN_ATTRIBUTE_SCALE = 0.0625D;
    private static final double MAX_ATTRIBUTE_SCALE = 16.0D;

    private GrowthScale() {}

    public static int normalize(int stage) { return Math.max(1, Math.min(MAX_STAGE, stage)); }

    /** Desired visible size relative to an adult vanilla mount. */
    public static double forStage(int stage, double maxScale) {
        double maximum = Double.isFinite(maxScale) && maxScale > 0.0D ? maxScale : 1.0D;
        return Math.max(Double.MIN_VALUE, maximum * (normalize(stage) / (double) MAX_STAGE));
    }

    /** SCALE attribute value, compensating for vanilla's additional baby size multiplier. */
    public static double forStage(int stage, double maxScale, boolean adult) {
        double attributeScale = forStage(stage, maxScale) / (adult ? 1.0D : BABY_SCALE);
        return Math.max(MIN_ATTRIBUTE_SCALE, Math.min(MAX_ATTRIBUTE_SCALE, attributeScale));
    }
}
