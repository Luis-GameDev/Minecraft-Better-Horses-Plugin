package me.luisgamedev.utils;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot;
import com.comphenix.protocol.wrappers.Pair;
import me.luisgamedev.BetterHorses;
import org.bukkit.Material;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public class ArmorHider {

    public static void hide(Player player, Horse horse) {
        if (!BetterHorses.getInstance().isProtocolLibAvailable()) return;

        try {
            // hide player armor
            List<Pair<ItemSlot, ItemStack>> hiddenPlayer = new ArrayList<>();
            hiddenPlayer.add(new Pair<>(ItemSlot.HEAD, null));
            hiddenPlayer.add(new Pair<>(ItemSlot.CHEST, null));
            hiddenPlayer.add(new Pair<>(ItemSlot.LEGS, null));
            hiddenPlayer.add(new Pair<>(ItemSlot.FEET, null));
            hiddenPlayer.add(new Pair<>(ItemSlot.MAINHAND, null));
            hiddenPlayer.add(new Pair<>(ItemSlot.OFFHAND, null));

            PacketContainer playerPacket = new PacketContainer(PacketType.Play.Server.ENTITY_EQUIPMENT);
            playerPacket.getIntegers().write(0, player.getEntityId());
            playerPacket.getSlotStackPairLists().write(0, hiddenPlayer);

            // hide horse armor
            HorseInventory inv = horse.getInventory();
            List<Pair<ItemSlot, ItemStack>> hiddenHorse = new ArrayList<>();
            hiddenHorse.add(new Pair<>(ItemSlot.CHEST, null));

            PacketContainer horsePacket = new PacketContainer(PacketType.Play.Server.ENTITY_EQUIPMENT);
            horsePacket.getIntegers().write(0, horse.getEntityId());
            horsePacket.getSlotStackPairLists().write(0, hiddenHorse);

            for (Player viewer : player.getWorld().getPlayers()) {
                if (viewer.equals(player)) continue;
                ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, playerPacket);
                ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, horsePacket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void show(Player player, Horse horse) {
        if (!BetterHorses.getInstance().isProtocolLibAvailable()) {
            return;
        }

        try {
            // show player armor
            List<Pair<ItemSlot, ItemStack>> visiblePlayer = new ArrayList<>();
            visiblePlayer.add(new Pair<>(ItemSlot.HEAD, player.getInventory().getHelmet()));
            visiblePlayer.add(new Pair<>(ItemSlot.CHEST, player.getInventory().getChestplate()));
            visiblePlayer.add(new Pair<>(ItemSlot.LEGS, player.getInventory().getLeggings()));
            visiblePlayer.add(new Pair<>(ItemSlot.FEET, player.getInventory().getBoots()));
            visiblePlayer.add(new Pair<>(ItemSlot.MAINHAND, player.getInventory().getItemInMainHand()));
            visiblePlayer.add(new Pair<>(ItemSlot.OFFHAND, player.getInventory().getItemInOffHand()));

            PacketContainer playerPacket = new PacketContainer(PacketType.Play.Server.ENTITY_EQUIPMENT);
            playerPacket.getIntegers().write(0, player.getEntityId());
            playerPacket.getSlotStackPairLists().write(0, visiblePlayer);

            // show horse armor
            HorseInventory inv = horse.getInventory();
            List<Pair<ItemSlot, ItemStack>> visibleHorse = new ArrayList<>();
            visibleHorse.add(new Pair<>(ItemSlot.CHEST, inv.getArmor()));

            PacketContainer horsePacket = new PacketContainer(PacketType.Play.Server.ENTITY_EQUIPMENT);
            horsePacket.getIntegers().write(0, horse.getEntityId());
            horsePacket.getSlotStackPairLists().write(0, visibleHorse);

            for (Player viewer : player.getWorld().getPlayers()) {
                if (viewer.equals(player)) continue;
                ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, playerPacket);
                ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, horsePacket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
