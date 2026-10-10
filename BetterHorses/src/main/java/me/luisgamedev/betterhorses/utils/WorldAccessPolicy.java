package me.luisgamedev.betterhorses.utils;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Determines whether BetterHorses is allowed to observe or modify a world. */
public final class WorldAccessPolicy {
    private static volatile Snapshot snapshot = new Snapshot(false, Set.of());

    private WorldAccessPolicy() {
    }

    public static void reload(BetterHorses plugin) {
        boolean whitelist = plugin.getConfig().getString("world-filter.mode", "BLACKLIST")
                .equalsIgnoreCase("WHITELIST");
        Set<String> configured = new HashSet<>();
        for (String value : plugin.getConfig().getStringList("world-filter.worlds")) {
            configured.add(value.trim().toLowerCase(Locale.ROOT));
        }
        snapshot = new Snapshot(whitelist, Set.copyOf(configured));
    }

    public static boolean isEnabled(World world) {
        if (world == null) return true;
        Snapshot current = snapshot;
        boolean listed = current.worlds.contains(world.getName().toLowerCase(Locale.ROOT))
                || current.worlds.contains(world.getUID().toString().toLowerCase(Locale.ROOT));
        return current.whitelist ? listed : !listed;
    }

    private record Snapshot(boolean whitelist, Set<String> worlds) { }
}
