package me.luisgamedev.betterhorses.traits;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.Particle;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class TraitParticleResolver {

    private static final Set<String> warnedInvalidParticles = new HashSet<>();

    private TraitParticleResolver() {
    }

    public static Particle getTraitParticle(String traitKey, Particle fallback) {
        BetterHorses plugin = BetterHorses.getInstance();
        String path = "traits." + traitKey + ".particle";
        String configured = plugin.getConfig().getString(path, fallback.name());

        if (configured == null || configured.isBlank()) {
            return fallback;
        }

        String normalized = configured.trim().toUpperCase(Locale.ROOT);
        try {
            return Particle.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            String warningKey = traitKey.toLowerCase(Locale.ROOT) + ":" + normalized;
            if (warnedInvalidParticles.add(warningKey)) {
                plugin.getLogger().warning("Invalid particle '" + configured + "' at " + path
                        + ". Falling back to " + fallback.name() + ".");
            }
            return fallback;
        }
    }
}
