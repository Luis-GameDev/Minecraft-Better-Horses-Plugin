package me.luisgamedev.betterhorses.growing;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GrowthScale {
    public static final int MAX_STAGE = 10;
    private static final double BABY_SCALE = 0.5D;
    private static final double MODERN_HORSE_BABY_SCALE = 0.7D;
    private static final double MODERN_UNDEAD_HORSE_BABY_SCALE = 0.775D;
    private static final double MODERN_DONKEY_BABY_SCALE = 0.88D;
    private static final double MODERN_MULE_BABY_SCALE = 0.83D;
    private static final double MIN_ATTRIBUTE_SCALE = 0.0625D;
    private static final double MAX_ATTRIBUTE_SCALE = 16.0D;
    private static final Pattern MINECRAFT_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:[.\\-+].*)?$");

    private GrowthScale() {}

    public static int normalize(int stage) { return Math.max(1, Math.min(MAX_STAGE, stage)); }

    /** Visible size from half of the configured maximum at birth to the full maximum at stage ten. */
    public static double forStage(int stage, double maxScale) {
        double maximum = Double.isFinite(maxScale) && maxScale > 0.0D ? maxScale : 1.0D;
        double fraction = 0.5D + 0.5D * (normalize(stage) - 1) / (MAX_STAGE - 1);
        return Math.max(Double.MIN_VALUE, maximum * fraction);
    }

    /** Legacy SCALE attribute value, compensating for vanilla's half-size baby model. */
    public static double forStage(int stage, double maxScale, boolean adult) {
        return forStage(stage, maxScale, adult, BABY_SCALE);
    }

    /** SCALE attribute value normalized to the baby model's approximate standing height. */
    public static double forStage(int stage, double maxScale, boolean adult, double babyScale) {
        double factor = Double.isFinite(babyScale) && babyScale > 0.0D ? babyScale : BABY_SCALE;
        double attributeScale = forStage(stage, maxScale) / (adult ? 1.0D : factor);
        return Math.max(MIN_ATTRIBUTE_SCALE, Math.min(MAX_ATTRIBUTE_SCALE, attributeScale));
    }

    /** Modern foal meshes change proportions, so this normalizes height rather than every body part. */
    public static double babyScale(String mountType, String minecraftVersion) {
        if (mountType == null || minecraftVersion == null) return BABY_SCALE;
        String type = mountType.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        double modernScale = switch (type) {
            case "HORSE" -> MODERN_HORSE_BABY_SCALE;
            case "SKELETON_HORSE", "ZOMBIE_HORSE" -> MODERN_UNDEAD_HORSE_BABY_SCALE;
            case "DONKEY" -> MODERN_DONKEY_BABY_SCALE;
            case "MULE" -> MODERN_MULE_BABY_SCALE;
            default -> BABY_SCALE;
        };
        if (modernScale == BABY_SCALE) return BABY_SCALE;
        Matcher version = MINECRAFT_VERSION.matcher(minecraftVersion.trim());
        if (!version.matches()) return BABY_SCALE;
        try {
            int major = Integer.parseInt(version.group(1));
            int minor = Integer.parseInt(version.group(2));
            return major > 26 || (major == 26 && minor >= 1) ? modernScale : BABY_SCALE;
        } catch (NumberFormatException exception) {
            return BABY_SCALE;
        }
    }
}
