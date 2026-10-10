package me.luisgamedev.betterhorses.utils;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Determines whether BetterHorses is allowed to observe or modify a world. */
public final class WorldAccessPolicy {
    private static boolean whitelist;
    private static Set<String> worlds = Set.of();

    private WorldAccessPolicy() {
    }

    public static void reload(BetterHorses plugin) {
        whitelist = plugin.getConfig().getString("world-filter.mode", "BLACKLIST")
                .equalsIgnoreCase("WHITELIST");
        Set<String> configured = new HashSet<>();
        for (String value : plugin.getConfig().getStringList("world-filter.worlds")) {
            configured.add(value.trim().toLowerCase(Locale.ROOT));
        }
        worlds = Set.copyOf(configured);
    }

    public static boolean isEnabled(World world) {
        if (world == null) return true;
        boolean listed = worlds.contains(world.getName().toLowerCase(Locale.ROOT))
                || worlds.contains(world.getUID().toString().toLowerCase(Locale.ROOT));
        return whitelist ? listed : !listed;
    }
}
