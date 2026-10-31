package me.luisgamedev.betterhorses.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ItemStackSerializationTest {
    @Test void portableItemRoundTripRetainsMaterialAndAmountWithoutPaperByteMethods() throws Exception {
        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        Object previous = field.get(null);
        try {
            field.set(null, server());
            ItemStack original = new ItemStack(Material.SADDLE, 3);
            byte[] encoded = ItemStackSerialization.serialize(original);
            assertArrayEquals(new byte[]{'B', 'H', 'I', 'S', 1}, Arrays.copyOf(encoded, 5));
            ItemStack decoded = ItemStackSerialization.deserialize(encoded);
            assertEquals(original.getType(), decoded.getType());
            assertEquals(original.getAmount(), decoded.getAmount());
        } finally {
            field.set(null, previous);
        }
    }

    @Test void malformedOrWrongTypePortableDataFailsInsteadOfCreatingABareEquipmentItem() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> ItemStackSerialization.deserialize(null));
        assertThrows(IllegalArgumentException.class, () -> ItemStackSerialization.deserialize(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> ItemStackSerialization.deserialize(new byte[]{'B','H','I','S',1,0}));
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject("not an item");
            output.flush();
            assertThrows(IllegalArgumentException.class, () -> ItemStackSerialization.deserialize(bytes.toByteArray()));
        }
    }

    @Test void serializationFailureIsExplicitSoCaptureCanKeepItsSourceMount() {
        ItemStack failing = new ItemStack(Material.SADDLE) {
            @Override public java.util.Map<String, Object> serialize() { throw new IllegalStateException("Cannot read metadata"); }
        };
        assertThrows(IllegalArgumentException.class, () -> ItemStackSerialization.serialize(failing));
        assertThrows(IllegalArgumentException.class, () -> ItemStackSerialization.serialize(null));
    }

    private static Server server() {
        UnsafeValues unsafe = (UnsafeValues) Proxy.newProxyInstance(UnsafeValues.class.getClassLoader(), new Class<?>[]{UnsafeValues.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDataVersion" -> 3700;
                    case "getMaterial" -> Material.matchMaterial((String) args[0]);
                    default -> throw new AssertionError("Unexpected unsafe access: " + method);
                });
        ItemFactory factory = (ItemFactory) Proxy.newProxyInstance(ItemFactory.class.getClassLoader(), new Class<?>[]{ItemFactory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getItemMeta" -> null;
                    case "ensureServerConversions" -> args[0];
                    case "equals" -> args[0] == args[1];
                    default -> throw new AssertionError("Unexpected item factory access: " + method);
                });
        return (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUnsafe" -> unsafe;
                    case "getItemFactory" -> factory;
                    default -> throw new AssertionError("Unexpected server access: " + method);
                });
    }
}
