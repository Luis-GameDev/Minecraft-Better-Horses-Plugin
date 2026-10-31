package me.luisgamedev.betterhorses.inspection;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;

class HorseNeuteringGateTest {
    @Test void disablingGlobalNeuteringOverridesEnabledShearsBeforeAnyEntityOrItemAccess() throws Exception {
        Field unsafe = Unsafe.class.getDeclaredField("theUnsafe");
        unsafe.setAccessible(true);
        TestPlugin plugin = (TestPlugin) ((Unsafe) unsafe.get(null)).allocateInstance(TestPlugin.class);
        plugin.configuration = new YamlConfiguration();
        plugin.configuration.set("horse-neutering.enabled", false);
        plugin.configuration.set("horse-neutering.shears-enabled", true);
        Player player = untouched(Player.class);
        AbstractHorse horse = untouched(AbstractHorse.class);
        assertFalse(new HorseNeuteringService(plugin).neuter(player, horse, EquipmentSlot.HAND));
    }

    public static final class TestPlugin extends BetterHorses {
        FileConfiguration configuration;
        @Override public FileConfiguration getConfig() { return configuration; }
    }

    private static <T> T untouched(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> { throw new AssertionError("Disabled neutering accessed entity: " + method); }));
    }
}
