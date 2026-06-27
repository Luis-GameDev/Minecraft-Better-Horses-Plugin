package me.luisgamedev.betterhorses.api;

import javax.annotation.Nullable;

/**
 * Texture/model references that can be applied to BetterHorses horse items.
 * Empty strings, null values, and non-positive custom model data values are
 * treated as not configured.
 */
public final class HorseItemTextureData {

    private final @Nullable Integer customModelData;
    private final @Nullable String itemModel;
    private final @Nullable String citString;
    private final @Nullable String modelString;

    public HorseItemTextureData(@Nullable Integer customModelData, @Nullable String itemModel, @Nullable String citString, @Nullable String modelString) {
        this.customModelData = normalizeCustomModelData(customModelData);
        this.itemModel = normalizeString(itemModel);
        this.citString = normalizeString(citString);
        this.modelString = normalizeString(modelString);
    }

    public static HorseItemTextureData empty() {
        return new HorseItemTextureData(null, null, null, null);
    }

    public @Nullable Integer getCustomModelData() {
        return customModelData;
    }

    public @Nullable String getItemModel() {
        return itemModel;
    }

    public @Nullable String getCitString() {
        return citString;
    }

    public @Nullable String getModelString() {
        return modelString;
    }

    public boolean isEmpty() {
        return customModelData == null && itemModel == null && citString == null && modelString == null;
    }

    private static @Nullable Integer normalizeCustomModelData(@Nullable Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private static @Nullable String normalizeString(@Nullable String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
