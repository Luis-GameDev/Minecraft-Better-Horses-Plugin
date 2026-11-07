package me.luisgamedev.betterhorses.listeners;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
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
                    if (kind.equals("enchantment") || kind.equals(Enchantment.class.getName())) return new TestEnchantment(key);
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

    private static final class TestEnchantment extends Enchantment {
        private final NamespacedKey key;
        TestEnchantment(NamespacedKey key) { this.key = key; }
        @Override public NamespacedKey getKey() { return key; }
        @Override public String getName() { return key.getKey().toUpperCase(Locale.ROOT); }
        @Override public int getMaxLevel() { return 3; }
        @Override public int getStartLevel() { return 1; }
        @Override public EnchantmentTarget getItemTarget() { return EnchantmentTarget.TOOL; }
        @Override public boolean isTreasure() { return false; }
        @Override public boolean isCursed() { return false; }
        @Override public boolean conflictsWith(Enchantment other) { return false; }
        @Override public boolean canEnchantItem(ItemStack item) { return true; }
        @Override public net.kyori.adventure.text.Component displayName(int level) { return net.kyori.adventure.text.Component.text(key.getKey()); }
        @Override public boolean isTradeable() { return true; }
        @Override public boolean isDiscoverable() { return true; }
        @Override public int getMinModifiedCost(int level) { return 1; }
        @Override public int getMaxModifiedCost(int level) { return 10; }
        @Override public int getAnvilCost() { return 1; }
        @Override public io.papermc.paper.enchantments.EnchantmentRarity getRarity() { return io.papermc.paper.enchantments.EnchantmentRarity.COMMON; }
        @Override public float getDamageIncrease(int level, org.bukkit.entity.EntityCategory category) { return 0; }
        @Override public float getDamageIncrease(int level, org.bukkit.entity.EntityType type) { return 0; }
        @Override public Set<EquipmentSlot> getActiveSlots() { return Set.of(EquipmentSlot.HAND); }
        @Override public String getTranslationKey() { return "enchantment." + key.getKey(); }
        @Override public String translationKey() { return getTranslationKey(); }
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
