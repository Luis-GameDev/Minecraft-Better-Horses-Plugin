package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.events.BetterHorseBreedEvent;
import me.luisgamedev.betterhorses.utils.MountConfig;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Bukkit;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityEnterLoveModeEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;

public class HorseBreedListener implements Listener {

    @EventHandler
    public void onHorseEnterLoveMode(EntityEnterLoveModeEvent event) {
        if (!(event.getEntity() instanceof AbstractHorse horse)) return;

        SupportedMountType mountType = SupportedMountType.fromEntity(horse).orElse(null);
        if (mountType == null) return;

        FileConfiguration config = BetterHorses.getInstance().getConfig();
        if (!mountType.isEnabled(config)) return;

        if (isNeutered(horse) || isOnBreedingCooldown(horse, config)) {
            event.setCancelled(true);
            return;
        }

        if (horse.getAge() != 0) {
            horse.setAge(0);
        }
    }

    @EventHandler
    public void onHorseBreed(EntityBreedEvent event) {
        if (!(event.getEntity() instanceof AbstractHorse child)) return;
        if (!(event.getFather() instanceof AbstractHorse father)) return;
        if (!(event.getMother() instanceof AbstractHorse mother)) return;

        SupportedMountType childType = SupportedMountType.fromEntity(child).orElse(null);
        SupportedMountType fatherType = SupportedMountType.fromEntity(father).orElse(null);
        SupportedMountType motherType = SupportedMountType.fromEntity(mother).orElse(null);

        FileConfiguration config = BetterHorses.getInstance().getConfig();

        if (childType == null || fatherType == null || motherType == null) return;
        if (!childType.equals(fatherType) || !childType.equals(motherType)) return;
        if (!childType.isEnabled(config)) return;

        father.setAge(0);
        mother.setAge(0);

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

        double childHealth = mutate(avg(getHealth(father), getHealth(mother)), mutationHealth, maxHealth);
        double childSpeed = mutate(avg(getSpeed(father), getSpeed(mother)), mutationSpeed, maxSpeed);
        double childJump = mutate(avg(getJump(father), getJump(mother)), mutationJump, maxJump);

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
                        selectedTrait = trait.toLowerCase();
                        break;
                    }
                }
            }
        }

        BetterHorseBreedEvent betterBreedEvent = new BetterHorseBreedEvent(child, father, mother, childHealth, childSpeed, childJump, gender, selectedTrait);
        Bukkit.getPluginManager().callEvent(betterBreedEvent);
        if (betterBreedEvent.isCancelled()) {
            event.setCancelled(true);
            return;
        }

        childHealth = betterBreedEvent.getHealth();
        childSpeed = betterBreedEvent.getSpeed();
        childJump = betterBreedEvent.getJump();
        gender = betterBreedEvent.getGender();
        selectedTrait = betterBreedEvent.getTrait();

        setHealth(child, childHealth);
        setSpeed(child, childSpeed);
        setJump(child, childJump);

        child.getPersistentDataContainer().set(BetterHorseKeys.GENDER, PersistentDataType.STRING, gender);
        child.getPersistentDataContainer().set(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER, 1);

        if (MountConfig.isGrowthEnabled(config, childType)) {
            child.setAgeLock(true);
        }

        if (selectedTrait != null && !selectedTrait.isBlank()) {
            child.getPersistentDataContainer().set(BetterHorseKeys.TRAIT, PersistentDataType.STRING, selectedTrait.toLowerCase());
        }

        // Apply cooldown to both parents
        if (!config.getBoolean("settings.male-ignore-cooldown", true)) {
            dataFather.set(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG, now);
        }
        dataMother.set(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG, now);

        father.setAge(0);
        mother.setAge(0);
    }

    private boolean isOnBreedingCooldown(AbstractHorse horse, FileConfiguration config) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        Long last = data.get(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG);
        if (last == null) return false;

        String gender = getGender(horse);
        if ("male".equalsIgnoreCase(gender) && config.getBoolean("settings.male-ignore-cooldown")) {
            return false;
        }

        long cooldownMillis = config.getLong("settings.breeding-cooldown", 0) * 1000L;
        return cooldownMillis > 0 && (System.currentTimeMillis() - last) < cooldownMillis;
    }

    private String getGender(AbstractHorse horse) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        if (!data.has(BetterHorseKeys.GENDER, PersistentDataType.STRING)) {
            String gender = Math.random() < 0.5 ? "male" : "female";
            data.set(BetterHorseKeys.GENDER, PersistentDataType.STRING, gender);
            return gender;
        }
        return data.getOrDefault(BetterHorseKeys.GENDER, PersistentDataType.STRING, "unknown");
    }

    private boolean isNeutered(AbstractHorse horse) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        return data.has(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE) && data.get(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE) == (byte) 1;
    }

    private double avg(double a, double b) {
        return (a + b) / 2.0;
    }

    private double mutate(double base, double factor, double max) {
        double mutation = (Math.random() * 2 - 1) * factor;
        return Math.min(base + mutation, max);
    }

    private double getHealth(AbstractHorse horse) {
        AttributeInstance attribute = horse.getAttribute(AttributeResolver.generic("MAX_HEALTH"));
        return attribute != null ? attribute.getBaseValue() : 0.0;
    }

    private double getSpeed(AbstractHorse horse) {
        AttributeInstance attribute = horse.getAttribute(AttributeResolver.generic("MOVEMENT_SPEED"));
        return attribute != null ? attribute.getBaseValue() : 0.0;
    }

    private double getJump(AbstractHorse horse) {
        AttributeInstance attribute = horse.getAttribute(Attribute.valueOf("HORSE_JUMP_STRENGTH"));
        return attribute != null ? attribute.getBaseValue() : 0.0;
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
        AttributeInstance attribute = horse.getAttribute(Attribute.valueOf("HORSE_JUMP_STRENGTH"));
        if (attribute != null) {
            attribute.setBaseValue(value);
        }
    }
}
