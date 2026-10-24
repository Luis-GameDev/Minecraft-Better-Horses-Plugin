package me.luisgamedev.betterhorses.api.events;

import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class HorseTrampleEventTest {
    @Test void exposesTheRiderMountAndTargetAndAllowsValidChanges() {
        Player rider = entity(Player.class);
        AbstractHorse horse = entity(AbstractHorse.class);
        LivingEntity target = entity(LivingEntity.class);
        HorseTrampleEvent event = new HorseTrampleEvent(rider, horse, target, 3.5, new Vector(1, .2, -2), 20);
        assertTrue(event.getPlayer() == rider);
        assertTrue(event.getRider() == rider);
        assertTrue(event.getHorse() == horse);
        assertTrue(event.getTarget() == target);
        assertEquals(3.5, event.getDamage(), 0);
        assertEquals(new Vector(1, .2, -2), event.getKnockback());
        assertEquals(20, event.getCooldownTicks());
        event.setDamage(0);
        event.setKnockback(new Vector(-2, 0, 4));
        event.setCooldownTicks(0);
        assertEquals(0, event.getDamage(), 0);
        assertEquals(new Vector(-2, 0, 4), event.getKnockback());
        assertEquals(0, event.getCooldownTicks());
    }

    @Test void knockbackIsCopiedOnConstructionAssignmentAndRetrieval() {
        Vector original = new Vector(1, 2, 3);
        HorseTrampleEvent event = event(1, original, 5);
        original.setX(99);
        assertEquals(new Vector(1, 2, 3), event.getKnockback());
        Vector result = event.getKnockback();
        result.setY(99);
        assertEquals(new Vector(1, 2, 3), event.getKnockback());
        Vector replacement = new Vector(4, 5, 6);
        event.setKnockback(replacement);
        replacement.setZ(99);
        assertEquals(new Vector(4, 5, 6), event.getKnockback());
        assertTrue(event.getKnockback() != event.getKnockback());
    }

    @Test void invalidValuesAreRejectedWithoutChangingTheExistingEvent() {
        HorseTrampleEvent event = event(4, new Vector(1, 2, 3), 10);
        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> event.setDamage(invalid));
            assertThrows(IllegalArgumentException.class, () -> event(invalid, new Vector(), 0));
        }
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            for (Vector vector : new Vector[]{new Vector(invalid, 0, 0), new Vector(0, invalid, 0), new Vector(0, 0, invalid)}) {
                assertThrows(IllegalArgumentException.class, () -> event.setKnockback(vector));
                assertThrows(IllegalArgumentException.class, () -> event(0, vector, 0));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> event.setCooldownTicks(-1));
        assertThrows(IllegalArgumentException.class, () -> event(0, new Vector(), -1));
        assertThrows(NullPointerException.class, () -> event.setKnockback(null));
        assertEquals(4, event.getDamage(), 0);
        assertEquals(new Vector(1, 2, 3), event.getKnockback());
        assertEquals(10, event.getCooldownTicks());
    }

    @Test void requiresEveryReferenceAndSupportsTheDefaultCooldown() {
        Player player = entity(Player.class);
        AbstractHorse horse = entity(AbstractHorse.class);
        LivingEntity target = entity(LivingEntity.class);
        assertThrows(NullPointerException.class, () -> new HorseTrampleEvent(null, horse, target, 0, new Vector()));
        assertThrows(NullPointerException.class, () -> new HorseTrampleEvent(player, null, target, 0, new Vector()));
        assertThrows(NullPointerException.class, () -> new HorseTrampleEvent(player, horse, null, 0, new Vector()));
        assertThrows(NullPointerException.class, () -> new HorseTrampleEvent(player, horse, target, 0, null));
        assertEquals(0, new HorseTrampleEvent(player, horse, target, 0, new Vector()).getCooldownTicks());
    }

    @Test void cancellationAndHandlersAreIndependentOfOtherEventTypes() {
        HorseTrampleEvent event = event(1, new Vector(), 0);
        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
        event.setCancelled(false);
        assertFalse(event.isCancelled());
        assertTrue(event.getHandlers() == HorseTrampleEvent.getHandlerList());
        assertTrue(event.getHandlers() != BetterHorseNeuterEvent.getHandlerList());
        assertTrue(event(0, new Vector(), 0).getHandlers() == event.getHandlers());
    }

    private static HorseTrampleEvent event(double damage, Vector knockback, int cooldownTicks) {
        return new HorseTrampleEvent(entity(Player.class), entity(AbstractHorse.class), entity(LivingEntity.class), damage, knockback, cooldownTicks);
    }

    static <T> T entity(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("toString")) return type.getSimpleName();
            throw new AssertionError("Event API must not read entity state: " + method.getName());
        }));
    }
}
