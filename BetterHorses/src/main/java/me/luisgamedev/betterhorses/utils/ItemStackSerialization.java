package me.luisgamedev.betterhorses.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Portable equipment encoding, with read support for items previously written through Paper's API. */
public final class ItemStackSerialization {
    private static final byte[] PREFIX = {'B', 'H', 'I', 'S', 1};
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private ItemStackSerialization() { }

    /** A failure is explicit so a caller never removes the source mount after losing its equipment. */
    public static byte[] serialize(ItemStack item) {
        if (item == null) throw new IllegalArgumentException("Cannot serialize a missing item");
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            bytes.write(PREFIX);
            try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
                output.writeObject(item);
            }
            byte[] result = bytes.toByteArray();
            if (result.length > MAX_BYTES) throw new IllegalArgumentException("Stored equipment is too large");
            return result;
        } catch (IOException | RuntimeException | LinkageError failure) {
            throw new IllegalArgumentException("Could not preserve item metadata", failure);
        }
    }

    /** Invalid or unsupported legacy data is rejected rather than replaced with a metadata-free item. */
    public static ItemStack deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Invalid stored equipment payload");
        }
        if (hasPrefix(bytes)) return readPortable(bytes, PREFIX.length);
        // Accept an unmarked Bukkit stream, but never mistake a failed portable decode for legacy NBT.
        if (bytes.length >= 2 && bytes[0] == (byte) 0xac && bytes[1] == (byte) 0xed) return readPortable(bytes, 0);
        try {
            Method legacy = ItemStack.class.getMethod("deserializeBytes", byte[].class);
            Object result = legacy.invoke(null, (Object) bytes);
            if (result instanceof ItemStack item) return item;
            throw new IllegalArgumentException("Legacy equipment decoder returned no item");
        } catch (InvocationTargetException failure) {
            throw new IllegalArgumentException("Legacy equipment could not be read", failure.getCause());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            throw new IllegalArgumentException("This server cannot read the stored equipment", failure);
        }
    }

    private static ItemStack readPortable(byte[] bytes, int offset) {
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(
                new ByteArrayInputStream(bytes, offset, bytes.length - offset))) {
            input.setObjectInputFilter(info -> info.depth() > 64 || info.references() > 100_000
                    || info.arrayLength() > 1_000_000 || info.streamBytes() > MAX_BYTES
                    ? ObjectInputFilter.Status.REJECTED : ObjectInputFilter.Status.UNDECIDED);
            Object result = input.readObject();
            if (result instanceof ItemStack item) return item;
            throw new IllegalArgumentException("Stored equipment is not an item");
        } catch (IOException | ClassNotFoundException | RuntimeException | LinkageError failure) {
            throw new IllegalArgumentException("Stored equipment could not be read", failure);
        }
    }

    private static boolean hasPrefix(byte[] bytes) {
        return bytes.length >= PREFIX.length && Arrays.equals(PREFIX, Arrays.copyOf(bytes, PREFIX.length));
    }
}
