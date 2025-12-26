package me.luisgamedev.betterhorses.utils;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Camel;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.entity.ZombieHorse;

import java.util.Arrays;
import java.util.Optional;

public enum SupportedMountType {
    HORSE(EntityType.HORSE, Horse.class, "horse", true),
    SKELETON_HORSE(EntityType.SKELETON_HORSE, SkeletonHorse.class, "skeleton-horses", false),
    ZOMBIE_HORSE(EntityType.ZOMBIE_HORSE, ZombieHorse.class, "zombie-horses", false),
    CAMEL(EntityType.CAMEL, Camel.class, "camels", false);

    private final EntityType entityType;
    private final Class<? extends AbstractHorse> entityClass;
    private final String configKey;
    private final boolean alwaysEnabled;

    SupportedMountType(EntityType entityType, Class<? extends AbstractHorse> entityClass, String configKey, boolean alwaysEnabled) {
        this.entityType = entityType;
        this.entityClass = entityClass;
        this.configKey = configKey;
        this.alwaysEnabled = alwaysEnabled;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public String getConfigKey() {
        return configKey;
    }

    public boolean isEnabled(FileConfiguration config) {
        if (alwaysEnabled) return true;
        return config.getBoolean("settings.mount-types." + configKey, false);
    }

    public AbstractHorse spawn(Location location) throws IllegalArgumentException {
        return location.getWorld().spawn(location, entityClass);
    }

    public static Optional<SupportedMountType> fromEntity(Entity entity) {
        if (!(entity instanceof AbstractHorse)) return Optional.empty();
        return Arrays.stream(values())
                .filter(type -> type.entityClass.isInstance(entity))
                .findFirst();
    }

    public static Optional<SupportedMountType> fromName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        try {
            EntityType entityType = EntityType.valueOf(name);
            return Arrays.stream(values())
                    .filter(type -> type.entityType == entityType)
                    .findFirst();
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static SupportedMountType fromNameOrDefault(String name) {
        return fromName(name).orElse(SupportedMountType.HORSE);
    }

    public static boolean isSupported(Entity entity) {
        return fromEntity(entity)
                .map(type -> type.isEnabled(BetterHorses.getInstance().getConfig()))
                .orElse(false);
    }
}
