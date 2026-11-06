package me.luisgamedev.betterhorses.listeners;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HorseStepHeightRecoveryTest {
    @Test void transientRidingAdjustmentNeverChangesTheNativeBaseAndRemovesOnlyItsModifier() throws Exception {
        try (Fixture fixture = new Fixture(true)) {
            fixture.base = 0.8D;
            AttributeModifier foreign = new AttributeModifier(UUID.randomUUID(), "other-plugin", .05D,
                    AttributeModifier.Operation.ADD_NUMBER);
            fixture.modifiers.put(foreign.getUniqueId(), foreign);
            HorseStepHeightListener.applyStepHeight(fixture.horse);
            assertEquals(.8D, fixture.base);
            assertTrue(fixture.modifiers.containsKey(HorseStepHeightListener.MODIFIER_ID));
            assertEquals(.3D, fixture.modifiers.get(HorseStepHeightListener.MODIFIER_ID).getAmount(), 1e-12);
            assertTrue(fixture.data.isEmpty(), "Paper/Folia should not persist a native-base restoration record");
            HorseStepHeightListener.restoreStepHeight(fixture.horse);
            assertEquals(.8D, fixture.base);
            assertEquals(List.of(foreign), new ArrayList<>(fixture.modifiers.values()));
        }
    }

    @Test void spigotFallbackStoresRecoveryBeforeChangingTheBaseAndCanRecoverAfterTrackingIsLost() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.base = .9D;
            HorseStepHeightListener.applyStepHeight(fixture.horse);
            assertEquals(1.1D, fixture.base);
            assertEquals(.9D, fixture.data.get(HorseStepHeightListener.ORIGINAL_KEY));
            assertEquals(1.1D, fixture.data.get(HorseStepHeightListener.APPLIED_KEY));
            HorseStepHeightListener.restoreStepHeight(fixture.horse);
            assertEquals(.9D, fixture.base);
            assertTrue(fixture.data.isEmpty());
        }
    }

    @Test void fallbackCleanupPreservesAnExternalChangeInsteadOfRestoringAnOutdatedBase() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            HorseStepHeightListener.applyStepHeight(fixture.horse);
            fixture.base = 2.0D;
            HorseStepHeightListener.restoreStepHeight(fixture.horse);
            assertEquals(2.0D, fixture.base);
            assertTrue(fixture.data.isEmpty());
        }
    }

    @Test void staleTransientModifierCanBeRemovedWithoutAnyInMemoryOriginalValue() throws Exception {
        try (Fixture fixture = new Fixture(true)) {
            fixture.base = .75D;
            fixture.modifiers.put(HorseStepHeightListener.MODIFIER_ID,
                    new AttributeModifier(HorseStepHeightListener.MODIFIER_ID, "betterhorses.step-height", .35D,
                            AttributeModifier.Operation.ADD_NUMBER));
            HorseStepHeightListener.restoreStepHeight(fixture.horse);
            assertTrue(fixture.modifiers.isEmpty());
            assertEquals(.75D, fixture.base);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Map<NamespacedKey, Object> data = new HashMap<>();
        final Map<UUID, AttributeModifier> modifiers = new HashMap<>();
        final Field server;
        final Object previous;
        final AbstractHorse horse;
        double base = 1.0D;

        Fixture(boolean transientSupported) throws Exception {
            server = Bukkit.class.getDeclaredField("server");
            server.setAccessible(true);
            previous = server.get(null);
            server.set(null, Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getMinecraftVersion" -> "1.20.6";
                        case "getBukkitVersion" -> "1.20.6-R0.1-SNAPSHOT";
                        default -> throw new AssertionError("Unexpected server access: " + method);
                    }));
            PersistentDataContainer container = (PersistentDataContainer) Proxy.newProxyInstance(
                    PersistentDataContainer.class.getClassLoader(), new Class<?>[]{PersistentDataContainer.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "get" -> data.get(args[0]);
                        case "set" -> { data.put((NamespacedKey) args[0], args[2]); yield null; }
                        case "remove" -> { data.remove(args[0]); yield null; }
                        default -> throw new AssertionError("Unexpected data access: " + method);
                    });
            AttributeInstance attribute = (AttributeInstance) Proxy.newProxyInstance(AttributeInstance.class.getClassLoader(),
                    new Class<?>[]{AttributeInstance.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getBaseValue" -> base;
                        case "setBaseValue" -> { base = (Double) args[0]; yield null; }
                        case "getModifiers" -> new ArrayList<>(modifiers.values());
                        case "removeModifier" -> { modifiers.remove(((AttributeModifier) args[0]).getUniqueId()); yield null; }
                        case "addTransientModifier" -> {
                            if (!transientSupported) throw new UnsupportedOperationException("Spigot-style implementation");
                            AttributeModifier modifier = (AttributeModifier) args[0];
                            modifiers.put(modifier.getUniqueId(), modifier); yield null;
                        }
                        default -> throw new AssertionError("Unexpected attribute access: " + method);
                    });
            UUID id = UUID.randomUUID();
            horse = (AbstractHorse) Proxy.newProxyInstance(AbstractHorse.class.getClassLoader(), new Class<?>[]{AbstractHorse.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getAttribute" -> attribute;
                        case "getPersistentDataContainer" -> container;
                        case "getType" -> EntityType.HORSE;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new AssertionError("Unexpected horse access: " + method);
                    });
        }

        @Override public void close() throws Exception {
            HorseStepHeightListener.restoreStepHeight(horse);
            server.set(null, previous);
        }
    }
}
