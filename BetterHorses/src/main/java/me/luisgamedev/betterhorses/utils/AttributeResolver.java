package me.luisgamedev.betterhorses.utils;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;

/**
 * Resolves attribute names across Minecraft versions where the Bukkit
 * attribute enum changed from the "GENERIC_" prefix to prefix-less names.
 */
public final class AttributeResolver {

    private static final boolean USE_GENERIC_PREFIX = !isAtLeast(1, 21, 11);

    private AttributeResolver() {}

    /**
     * Resolves an attribute that used to have the {@code GENERIC_} prefix.
     *
     * @param baseName attribute name without the {@code GENERIC_} prefix
     * @return the matching {@link Attribute} for the running server version
     */
    public static Attribute generic(String baseName) {
        String normalized = baseName.startsWith("GENERIC_")
                ? baseName.substring("GENERIC_".length())
                : baseName;

        if (USE_GENERIC_PREFIX) {
            return resolveWithFallback("GENERIC_" + normalized, normalized);
        }

        return resolveWithFallback(normalized, "GENERIC_" + normalized);
    }

    /**
     * Resolves horse jump strength across versions where it was renamed from
     * {@code HORSE_JUMP_STRENGTH} to {@code JUMP_STRENGTH}.
     *
     * @return the matching jump strength {@link Attribute}
     */
    public static Attribute horseJumpStrength() {
        return resolveWithFallback("HORSE_JUMP_STRENGTH", "JUMP_STRENGTH");
    }

    private static Attribute resolveWithFallback(String primary, String fallback) {
        try {
            return Attribute.valueOf(primary);
        } catch (IllegalArgumentException ignored) {
            return Attribute.valueOf(fallback);
        }
    }

    private static boolean isAtLeast(int major, int minor, int patch) {
        String version = Bukkit.getMinecraftVersion();
        String[] parts = version.split("[-+]")[0].split("\\.");

        int serverMajor = parsePart(parts, 0);
        int serverMinor = parsePart(parts, 1);
        int serverPatch = parsePart(parts, 2);

        if (serverMajor != major) return serverMajor > major;
        if (serverMinor != minor) return serverMinor > minor;
        return serverPatch >= patch;
    }

    private static int parsePart(String[] parts, int index) {
        if (index >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[index].replaceAll("[^0-9]", ""));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
