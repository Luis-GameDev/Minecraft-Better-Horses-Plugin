package me.luisgamedev.betterhorses.api.events;

import org.bukkit.Material;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.junit.jupiter.api.Test;

import static me.luisgamedev.betterhorses.api.events.HorseTrampleEventTest.entity;
import static org.junit.jupiter.api.Assertions.*;

class BetterHorseNeuterEventTest {
    @Test void existingItemConstructorAndReplacementContractRemainAvailable() throws Exception {
        Player player = entity(Player.class);
        ItemStack item = new ItemStack(Material.SADDLE);
        BetterHorseNeuterEvent event = new BetterHorseNeuterEvent(player, item);
        assertTrue(event.getPlayer() == player);
        assertTrue(event.getHorseItem() == item);
        assertTrue(event.getHorse() == null);
        assertTrue(event.isItem());
        assertFalse(event.isEntity());
        assertEquals(BetterHorseNeuterEvent.TargetType.ITEM, event.getTargetType());
        assertEquals(EquipmentSlot.HAND, event.getHand());
        ItemStack replacement = new ItemStack(Material.PAPER);
        event.setHorseItem(replacement);
        assertTrue(event.getHorseItem() == replacement);
        assertThrows(NullPointerException.class, () -> event.setHorseItem(null));
        assertTrue(event.getHorseItem() == replacement);
        assertTrue(BetterHorseNeuterEvent.class.getConstructor(Player.class, ItemStack.class) != null);
        assertEquals(ItemStack.class, BetterHorseNeuterEvent.class.getMethod("getHorseItem").getReturnType());
        assertEquals(void.class, BetterHorseNeuterEvent.class.getMethod("setHorseItem", ItemStack.class).getReturnType());
    }

    @Test void entityTargetExposesItsHandWithoutPretendingToHaveAnItem() {
        Player player = entity(Player.class);
        AbstractHorse horse = entity(AbstractHorse.class);
        BetterHorseNeuterEvent event = new BetterHorseNeuterEvent(player, horse, EquipmentSlot.OFF_HAND);
        assertTrue(event.getPlayer() == player);
        assertTrue(event.getHorse() == horse);
        assertTrue(event.getHorseItem() == null);
        assertTrue(event.isEntity());
        assertFalse(event.isItem());
        assertEquals(BetterHorseNeuterEvent.TargetType.ENTITY, event.getTargetType());
        assertEquals(EquipmentSlot.OFF_HAND, event.getHand());
        assertEquals(EquipmentSlot.HAND, new BetterHorseNeuterEvent(player, horse).getHand());
        assertThrows(IllegalStateException.class, () -> event.setHorseItem(new ItemStack(Material.SADDLE)));
        assertThrows(IllegalStateException.class, () -> event.setHorseItem(null));
        assertTrue(event.getHorse() == horse);
        assertTrue(event.getHorseItem() == null);
    }

    @Test void constructorsRequireThePlayerAndTheSelectedTarget() {
        Player player = entity(Player.class);
        AbstractHorse horse = entity(AbstractHorse.class);
        ItemStack item = new ItemStack(Material.PAPER);
        assertThrows(NullPointerException.class, () -> new BetterHorseNeuterEvent(null, item));
        assertThrows(NullPointerException.class, () -> new BetterHorseNeuterEvent(player, (ItemStack) null));
        assertThrows(NullPointerException.class, () -> new BetterHorseNeuterEvent(null, horse));
        assertThrows(NullPointerException.class, () -> new BetterHorseNeuterEvent(player, (AbstractHorse) null));
        assertThrows(NullPointerException.class, () -> new BetterHorseNeuterEvent(player, horse, null));
    }

