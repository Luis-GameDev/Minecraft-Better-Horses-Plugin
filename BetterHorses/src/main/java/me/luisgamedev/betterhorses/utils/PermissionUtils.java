package me.luisgamedev.betterhorses.utils;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;

public final class PermissionUtils {
    private static final Set<String> TRAIT_SPECIFIC_PERMISSIONS = Set.of(
            "hellmare",
            "fireheart",
            "featherhooves",
            "dashboost",
            "kickback",
            "ghosthorse",
            "heavenhooves",
            "undead"
    );

    private PermissionUtils() {
    }

    public static final String MOUNT = "betterhorses.mount";
    public static final String FEED = "betterhorses.feed";
    public static final String BREED = "betterhorses.breed";
    public static final String TAME = "betterhorses.tame";
    public static final String LEASH = "betterhorses.leash";
    public static final String TRAMPLE = "betterhorses.trample";
    public static final String SPAWN_RIGHT_CLICK = "betterhorses.spawn.rightclick";
    public static final String TRAINING = "betterhorses.training";
    public static final String COOLDOWN_BYPASS = "betterhorses.cooldown.bypass";
    public static final String RIDER_INVINCIBLE = "betterhorses.rider.invincible";
    public static final String RIDER_DAMAGE_BOOST = "betterhorses.rider.damageboost";
    public static final String TRAIT_USE = "betterhorses.trait.use";
    public static final String TRAIT_RECEIVE = "betterhorses.trait.receive";
    public static final String INFO = "betterhorses.info";
    public static final String SPAWN_COMMAND = "betterhorses.spawn.command";
    public static final String DESPAWN = "betterhorses.despawn";

    public static boolean has(CommandSender sender, String permission) {
        return sender.hasPermission(permission);
    }

    public static boolean canUseTrait(Player player, String traitKey) {
        if (!player.hasPermission(TRAIT_USE)) {
            return false;
        }
        String normalized = normalizeTraitKey(traitKey);
        return normalized.isEmpty()
                || !TRAIT_SPECIFIC_PERMISSIONS.contains(normalized)
                || player.hasPermission("betterhorses.trait." + normalized);
    }

    public static boolean canReceiveTrait(Player player, String traitKey) {
        if (!player.hasPermission(TRAIT_RECEIVE)) {
            return false;
        }
        String normalized = normalizeTraitKey(traitKey);
        return normalized.isEmpty()
                || !TRAIT_SPECIFIC_PERMISSIONS.contains(normalized)
                || player.hasPermission("betterhorses.trait." + normalized);
    }

    public static void deny(Player player, String command) {
        BetterHorses.getInstance().getLang().sendFormatted(player, "messages.insufficient-permission", "%command%", command);
    }

    public static String normalizeTraitKey(String traitKey) {
        if (traitKey == null) {
            return "";
        }
        return traitKey.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .trim();
    }
}
