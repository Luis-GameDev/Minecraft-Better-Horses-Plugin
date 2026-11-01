package me.luisgamedev.betterhorses.traits;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.RuntimeCompat;
import org.bukkit.Particle;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;
import java.util.Set;

public final class TraitParticleResolver {

    private static final Set<String> warnedInvalidParticles = ConcurrentHashMap.newKeySet();

    private TraitParticleResolver() {
    }

    public static Particle getTraitParticle(String traitKey, String... fallbackNames) {
        return getTraitParticle(traitKey, RuntimeCompat.particle(fallbackNames));
    }

    public static Particle getTraitParticle(String traitKey, Particle fallback) {
        BetterHorses plugin = BetterHorses.getInstance();
        String path = "traits." + traitKey + ".particle";
        // Every trait emitter uses a no-data overload, including callers' fallback particles.
        if (fallback.getDataType() != Void.class) fallback = Particle.CLOUD;
        String configured = plugin.getConfig().getString(path, fallback.name());

        if (configured == null || configured.isBlank()) {
            return fallback;
        }

        String normalized = configured.trim().toUpperCase(Locale.ROOT);
        try {
            Particle particle = RuntimeCompat.particle(normalized);
            if (particle.getDataType() != Void.class) {
                throw new IllegalArgumentException("Particle requires " + particle.getDataType().getSimpleName());
            }
            return particle;
        } catch (IllegalArgumentException ignored) {
            String warningKey = traitKey.toLowerCase(Locale.ROOT) + ":" + normalized;
            if (warnedInvalidParticles.add(warningKey)) {
                plugin.getLogger().warning("Unsupported no-data particle '" + configured + "' at " + path
                        + ". Falling back to " + fallback.name() + ".");
            }
            return fallback;
        }
    }
}
