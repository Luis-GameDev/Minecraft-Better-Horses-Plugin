package me.luisgamedev.betterhorses.api;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.NamespacedKey;

/**
 * Centralizes the namespaced keys used by BetterHorses to store metadata on
 * entities and items. These keys are stable to ensure backwards compatibility
 * for horses created with previous versions of the plugin.
 */
public final class BetterHorseKeys {

    public static final NamespacedKey GENDER = key("gender");
    public static final NamespacedKey CURRENT_HEALTH = key("current_health");
    public static final NamespacedKey HEALTH = key("health");
    public static final NamespacedKey SPEED = key("speed");
    public static final NamespacedKey JUMP = key("jump");
    public static final NamespacedKey OWNER = key("owner");
    public static final NamespacedKey NAME = key("name");
    public static final NamespacedKey STYLE = key("style");
    public static final NamespacedKey COLOR = key("color");
    public static final NamespacedKey GROWTH_STAGE = key("growth_stage");
    public static final NamespacedKey MOUNT_TYPE = key("mount_type");
    public static final NamespacedKey TRAIT = key("trait");
    public static final NamespacedKey NEUTERED = key("neutered");
    public static final NamespacedKey COOLDOWN = key("cooldown");
    public static final NamespacedKey SADDLE = key("saddle");
    public static final NamespacedKey ARMOR = key("armor");
    public static final NamespacedKey ARMOR_DATA = key("armor_data");
    public static final NamespacedKey CHESTED = key("chested");
    public static final NamespacedKey CHEST_CONTENTS = key("chest_contents");
    public static final NamespacedKey BASE_HEALTH = key("base_health");
    public static final NamespacedKey BASE_SPEED = key("base_speed");
    public static final NamespacedKey BASE_JUMP = key("base_jump");
    public static final NamespacedKey TRAINING_RIDING_UNITS = key("training_riding_units");
    public static final NamespacedKey TRAINING_BRUSHING_UNITS = key("training_brushing_units");
    public static final NamespacedKey TRAINING_FEEDING_UNITS = key("training_feeding_units");
    public static final NamespacedKey TRAINING_BRUSH_COOLDOWN = key("training_brush_cooldown");
    public static final NamespacedKey TRAINING_FEED_COOLDOWN = key("training_feed_cooldown");
    public static final NamespacedKey TEXTURE_CUSTOM_MODEL_DATA = key("texture_custom_model_data");
    public static final NamespacedKey TEXTURE_ITEM_MODEL = key("texture_item_model");
    public static final NamespacedKey TEXTURE_CIT_STRING = key("texture_cit_string");
    public static final NamespacedKey TEXTURE_MODEL_STRING = key("texture_model_string");
    public static final NamespacedKey UNDEAD_SKELETON = key("undead_skeleton");
    public static final NamespacedKey UNDEAD_ORIGINAL_TYPE = key("undead_original_type");
    public static final NamespacedKey UNDEAD_ORIGINAL_HEALTH = key("undead_original_health");
    public static final NamespacedKey UNDEAD_ORIGINAL_SPEED = key("undead_original_speed");
    public static final NamespacedKey UNDEAD_ORIGINAL_JUMP = key("undead_original_jump");
    public static final NamespacedKey UNDEAD_ORIGINAL_COLOR = key("undead_original_color");
    public static final NamespacedKey UNDEAD_ORIGINAL_STYLE = key("undead_original_style");
    public static final NamespacedKey UNDEAD_ARMOR_DATA = key("undead_armor_data");

    private static NamespacedKey key(String key) {
        return new NamespacedKey(BetterHorses.getInstance(), key);
    }
}
