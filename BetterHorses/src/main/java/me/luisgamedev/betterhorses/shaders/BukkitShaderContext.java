package me.luisgamedev.betterhorses.shaders;

import me.clip.placeholderapi.PlaceholderAPI;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.utils.MountConfig;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** A short-lived context, read exclusively on the player's owning thread. */
final class BukkitShaderContext implements ShaderContext {
    private final Player player;
    private final AbstractHorse mount;
    private final SupportedMountType mountType;
    private final FileConfiguration config;
    private final Consumer<String> warning;
    private final Map<String, String> placeholders = new HashMap<>();

    BukkitShaderContext(Player player, FileConfiguration config, Consumer<String> warning) {
        this.player = player;
        this.config = config;
        this.warning = warning;
        SupportedMountType candidate = SupportedMountType.fromEntity(player.getVehicle()).orElse(null);
        mountType = candidate != null && candidate.isEnabled(config) ? candidate : null;
        mount = mountType == null ? null : (AbstractHorse) player.getVehicle();
    }

    @Override
    public boolean matches(String condition, String value) {
        return switch (normalize(condition)) {
            case "mounted" -> mount != null;
            case "unmounted" -> mount == null;
            case "sneaking" -> player.isSneaking();
            case "sprinting" -> player.isSprinting();
            case "swimming" -> player.isSwimming();
            case "gliding" -> player.isGliding();
            case "flying" -> player.isFlying();
            case "onfire" -> player.getFireTicks() > 0;
            case "frozen" -> player.isFrozen();
            case "gender" -> mount != null && equal(value, data().get(BetterHorseKeys.GENDER, PersistentDataType.STRING));
            case "mounttype" -> mountType != null && SupportedMountType.fromUserInput(normalize(value)).orElse(null) == mountType;
            case "traited" -> hasTrait() && (normalize(value).equals("default") || equal(value,
                    data().get(BetterHorseKeys.TRAIT, PersistentDataType.STRING)));
            case "world" -> value.equalsIgnoreCase(player.getWorld().getName())
                    || value.equalsIgnoreCase(player.getWorld().getUID().toString());
            case "biome" -> equal(value, player.getLocation().getBlock().getBiome().name());
            case "weather" -> switch (normalize(value)) {
                case "dry" -> !player.getWorld().hasStorm() && !player.getWorld().isThundering();
                case "rain" -> player.getWorld().hasStorm() && !player.getWorld().isThundering();
                case "thunder" -> player.getWorld().isThundering();
                default -> false;
            };
            case "environment" -> equal(value, player.getWorld().getEnvironment().name());
            case "gamemode" -> equal(value, player.getGameMode().name());
            case "permission" -> player.hasPermission(value);
            case "owner" -> ownerMatches(value);
            case "growth" -> mount != null && (equal(value, "baby") ? isBaby()
                    : equal(value, "adult") && !isBaby());
            case "neutered" -> mount != null && (equal(value, "yes") ? isNeutered()
                    : equal(value, "no") && !isNeutered());
            default -> false;
        };
    }

    private PersistentDataContainer data() { return mount.getPersistentDataContainer(); }

    private boolean hasTrait() {
        if (mount == null) return false;
        String trait = data().get(BetterHorseKeys.TRAIT, PersistentDataType.STRING);
        return trait != null && !trait.isBlank() && !trait.equalsIgnoreCase("none");
    }

    private boolean ownerMatches(String value) {
        if (mount == null) return false;
        String owner = data().get(BetterHorseKeys.OWNER, PersistentDataType.STRING);
        if (owner == null || owner.isBlank()) return equal(value, "none");
        boolean self = player.getUniqueId().toString().equalsIgnoreCase(owner);
        return equal(value, self ? "self" : "other");
    }

    private boolean isNeutered() {
        return Byte.valueOf((byte) 1).equals(data().get(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE));
    }

    private boolean isBaby() {
        Integer stage = data().get(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER);
        if (stage != null && MountConfig.isGrowthEnabled(config, mountType)) return stage < 10;
        return !mount.isAdult();
    }

    @Override
    public String placeholder(String expression) {
        return placeholders.computeIfAbsent(expression, key -> {
            if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return "";
            try {
                String result = PlaceholderAPI.setPlaceholders(player, key);
                return result == null || result.equals(key) ? "" : result.trim();
            } catch (RuntimeException | LinkageError exception) {
                warning.accept("A shader placeholder could not be evaluated: " + key);
                return "";
            }
        });
    }

    private static boolean equal(String expected, String actual) {
        return actual != null && normalize(expected).equals(normalize(actual));
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }
}
