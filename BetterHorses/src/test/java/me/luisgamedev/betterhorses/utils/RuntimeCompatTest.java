package me.luisgamedev.betterhorses.utils;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.meta.Damageable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeCompatTest {
    @Test void particleConfigurationAcceptsBothSidesOfThe1205Rename() {
        assertSame(RuntimeCompat.particle("SMOKE"), RuntimeCompat.particle("SMOKE_NORMAL"));
        assertSame(RuntimeCompat.particle("WITCH"), RuntimeCompat.particle("SPELL_WITCH"));
        assertSame(Particle.CLOUD, RuntimeCompat.particle("cloud"));
    }

    @Test void missingPreferredParticleUsesAnAvailableFallbackInsteadOfLinkingAMissingField() {
        assertSame(Particle.CLOUD, RuntimeCompat.particle("DOES_NOT_EXIST", "CLOUD"));
        assertThrows(IllegalArgumentException.class, () -> RuntimeCompat.particle("DOES_NOT_EXIST"));
    }

    @Test void customDurabilityIsReadOnlyWhenPresentAndOtherwiseUsesTheMaterial() {
        assertEquals(517, RuntimeCompat.maximumDamage(metadata(true, 517), Material.SHEARS));
        assertEquals(Material.SHEARS.getMaxDurability(), RuntimeCompat.maximumDamage(metadata(false, 0), Material.SHEARS));
    }

    private static Damageable metadata(boolean hasMaximum, int maximum) {
        return (Damageable) Proxy.newProxyInstance(Damageable.class.getClassLoader(), new Class<?>[]{Damageable.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hasMaxDamage" -> hasMaximum;
                    case "getMaxDamage" -> maximum;
                    default -> throw new AssertionError("Unexpected metadata read: " + method);
                });
    }
}
