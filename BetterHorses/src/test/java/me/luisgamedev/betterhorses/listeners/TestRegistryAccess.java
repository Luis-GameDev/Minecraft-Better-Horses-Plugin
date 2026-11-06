package me.luisgamedev.betterhorses.listeners;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;

import java.lang.reflect.Proxy;
import java.util.*;

/** Minimal native registry bootstrap for API-only potion/damage lifecycle tests. */
public final class TestRegistryAccess implements RegistryAccess {
    private final Map<String, Registry<?>> registries = new HashMap<>();

    @Override public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        String kind = type == PotionEffectType.class ? "mob_effect" : type == DamageType.class ? "damage_type" : type.getName();
        return registry(kind);
    }

    @Override public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key) {
        return registry(key.key().value());
    }

    @SuppressWarnings("unchecked") private synchronized <T extends Keyed> Registry<T> registry(String kind) {
        return (Registry<T>) registries.computeIfAbsent(kind, ignored -> {
            Map<NamespacedKey, Keyed> values = new LinkedHashMap<>();
            return (Registry<?>) Proxy.newProxyInstance(Registry.class.getClassLoader(), new Class<?>[]{Registry.class}, (proxy, method, args) -> switch (method.getName()) {
                case "get", "getOrThrow" -> values.computeIfAbsent((NamespacedKey) args[0], key -> {
                    if (kind.equals("mob_effect")) return new TestPotion(key);
                    if (kind.equals("damage_type")) return (DamageType) Proxy.newProxyInstance(DamageType.class.getClassLoader(),
                            new Class<?>[]{DamageType.class}, (target, getter, arguments) -> switch (getter.getName()) {
                                case "getKey" -> key;
                                case "getTranslationKey" -> "damage." + key.getKey();
                                case "equals" -> target == arguments[0];
                                case "hashCode" -> key.hashCode();
                                default -> TraitLifecycleRegressionTest.zero(getter.getReturnType());
                            });
                    return null;
                });
                case "iterator" -> List.copyOf(values.values()).iterator();
                case "stream" -> List.copyOf(values.values()).stream();
                case "getKey" -> args[0] instanceof Keyed keyed ? keyed.getKey() : null;
                default -> TraitLifecycleRegressionTest.zero(method.getReturnType());
            });
        });
    }

    private static final class TestPotion extends PotionEffectType {
        private final NamespacedKey key;
        TestPotion(NamespacedKey key) { this.key = key; }
        @Override public NamespacedKey getKey() { return key; }
        @Override public String getName() { return key.getKey().toUpperCase(Locale.ROOT); }
        @Override public int getId() { return key.hashCode(); }
        @Override public PotionEffect createEffect(int duration, int amplifier) { return new PotionEffect(this, duration, amplifier); }
        @Override public boolean isInstant() { return false; }
        @Override public Color getColor() { return Color.WHITE; }
        @Override public double getDurationModifier() { return 1; }
        @Override public PotionEffectTypeCategory getCategory() { return PotionEffectTypeCategory.BENEFICIAL; }
        @Override public Category getEffectCategory() { return Category.BENEFICIAL; }
        @Override public Map<Attribute, AttributeModifier> getEffectAttributes() { return Map.of(); }
        @Override public double getAttributeModifierAmount(Attribute attribute, int amplifier) { return 0; }
        @Override public String getTranslationKey() { return "effect." + key.getKey(); }
        @Override public String translationKey() { return getTranslationKey(); }
    }
}