    @Test void itemAndEntityInstancesShareTheUnifiedHandlerListAndCanBeCancelled() {
        BetterHorseNeuterEvent item = new BetterHorseNeuterEvent(entity(Player.class), new ItemStack(Material.PAPER));
        BetterHorseNeuterEvent live = new BetterHorseNeuterEvent(entity(Player.class), entity(AbstractHorse.class));
        assertTrue(item.getHandlers() == BetterHorseNeuterEvent.getHandlerList());
        assertTrue(live.getHandlers() == item.getHandlers());
        assertFalse(item.isCancelled());
        assertFalse(live.isCancelled());
        live.setCancelled(true);
        assertTrue(live.isCancelled());
        assertFalse(item.isCancelled());
        live.setCancelled(false);
        assertFalse(live.isCancelled());
    }

    @SuppressWarnings("deprecation")
    @Test void legacyEntityEventRetainsItsContractAndSharesUnifiedHandlers() {
        Player player = entity(Player.class);
        AbstractHorse horse = entity(AbstractHorse.class);
        BetterHorseEntityNeuterEvent legacy = new BetterHorseEntityNeuterEvent(player, horse, EquipmentSlot.OFF_HAND);
        assertTrue(legacy instanceof BetterHorseNeuterEvent);
        assertTrue(legacy.getPlayer() == player);
        assertTrue(legacy.getHorse() == horse);
        assertEquals(EquipmentSlot.OFF_HAND, legacy.getHand());
        assertTrue(legacy.isEntity());
        assertTrue(legacy.getHorseItem() == null);
        assertTrue(legacy.getHandlers() == BetterHorseEntityNeuterEvent.getHandlerList());
        assertTrue(legacy.getHandlers() == BetterHorseNeuterEvent.getHandlerList());
        assertTrue(BetterHorseEntityNeuterEvent.class.isAnnotationPresent(Deprecated.class));
        assertFalse(legacy.isCancelled());
        legacy.setCancelled(true);
        assertTrue(legacy.isCancelled());
        legacy.setCancelled(false);
        assertFalse(legacy.isCancelled());
        assertThrows(IllegalStateException.class, () -> legacy.setHorseItem(new ItemStack(Material.PAPER)));
    }

    @SuppressWarnings("deprecation")
    @Test void nativeEventExecutorsFilterItemsAndShareOneEntityCancellationState() throws Exception {
        EventCallbacks callbacks = new EventCallbacks();
        EventExecutor unified = EventExecutor.create(EventCallbacks.class.getMethod("onUnified", BetterHorseNeuterEvent.class), BetterHorseNeuterEvent.class);
        EventExecutor legacy = EventExecutor.create(EventCallbacks.class.getMethod("onLegacy", BetterHorseEntityNeuterEvent.class), BetterHorseEntityNeuterEvent.class);
        BetterHorseNeuterEvent item = new BetterHorseNeuterEvent(entity(Player.class), new ItemStack(Material.PAPER));
        unified.execute(callbacks, item);
        legacy.execute(callbacks, item);
        assertEquals(1, callbacks.unifiedCalls);
        assertEquals(0, callbacks.legacyCalls);
        assertTrue(item.isCancelled());
        BetterHorseEntityNeuterEvent live = new BetterHorseEntityNeuterEvent(entity(Player.class), entity(AbstractHorse.class), EquipmentSlot.HAND);
        unified.execute(callbacks, live);
        legacy.execute(callbacks, live);
        assertEquals(2, callbacks.unifiedCalls);
        assertEquals(1, callbacks.legacyCalls);
        assertTrue(callbacks.unifiedEvent == live);
        assertTrue(callbacks.legacyEvent == live);
        assertTrue(callbacks.legacySawCancellation);
        assertFalse(live.isCancelled());
    }

    @SuppressWarnings("deprecation")
    public static final class EventCallbacks implements Listener {
        int unifiedCalls;
        int legacyCalls;
        BetterHorseNeuterEvent unifiedEvent;
        BetterHorseEntityNeuterEvent legacyEvent;
        boolean legacySawCancellation;
        public void onUnified(BetterHorseNeuterEvent event) {
            unifiedCalls++;
            unifiedEvent = event;
            event.setCancelled(true);
        }
        public void onLegacy(BetterHorseEntityNeuterEvent event) {
            legacyCalls++;
            legacyEvent = event;
            legacySawCancellation = event.isCancelled();
            event.setCancelled(false);
        }
    }
}
