package me.luisgamedev.betterhorses.api;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.training.TrainingManager;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import me.luisgamedev.betterhorses.utils.HorseStats;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.Objects;

/**
 * Wrapper around an in-world BetterHorses mount to simplify reading and
 * writing its custom stats while keeping persistent data synchronized.
 */
public final class BetterHorse {

    private final AbstractHorse handle;

    public BetterHorse(AbstractHorse handle) {
        this.handle = handle;
    }

    public AbstractHorse getHandle() {
        return handle;
    }

    public double getMaxHealth() {
        return HorseStats.maxHealth(handle);
    }

    public void setMaxHealth(double value) {
        TrainingManager.setBaseStat(handle, AttributeResolver.generic("MAX_HEALTH"),
                BetterHorseKeys.HEALTH, BetterHorseKeys.BASE_HEALTH, value);
    }

    public double getSpeed() {
        return HorseStats.speed(handle);
    }

    public void setSpeed(double value) {
        TrainingManager.setBaseStat(handle, AttributeResolver.generic("MOVEMENT_SPEED"),
                BetterHorseKeys.SPEED, BetterHorseKeys.BASE_SPEED, value);
    }

    public double getJump() {
        return HorseStats.jump(handle);
    }

    public void setJump(double value) {
        TrainingManager.setBaseStat(handle, AttributeResolver.horseJumpStrength(),
                BetterHorseKeys.JUMP, BetterHorseKeys.BASE_JUMP, value);
    }

    public Optional<String> getTrait() {
        PersistentDataContainer data = handle.getPersistentDataContainer();
        return Optional.ofNullable(data.get(BetterHorseKeys.TRAIT, PersistentDataType.STRING));
    }

    public void setTrait(String trait) {
        PersistentDataContainer data = handle.getPersistentDataContainer();
        String previous = data.get(BetterHorseKeys.TRAIT, PersistentDataType.STRING);
        String normalized = trait == null || trait.isBlank() ? null : trait.toLowerCase();
        if (trait == null || trait.isBlank()) {
            data.remove(BetterHorseKeys.TRAIT);
        } else {
            data.set(BetterHorseKeys.TRAIT, PersistentDataType.STRING, normalized);
        }
        if (!Objects.equals(previous, normalized)) refreshShaders();
    }

    public Optional<Integer> getGrowthStage() {
        PersistentDataContainer data = handle.getPersistentDataContainer();
        return Optional.ofNullable(data.get(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER));
    }

    public void setGrowthStage(int stage) {
        PersistentDataContainer data = handle.getPersistentDataContainer();
        Integer previous = data.get(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER);
        data.set(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER, stage);
        if (previous == null || previous != stage) refreshShaders();
    }

    private void refreshShaders() {
        BetterHorses plugin = BetterHorses.getInstance();
        if (plugin != null) plugin.refreshMountShaders(handle);
    }

    public HorseItemTextureData getTextureData() {
        PersistentDataContainer data = handle.getPersistentDataContainer();
        return new HorseItemTextureData(
                data.get(BetterHorseKeys.TEXTURE_CUSTOM_MODEL_DATA, PersistentDataType.INTEGER),
                data.get(BetterHorseKeys.TEXTURE_ITEM_MODEL, PersistentDataType.STRING),
                data.get(BetterHorseKeys.TEXTURE_CIT_STRING, PersistentDataType.STRING),
                data.get(BetterHorseKeys.TEXTURE_MODEL_STRING, PersistentDataType.STRING)
        );
    }

    public void setTextureData(HorseItemTextureData textureData) {
        PersistentDataContainer data = handle.getPersistentDataContainer();
        HorseItemTextureData normalized = textureData == null ? HorseItemTextureData.empty() : textureData;
        setOrRemove(data, BetterHorseKeys.TEXTURE_CUSTOM_MODEL_DATA, PersistentDataType.INTEGER, normalized.getCustomModelData());
        setOrRemove(data, BetterHorseKeys.TEXTURE_ITEM_MODEL, PersistentDataType.STRING, normalized.getItemModel());
        setOrRemove(data, BetterHorseKeys.TEXTURE_CIT_STRING, PersistentDataType.STRING, normalized.getCitString());
        setOrRemove(data, BetterHorseKeys.TEXTURE_MODEL_STRING, PersistentDataType.STRING, normalized.getModelString());
    }

    private <T> void setOrRemove(PersistentDataContainer data, NamespacedKey key, PersistentDataType<?, T> type, T value) {
        if (value == null) {
            data.remove(key);
        } else {
            data.set(key, type, value);
        }
    }

}
