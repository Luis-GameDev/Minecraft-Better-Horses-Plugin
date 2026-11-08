package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AbstractHorseInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static me.luisgamedev.betterhorses.listeners.TraitLifecycleRegressionTest.*;
import static org.junit.jupiter.api.Assertions.*;

class HorseItemBlockerListenerTest {
    @Test void storedHorseItemsCannotBeDispensedOrEquippedByDispensers() throws Exception {
        try (Case c = new Case()) {
            org.bukkit.block.Block block = proxy(org.bukkit.block.Block.class,
                    (target, method, args) -> method.getName().equals("getWorld") ? c.fixture.world : zero(method.getReturnType()));
            for (Material material : List.of(Material.SADDLE, Material.APPLE)) {
                ItemStack stored = storedHorse(material);
                var plain = new org.bukkit.event.block.BlockDispenseEvent(block, stored, new org.bukkit.util.Vector());
                var equip = new org.bukkit.event.block.BlockDispenseArmorEvent(block, stored, c.fixture.horse.handle);
                c.listener.onDispense(plain);
                c.listener.onDispense(equip);
                assertTrue(plain.isCancelled());
                assertTrue(equip.isCancelled());
                assertSame(plain.getHandlers(), equip.getHandlers());
            }
        }
    }

    @Test void ordinarySaddleDispensingIsStillAllowed() throws Exception {
        try (Case c = new Case()) {
            org.bukkit.block.Block block = proxy(org.bukkit.block.Block.class,
                    (target, method, args) -> method.getName().equals("getWorld") ? c.fixture.world : zero(method.getReturnType()));
            var plain = new org.bukkit.event.block.BlockDispenseEvent(block, new ItemStack(Material.SADDLE), new org.bukkit.util.Vector());
            var equip = new org.bukkit.event.block.BlockDispenseArmorEvent(block, new ItemStack(Material.SADDLE), c.fixture.horse.handle);
            c.listener.onDispense(plain);
            c.listener.onDispense(equip);
            assertFalse(plain.isCancelled());
            assertFalse(equip.isCancelled());
        }
    }

    @Test void numberKeysCannotEquipAStoredHorseFromTheHotbar() throws Exception {
        try (Case c = new Case()) {
            c.bottom[0] = storedHorse(Material.SADDLE);
            InventoryClickEvent event = c.click(0, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0);
            c.listener.onInventoryClick(event);
            assertTrue(event.isCancelled());
        }
    }

    @Test void numberKeyRemovalThatReaddsTheHotbarItemToThePlayerIsAllowed() throws Exception {
        try (Case c = new Case()) {
            c.top[0] = storedHorse(Material.SADDLE);
            c.bottom[0] = storedHorse(Material.SADDLE);
            // This action takes the clicked item out; its hotbar item goes back into player storage.
            InventoryClickEvent event = c.click(0, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_MOVE_AND_READD, 0);
            c.listener.onInventoryClick(event);
            assertFalse(event.isCancelled());
        }
    }

    @Test void offhandSwapsInspectTheOffhandRatherThanTheHotbar() throws Exception {
        try (Case c = new Case()) {
            c.offhand = storedHorse(Material.SADDLE);
            InventoryClickEvent event = c.click(0, ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP, -1);
            c.listener.onInventoryClick(event);
            assertTrue(event.isCancelled());

            c.offhand = new ItemStack(Material.SADDLE);
            c.bottom[0] = storedHorse(Material.SADDLE);
            event = c.click(0, ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP, -1);
            c.listener.onInventoryClick(event);
            assertFalse(event.isCancelled());
        }
    }

    @Test void cursorAndCreativePlacementCannotInsertStoredItems() throws Exception {
        try (Case c = new Case()) {
            // A custom horse-item material must also be protected from chest/cargo placement.
            c.cursor = storedHorse(Material.APPLE);
            for (InventoryAction action : List.of(InventoryAction.PLACE_ALL, InventoryAction.PLACE_SOME,
                    InventoryAction.PLACE_ONE, InventoryAction.SWAP_WITH_CURSOR)) {
                InventoryClickEvent event = c.click(1, ClickType.LEFT, action, -1);
                c.listener.onInventoryClick(event);
                assertTrue(event.isCancelled(), action.name());
            }
            InventoryCreativeEvent creative = new InventoryCreativeEvent(c.view,
                    InventoryType.SlotType.CONTAINER, 0, c.cursor);
            c.cursor = null;
            c.listener.onInventoryClick(creative);
            assertTrue(creative.isCancelled());
        }
    }

    @Test void shiftTransfersAreBlockedOnlyWhenTheirDestinationIsTheHorse() throws Exception {
        try (Case c = new Case()) {
            c.bottom[0] = storedHorse(Material.SADDLE);
            InventoryClickEvent insert = c.click(c.playerSlot(), ClickType.SHIFT_LEFT,
                    InventoryAction.MOVE_TO_OTHER_INVENTORY, -1);
            c.listener.onInventoryClick(insert);
            assertTrue(insert.isCancelled());

            c.top[0] = c.bottom[0];
            InventoryClickEvent recover = c.click(0, ClickType.SHIFT_LEFT,
                    InventoryAction.MOVE_TO_OTHER_INVENTORY, -1);
            c.listener.onInventoryClick(recover);
            assertFalse(recover.isCancelled());
        }
    }

