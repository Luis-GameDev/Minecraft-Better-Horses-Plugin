package me.luisgamedev.betterhorses.shaders;

/** Read-only facts for one player's current shader evaluation. */
public interface ShaderContext {
    boolean matches(String condition, String value);

    /** Returns an empty string when a placeholder is unavailable or unresolved. */
    String placeholder(String expression);
}
