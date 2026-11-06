package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.events.BetterHorseBreedEvent;
import me.luisgamedev.betterhorses.growing.GrowthScale;
import me.luisgamedev.betterhorses.growing.HorseSize;
import me.luisgamedev.betterhorses.utils.HorseStats;
import me.luisgamedev.betterhorses.utils.HorseDataValidator;
import me.luisgamedev.betterhorses.training.TrainingManager;
import me.luisgamedev.betterhorses.utils.MountConfig;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Bukkit;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;

public class HorseBreedListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHorseEnterLoveMode(EntityEnterLoveModeEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof AbstractHorse horse)) return;

        SupportedMountType mountType = SupportedMountType.fromEntity(horse).orElse(null);
        if (mountType == null) return;

        FileConfiguration config = BetterHorses.getInstance().getConfig();
        if (!mountType.isEnabled(config)) return;

        HumanEntity feeder = event.getHumanEntity();
        if (feeder instanceof Player player && !player.hasPermission(PermissionUtils.BREED)) {
            event.setCancelled(true);
            return;
        }

        if (isNeutered(horse) || isOnBreedingCooldown(horse, config, feeder instanceof Player player ? player : null)) {
            event.setCancelled(true);
            return;
        }

        if (horse.getAge() != 0) {
            horse.setAge(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHorseBreed(EntityBreedEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof AbstractHorse child)) return;
        SupportedMountType sizeType = SupportedMountType.fromEntity(child).orElse(null);
        FileConfiguration sizeConfig = BetterHorses.getInstance().getConfig();
        if (sizeType != null && sizeType.isEnabled(sizeConfig)) {
            // A newborn is a new individual, even if another plugin copied parental data.
            child.getPersistentDataContainer().remove(BetterHorseKeys.RANDOM_SIZE_MULTIPLIER);
            HorseSize.applyWithoutGrowth(child, sizeConfig);
        }
        if (!(event.getFather() instanceof AbstractHorse father)) return;
        if (!(event.getMother() instanceof AbstractHorse mother)) return;

        SupportedMountType childType = SupportedMountType.fromEntity(child).orElse(null);
        SupportedMountType fatherType = SupportedMountType.fromEntity(father).orElse(null);
        SupportedMountType motherType = SupportedMountType.fromEntity(mother).orElse(null);

        FileConfiguration config = BetterHorses.getInstance().getConfig();

        if (childType == null || fatherType == null || motherType == null) return;
        if (!childType.isEnabled(config)) return;
        boolean crossbreed = !childType.equals(fatherType) || !childType.equals(motherType);

        Player breeder = event.getBreeder() instanceof Player player ? player : null;
        if (breeder != null && !breeder.hasPermission(PermissionUtils.BREED)) {
            event.setCancelled(true);
            return;
        }

        if (isNeutered(father) || isNeutered(mother)
                || isOnBreedingCooldown(father, config, breeder) || isOnBreedingCooldown(mother, config, breeder)) {
            event.setCancelled(true);
            return;
        }

        long now = System.currentTimeMillis();

        PersistentDataContainer dataFather = father.getPersistentDataContainer();
        PersistentDataContainer dataMother = mother.getPersistentDataContainer();

        String gender1 = getGender(father);
        String gender2 = getGender(mother);

        boolean allowSameGender = config.getBoolean("settings.allow-same-gender-breeding", false);
        if (!allowSameGender && gender1.equalsIgnoreCase(gender2)) {
            event.setCancelled(true);
            return;
        }

        double mutationHealth = MountConfig.getMutationFactor(config, childType, "health");
        double mutationSpeed = MountConfig.getMutationFactor(config, childType, "speed");
        double mutationJump = MountConfig.getMutationFactor(config, childType, "jump");
        double maxHealth = MountConfig.getMaxStat(config, childType, "health");
        double maxSpeed = MountConfig.getMaxStat(config, childType, "speed");
        double maxJump = MountConfig.getMaxStat(config, childType, "jump");

        // The vanilla child already contains crossbreed inheritance. Shared policy and
        // API hooks still apply, without replacing those stats with same-type genetics.
        double childHealth = crossbreed ? nativeBase(child, AttributeResolver.generic("MAX_HEALTH"), getHealth(child)) : mutate(avg(getHealth(father), getHealth(mother)), mutationHealth, maxHealth);
        double childSpeed = crossbreed ? nativeBase(child, AttributeResolver.generic("MOVEMENT_SPEED"), getSpeed(child)) : mutate(avg(getSpeed(father), getSpeed(mother)), mutationSpeed, maxSpeed);
        double childJump = crossbreed ? nativeBase(child, AttributeResolver.horseJumpStrength(), getJump(child)) : mutate(avg(getJump(father), getJump(mother)), mutationJump, maxJump);

        String gender = Math.random() < 0.5 ? "male" : "female";
        String selectedTrait = null;

        if (config.getBoolean("traits.enabled")) {
            ConfigurationSection traitsSection = config.getConfigurationSection("traits");
            if (traitsSection != null) {
                Set<String> traits = traitsSection.getKeys(false);
                for (String trait : traits) {
                    if (trait.equals("enabled")) continue;

                    ConfigurationSection tSec = traitsSection.getConfigurationSection(trait);
                    if (tSec == null || !tSec.getBoolean("enabled", false)) continue;

                    double chance = tSec.getDouble("chance", 0);
                    if (Math.random() < chance) {
                        String candidateTrait = trait.toLowerCase();
                        if (breeder == null || PermissionUtils.canReceiveTrait(breeder, candidateTrait)) {
                            selectedTrait = candidateTrait;
                        }
                        break;
                    }
                }
            }
        }

        BetterHorseBreedEvent betterBreedEvent = new BetterHorseBreedEvent(child, father, mother, childHealth, childSpeed, childJump, gender, selectedTrait);
        Bukkit.getPluginManager().callEvent(betterBreedEvent);
        // API listeners may teleport participants, revoke eligibility or cancel the birth.
        // Ownership must be checked before any live read after their callbacks.
        if (!owns(child) || !owns(father) || !owns(mother) || (breeder != null && !owns(breeder))) {
            event.setCancelled(true);
            return;
        }
        config = BetterHorses.getInstance().getConfig();
        if (event.isCancelled() || betterBreedEvent.isCancelled() || child.isDead() || father.isDead() || mother.isDead()
                || !father.isValid() || !mother.isValid()
                || !WorldAccessPolicy.isEnabled(child.getWorld()) || !childType.isEnabled(config) || (breeder != null && !breeder.hasPermission(PermissionUtils.BREED))
                || isNeutered(father) || isNeutered(mother)
                || isOnBreedingCooldown(father, config, breeder) || isOnBreedingCooldown(mother, config, breeder)
                || (!config.getBoolean("settings.allow-same-gender-breeding", false)
                    && getGender(father).equalsIgnoreCase(getGender(mother)))) {
            event.setCancelled(true);
            return;
        }

        childHealth = betterBreedEvent.getHealth();
        childSpeed = betterBreedEvent.getSpeed();
        childJump = betterBreedEvent.getJump();
        gender = betterBreedEvent.getGender();
        selectedTrait = betterBreedEvent.getTrait();

        if (!HorseDataValidator.isValid(childHealth, childHealth, childSpeed, childJump, gender, 1)) {
            event.setCancelled(true);
            return;
        }
        setHealth(child, childHealth);
        setSpeed(child, childSpeed);
        setJump(child, childJump);
        PersistentDataContainer childData = child.getPersistentDataContainer();
        childData.set(BetterHorseKeys.HEALTH, PersistentDataType.DOUBLE, childHealth);
        childData.set(BetterHorseKeys.BASE_HEALTH, PersistentDataType.DOUBLE, childHealth);
        childData.set(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE, childSpeed);
        childData.set(BetterHorseKeys.BASE_SPEED, PersistentDataType.DOUBLE, childSpeed);
        childData.set(BetterHorseKeys.JUMP, PersistentDataType.DOUBLE, childJump);
        childData.set(BetterHorseKeys.BASE_JUMP, PersistentDataType.DOUBLE, childJump);
        childData.set(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING, childType.getEntityType().name());
        childData.set(BetterHorseKeys.GENDER, PersistentDataType.STRING, gender.toLowerCase(java.util.Locale.ROOT));

        if (MountConfig.isGrowthEnabled(config, childType)) {
            child.getPersistentDataContainer().set(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER, 1);
            int threshold = GrowthScale.normalize(config.getInt("horse-growth-settings.ride-and-breed-threshhold", 7));
            if (threshold == 1) child.setAdult(); else child.setBaby();
            child.setAgeLock(!child.isAdult());
            HorseSize.apply(child, GrowthScale.forStageUnclamped(1,
                    config.getDouble("horse-growth-settings.max-size", 1.3), child.isAdult(),
                    GrowthScale.babyScale(childType.name(), Bukkit.getBukkitVersion())), config);
        }

        if (selectedTrait != null && !selectedTrait.isBlank()) {
            child.getPersistentDataContainer().set(BetterHorseKeys.TRAIT, PersistentDataType.STRING, selectedTrait.toLowerCase());
        }

        TrainingManager.recalculateAndApplyBonuses(child);

        // Bukkit's parent roles do not identify the stored genders. Record both
        // timestamps so item conversion preserves them; cooldown checks apply exemptions.
        dataFather.set(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG, now);
        dataMother.set(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG, now);

        father.setAge(0);
        mother.setAge(0);
    }

    private boolean owns(org.bukkit.entity.Entity entity) { return Bukkit.isOwnedByCurrentRegion(entity); }

    private boolean isOnBreedingCooldown(AbstractHorse horse, FileConfiguration config, Player breeder) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        Long last = data.get(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG);
        if (last == null) return false;
        if (breeder != null && breeder.hasPermission(PermissionUtils.COOLDOWN_BYPASS)) return false;

        String gender = getGender(horse);
        if ("male".equalsIgnoreCase(gender) && config.getBoolean("settings.male-ignore-cooldown")) {
            return false;
        }

        long seconds = config.getLong("settings.breeding-cooldown", 0);
        long cooldownMillis = seconds <= 0L ? 0L : seconds > Long.MAX_VALUE / 1000L ? Long.MAX_VALUE : seconds * 1000L;
        return cooldownMillis > 0 && (System.currentTimeMillis() - last) < cooldownMillis;
    }

    private String getGender(AbstractHorse horse) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        if (!data.has(BetterHorseKeys.GENDER, PersistentDataType.STRING)) {
            String gender = Math.random() < 0.5 ? "male" : "female";
            data.set(BetterHorseKeys.GENDER, PersistentDataType.STRING, gender);
            BetterHorses.getInstance().refreshMountShaders(horse);
            return gender;
        }
        return data.getOrDefault(BetterHorseKeys.GENDER, PersistentDataType.STRING, "unknown");
    }

    private boolean isNeutered(AbstractHorse horse) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        return data.has(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE) && data.get(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE) == (byte) 1;
    }

    private double nativeBase(AbstractHorse horse, Attribute attribute, double fallback) {
        AttributeInstance instance = horse.getAttribute(attribute);
        double value = instance == null ? fallback : instance.getBaseValue();
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }

    private double avg(double a, double b) {
        return (a + b) / 2.0;
    }

    private double mutate(double base, double factor, double max) {
        double mutation = (Math.random() * 2 - 1) * factor;
        double result = base + mutation;
        if (!Double.isFinite(result)) return Math.max(0.001, Math.min(base, max));
        return Math.max(0.001, Math.min(result, max));
    }

    private double getHealth(AbstractHorse horse) {
        return HorseStats.maxHealth(horse);
    }

    private double getSpeed(AbstractHorse horse) {
        return HorseStats.speed(horse);
    }

    private double getJump(AbstractHorse horse) {
        return HorseStats.jump(horse);
    }

    private void setHealth(AbstractHorse horse, double value) {
        AttributeInstance attr = horse.getAttribute(AttributeResolver.generic("MAX_HEALTH"));
        if (attr != null) {
            attr.setBaseValue(value);
        }
        horse.setHealth(value);
    }

    private void setSpeed(AbstractHorse horse, double value) {
        AttributeInstance attribute = horse.getAttribute(AttributeResolver.generic("MOVEMENT_SPEED"));
        if (attribute != null) {
            attribute.setBaseValue(value);
        }
    }

    private void setJump(AbstractHorse horse, double value) {
        AttributeInstance attribute = horse.getAttribute(AttributeResolver.horseJumpStrength());
        if (attribute != null) {
            attribute.setBaseValue(value);
        }
    }
}