    @Test void previouslyInsertedHorseItemsRemainRecoverable() throws Exception {
        try (Case c = new Case()) {
            c.top[0] = storedHorse(Material.SADDLE);
            for (InventoryAction action : List.of(InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF,
                    InventoryAction.DROP_ALL_SLOT, InventoryAction.DROP_ONE_SLOT, InventoryAction.COLLECT_TO_CURSOR)) {
                c.cursor = action == InventoryAction.COLLECT_TO_CURSOR ? storedHorse(Material.SADDLE) : null;
                InventoryClickEvent event = c.click(0, ClickType.LEFT, action, -1);
                c.listener.onInventoryClick(event);
                assertFalse(event.isCancelled(), action.name());
            }
            c.bottom[0] = new ItemStack(Material.SADDLE);
            InventoryClickEvent recover = c.click(0, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0);
            c.listener.onInventoryClick(recover);
            assertFalse(recover.isCancelled());
        }
    }

    @Test void playerInventoryMovesAndOrdinaryHorseEquipmentStayUsable() throws Exception {
        try (Case c = new Case()) {
            c.cursor = storedHorse(Material.SADDLE);
            c.bottom[0] = storedHorse(Material.SADDLE);
            for (InventoryAction action : List.of(InventoryAction.PICKUP_ALL, InventoryAction.PLACE_ALL,
                    InventoryAction.SWAP_WITH_CURSOR, InventoryAction.HOTBAR_SWAP)) {
                InventoryClickEvent event = c.click(c.playerSlot(), ClickType.LEFT, action, 0);
                c.listener.onInventoryClick(event);
                assertFalse(event.isCancelled(), action.name());
            }
            c.cursor = new ItemStack(Material.SADDLE);
            InventoryClickEvent ordinary = c.click(0, ClickType.LEFT, InventoryAction.PLACE_ALL, -1);
            c.listener.onInventoryClick(ordinary);
            assertFalse(ordinary.isCancelled());
        }
    }

    @Test void dragsOnlyBlockWhenAStoredHorseWouldEnterHorseSlots() throws Exception {
        try (Case c = new Case()) {
            ItemStack stored = storedHorse(Material.SADDLE);
            InventoryDragEvent mixed = new InventoryDragEvent(c.view, null, stored, false,
                    Map.of(0, stored, c.playerSlot(), stored));
            c.listener.onInventoryDrag(mixed);
            assertTrue(mixed.isCancelled());

            InventoryDragEvent playerOnly = new InventoryDragEvent(c.view, null, stored, false,
                    Map.of(c.playerSlot(), stored));
            c.listener.onInventoryDrag(playerOnly);
            assertFalse(playerOnly.isCancelled());
        }
    }

    @Test void storingHorseItemsInOrdinaryChestsIsAllowed() throws Exception {
        try (Case c = new Case()) {
            c.horseView = false;
            c.cursor = storedHorse(Material.SADDLE);
            InventoryClickEvent event = c.click(0, ClickType.LEFT, InventoryAction.PLACE_ALL, -1);
            c.listener.onInventoryClick(event);
            assertFalse(event.isCancelled());
        }
    }

    private static ItemStack storedHorse(Material material) {
        ItemMeta meta = proxy(ItemMeta.class, (target, method, args) -> method.getName().equals("getPersistentDataContainer")
                ? data(new HashMap<>(Map.of(BetterHorseKeys.HEALTH, 20.0))) : zero(method.getReturnType()));
        return new ItemStack(material) {
            @Override public ItemMeta getItemMeta() { return meta; }
        };
    }

    private static final class Case implements AutoCloseable {
        final Fixture fixture = new Fixture();
        final ItemStack[] top = new ItemStack[2], bottom = new ItemStack[41];
        ItemStack cursor, offhand;
        boolean horseView = true;
        final PlayerInventory inventory = proxy(PlayerInventory.class, (target, method, args) -> switch (method.getName()) {
            case "getItem" -> bottom[(int) args[0]];
            case "getItemInOffHand" -> offhand;
            case "getSize" -> bottom.length;
            default -> zero(method.getReturnType());
        });
        final Player player = proxy(Player.class, (target, method, args) -> switch (method.getName()) {
            case "getInventory" -> inventory;
            case "getItemOnCursor" -> cursor;
            default -> fixture.player.invoke(target, method, args);
        });
        final Inventory horse = proxy(AbstractHorseInventory.class, (target, method, args) -> switch (method.getName()) {
            case "getSize" -> top.length;
            case "getItem" -> top[(int) args[0]];
            default -> zero(method.getReturnType());
        });
        final Inventory chest = proxy(Inventory.class, (target, method, args) -> switch (method.getName()) {
            case "getSize" -> top.length;
            case "getItem" -> top[(int) args[0]];
            default -> zero(method.getReturnType());
        });
        final InventoryView view = new InventoryView() {
            public Inventory getTopInventory() { return horseView ? horse : chest; }
            public Inventory getBottomInventory() { return inventory; }
            public HumanEntity getPlayer() { return player; }
            public InventoryType getType() { return InventoryType.CHEST; }
            public String getTitle() { return "Inventory"; }
            public String getOriginalTitle() { return "Inventory"; }
            public void setTitle(String title) { }
        };
        final HorseItemBlockerListener listener = new HorseItemBlockerListener();

        Case() throws Exception { }
        int playerSlot() { return top.length + 27; }
        InventoryClickEvent click(int rawSlot, ClickType click, InventoryAction action, int hotbar) {
            return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot, click, action, hotbar);
        }
        @Override public void close() throws Exception { fixture.close(); }
    }
}
