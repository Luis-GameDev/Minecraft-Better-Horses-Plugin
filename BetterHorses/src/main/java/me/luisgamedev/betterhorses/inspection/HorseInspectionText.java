package me.luisgamedev.betterhorses.inspection;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.growing.GrowthScale;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.utils.HorseStats;
import me.luisgamedev.betterhorses.utils.MountConfig;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads a mount on its owning thread; rendering never initializes or changes its data. */
public final class HorseInspectionText {
    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z0-9_]+)%");
    private static final Pattern INSERTION = Pattern.compile("bhinspectiontoken[0-9]+end");
    private static final List<String> DEFAULT_LINES = List.of(
            "<dark_gray>────────────────────────</dark_gray>",
            "<gold><bold>%name%</bold></gold> <dark_gray>·</dark_gray> %gender%",
            "%health_line%",
            "%speed_line% <dark_gray>│</dark_gray> %jump_line%",
            "%growth_line%",
            "%trait_line%",
            "%neutered_line%",
            "%training_title%",
            "%riding_line%",
            "%brushing_line%",
            "%feeding_line%",
            "<dark_gray>────────────────────────</dark_gray>"
    );
    private final BetterHorses plugin;

    public HorseInspectionText(BetterHorses plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public Component render(Player viewer, AbstractHorse horse) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(horse, "horse");
        requireOwned(viewer, horse);
        LanguageManager language = plugin.getLang();
        FileConfiguration config = plugin.getConfig();
        PersistentDataContainer data = horse.getPersistentDataContainer();
        Map<String, Component> values = new LinkedHashMap<>();
        Function<String, Component> parse = text -> {
            requireOwned(viewer, horse);
            Component result = language.parse(viewer, text);
            // Placeholder expansions may teleport entities or reload the plugin.
            // Stop before the next live read if their ownership changed.
            requireOwned(viewer, horse);
            return result;
        };

        SupportedMountType type = SupportedMountType.fromEntity(horse).orElse(SupportedMountType.HORSE);
        String gender = data.getOrDefault(BetterHorseKeys.GENDER, PersistentDataType.STRING, "unknown");
        String genderKey = "male".equalsIgnoreCase(gender) ? "messages.gender-male"
                : "female".equalsIgnoreCase(gender) ? "messages.gender-female" : "horse-inspection.values.unknown";
        values.put("gender", localized(language, genderKey, "?", parse));
        values.put("gender_id", plain(gender));
        String name = horse.getCustomName();
        values.put("name", name == null || name.isBlank()
                ? parse.apply(type.getDisplayName(language, viewer)) : plain(name));
        values.put("mount_type", plain(type.name()));
        values.put("mount_name", parse.apply(type.getDisplayName(language, viewer)));
        String storedOwner = data.get(BetterHorseKeys.OWNER, PersistentDataType.STRING);
        AnimalTamer nativeOwner = horse.getOwner();
        String ownerId = nativeOwner == null ? storedOwner : nativeOwner.getUniqueId().toString();
        // UUID is immutable; do not ask a live owner in another region for entity data.
        String owner = nativeOwner == null ? storedOwner
                : nativeOwner instanceof Player ownerPlayer && !Bukkit.isOwnedByCurrentRegion(ownerPlayer)
                ? ownerId : nativeOwner.getName();
        if (owner == null || owner.isBlank()) {
            owner = ownerId;
        }
        values.put("owner", owner == null || owner.isBlank()
                ? localized(language, "horse-inspection.values.none", "None", parse) : plain(owner));
        values.put("owner_uuid", plain(ownerId));
        values.put("color", plain(horse instanceof Horse ordinary ? ordinary.getColor().name() : ""));
        values.put("style", plain(horse instanceof Horse ordinary ? ordinary.getStyle().name() : ""));

        double maximum = HorseStats.maxHealth(horse);
        double current = HorseStats.currentHealth(horse);
        double speed = HorseStats.speed(horse);
        double jump = HorseStats.jump(horse);
        values.put("current_health", number(current, 2));
        values.put("max_health", number(maximum, 2));
        values.put("health", number(maximum, 2));
        values.put("speed_raw", number(speed, 4));
        values.put("speed_blocks", number(speed * 42.16, 2));
        values.put("jump_raw", number(jump, 4));
        values.put("jump_blocks", number(jumpHeight(jump), 2));
        boolean blocks = "BLOCKS".equalsIgnoreCase(config.getString("stats.display-mode", "RAW"));
        values.put("speed", values.get(blocks ? "speed_blocks" : "speed_raw"));
        values.put("jump", values.get(blocks ? "jump_blocks" : "jump_raw"));
        values.put("health_line", formatted(language, "messages.lore-health", "Health: %value% / %max%",
                Map.of("value", values.get("current_health"), "max", values.get("max_health")), parse));
        values.put("speed_line", statLine(language, true, blocks, values.get("speed"), parse));
        values.put("jump_line", statLine(language, false, blocks, values.get("jump"), parse));

        Integer storedStage = data.get(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER);
        boolean customGrowth = MountConfig.isGrowthEnabled(config, type);
        int growthStage;
        int growthPercent;
        if (customGrowth) {
            growthStage = GrowthScale.normalize(storedStage == null ? (horse.isAdult() ? 10 : 1) : storedStage);
            growthPercent = growthStage * 10;
        } else {
            growthPercent = horse.isAdult() ? 100
                    : Math.min(99, (int) Math.round(Math.max(0.0, Math.min(100.0, (24000.0 + horse.getAge()) / 240.0))));
            growthStage = horse.isAdult() ? 10 : Math.min(9, Math.max(1, (int) Math.ceil(growthPercent / 10.0)));
        }
        values.put("growth_stage", plain(String.valueOf(growthStage)));
        values.put("growth_percent", plain(String.valueOf(growthPercent)));
        values.put("growth_line", formatted(language, "horse-inspection.values.growth",
                "Growth: %growth_percent%% <dark_gray>(%growth_stage%/10)</dark_gray>", values, parse));

        String trait = data.get(BetterHorseKeys.TRAIT, PersistentDataType.STRING);
        boolean hasTrait = trait != null && !trait.isBlank() && !trait.equalsIgnoreCase("none");
        Component traitName = localized(language, "horse-inspection.values.none", "None", parse);
        if (hasTrait) {
            String key = trait.toLowerCase(Locale.ROOT);
            traitName = key.matches("[a-z0-9_-]+") && language.getConfig().isString("traits." + key)
                    ? parse.apply(language.getConfig().getString("traits." + key))
                    : plain(Character.toUpperCase(trait.charAt(0)) + trait.substring(1));
        }
        values.put("trait", traitName);
        values.put("trait_line", hasTrait
                ? formatted(language, "messages.trait-line", "Trait: %trait%", Map.of("trait", traitName), parse)
                : Component.empty());
        boolean neutered = Byte.valueOf((byte) 1).equals(data.get(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE));
        values.put("neutered", localized(language, "horse-inspection.values." + (neutered ? "yes" : "no"),
                neutered ? "Yes" : "No", parse));
        values.put("neutered_line", neutered
                ? localized(language, "messages.lore-neutered", "⚠ Castrated – Cannot breed", parse)
                : Component.empty());
        addTraining(values, language, config, data, parse);

        List<String> lines = language.getConfig().isList("horse-inspection.lines")
                ? language.getConfig().getStringList("horse-inspection.lines") : DEFAULT_LINES;
        List<Component> rendered = new ArrayList<>();
        for (String line : lines) {
            Component result = renderTemplate(line, values, parse);
            if (!ChatColor.stripColor(LegacyComponentSerializer.legacySection().serialize(result)).isBlank()) rendered.add(result);
        }
        Component result = Component.empty().color(NamedTextColor.GRAY);
        for (Component line : rendered) {
            if (!result.children().isEmpty()) result = result.append(Component.newline());
            result = result.append(line);
        }
        return result;
    }

    private static void requireOwned(Player viewer, AbstractHorse horse) {
        if (!Bukkit.isOwnedByCurrentRegion(viewer) || !Bukkit.isOwnedByCurrentRegion(horse)) {
            throw new IllegalStateException("Horse inspection requires ownership of both viewer and mount");
        }
    }

    private static void addTraining(Map<String, Component> values, LanguageManager language,
                                    FileConfiguration config, PersistentDataContainer data,
                                    Function<String, Component> parse) {
        String[] categories = {"riding", "brushing", "feeding"};
        NamespacedKey[] keys = {BetterHorseKeys.TRAINING_RIDING_UNITS,
                BetterHorseKeys.TRAINING_BRUSHING_UNITS, BetterHorseKeys.TRAINING_FEEDING_UNITS};
        boolean visible = config.getBoolean("training.enabled", false) && config.getBoolean("training.lore.enabled", true);
        boolean hideComplete = config.getBoolean("training.lore.progress-bar.hide-on-complete", true);
        boolean allComplete = visible && hideComplete && config.getBoolean("training.lore.progress-bar.combine-all-complete", true);
        boolean anyEnabled = false;
        double[] percentages = new double[categories.length];
        for (int i = 0; i < categories.length; i++) {
            String category = categories[i];
            boolean enabled = config.getBoolean("training.categories." + category + ".enabled", true);
            anyEnabled |= enabled;
            percentages[i] = config.getBoolean("training.enabled", false) && enabled
                    ? trainingPercent(data.getOrDefault(keys[i], PersistentDataType.DOUBLE, 0.0),
                    config.getDouble("training.categories." + category + ".units-per-percent", 10.0)) : 0.0;
            allComplete &= !enabled || percentages[i] >= 100.0;
            values.put(category + "_percent", plain(String.valueOf(Math.round(percentages[i]))));
        }
        allComplete &= anyEnabled;
        Component complete = parse.apply(language.getConfig().getString("training-lore.progress-bar.filled-color", "&b")
                + language.getConfig().getString("training-lore.complete", "completed"));
        Component title = visible && anyEnabled
                ? localized(language, "training-lore.title", "&6Training", parse) : Component.empty();
        if (allComplete) title = title.append(Component.space()).append(complete);
        values.put("training_title", title);
        for (int i = 0; i < categories.length; i++) {
            String category = categories[i];
            Component line = Component.empty();
            if (visible && !allComplete && config.getBoolean("training.categories." + category + ".enabled", true)) {
                if (hideComplete && percentages[i] >= 100.0) {
                    line = localized(language, "training-lore.categories." + category + "-name",
                            "&7" + titleCase(category) + ":", parse).append(Component.space()).append(complete);
                } else {
                    line = formatted(language, "training-lore.categories." + category,
                            "&7" + titleCase(category) + ": %bar% &b%percent%%",
                            Map.of("bar", progressBar(language, config, percentages[i], parse),
                                    "percent", values.get(category + "_percent")), parse);
                }
            }
            values.put(category + "_line", line);
        }
    }

    private static Component progressBar(LanguageManager language, FileConfiguration config, double percent,
                                         Function<String, Component> parse) {
        int length = Math.max(5, Math.min(200, config.getInt("training.lore.progress-bar.length", 20)));
        int filled = (int) Math.round(percent / 100.0 * length);
        String filledChar = firstCharacter(language.getConfig().getString("training-lore.progress-bar.filled-char", "■"));
        String emptyChar = firstCharacter(language.getConfig().getString("training-lore.progress-bar.empty-char", "■"));
        return renderTemplate(language.getConfig().getString("training-lore.progress-bar.filled-color", "&b")
                        + "%filled%" + language.getConfig().getString("training-lore.progress-bar.empty-color", "&8") + "%empty%",
                Map.of("filled", Component.text(filledChar.repeat(filled)),
                        "empty", Component.text(emptyChar.repeat(length - filled))), parse);
    }

    private static String firstCharacter(String value) {
        return value == null || value.isEmpty() ? "■" : value.substring(0, value.offsetByCodePoints(0, 1));
    }

    private static String titleCase(String value) { return Character.toUpperCase(value.charAt(0)) + value.substring(1); }

    private static Component statLine(LanguageManager language, boolean speed, boolean blocks,
                                      Component value, Function<String, Component> parse) {
        String stat = speed ? "speed" : "jump";
        String fallback = titleCase(stat) + ": %value%" + (blocks ? (speed ? " b/s" : " blocks") : "");
        return formatted(language, "messages.lore-" + stat + (blocks ? "-blocks" : ""), fallback,
                Map.of("value", value, stat, value), parse);
    }

    private static Component formatted(LanguageManager language, String key, String fallback,
                                       Map<String, Component> values, Function<String, Component> parse) {
        return renderTemplate(language.getConfig().getString(key, fallback), values, parse);
    }

    private static Component localized(LanguageManager language, String key, String fallback, Function<String, Component> parse) {
        return parse.apply(language.getConfig().getString(key, fallback));
    }

    /** Insert components after parsing trusted formatting, so mount data never becomes markup or PAPI input. */
    static Component renderTemplate(String template, Map<String, Component> values, Function<String, Component> parse) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer protectedTemplate = new StringBuffer();
        Map<String, Component> insertions = new LinkedHashMap<>();
        while (matcher.find()) {
            Component value = values.get(matcher.group(1));
            if (value == null) continue;
            String token = "bhinspectiontoken" + insertions.size() + "end";
            insertions.put(token, value);
            matcher.appendReplacement(protectedTemplate, token);
        }
        matcher.appendTail(protectedTemplate);
        return parse.apply(protectedTemplate.toString()).replaceText(builder -> builder.match(INSERTION)
                .replacement((match, original) -> insertions.getOrDefault(match.group(), original.build())));
    }

    static double trainingPercent(double units, double unitsPerPercent) {
        double divisor = Double.isFinite(unitsPerPercent) ? Math.max(0.0001, unitsPerPercent) : 10.0;
        return Math.max(0.0, Math.min(100.0, finite(units) / divisor));
    }

    static double jumpHeight(double strength) {
        return -0.1817584952 * Math.pow(strength, 3) + 3.689713992 * Math.pow(strength, 2)
                + 2.128599134 * strength - 0.343930367;
    }

    private static double finite(double value) { return Double.isFinite(value) ? value : 0.0; }
    private static Component number(double value, int precision) {
        return plain(String.format(Locale.US, "%." + precision + "f", value));
    }
    private static Component plain(String value) {
        return Component.text(value == null ? "" : ChatColor.stripColor(value));
    }
}
