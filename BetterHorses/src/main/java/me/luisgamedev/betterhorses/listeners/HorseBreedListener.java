package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.events.BetterHorseBreedEvent;
import me.luisgamedev.betterhorses.utils.MountConfig;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;

public class HorseBreedListener implements Listener {

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

        long cooldownSeconds = config.getLong("settings.breeding-cooldown", 0);
        long cooldownMillis = cooldownSeconds * 1000L;
        long now = System.currentTimeMillis();

        PersistentDataContainer dataFather = father.getPersistentDataContainer();
        PersistentDataContainer dataMother = mother.getPersistentDataContainer();

        // Cancel if either parent has cooldown that hasn't expired
        if (dataFather.has(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG) && !config.getBoolean("settings.male-ignore-cooldown")) {
            long last = dataFather.get(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG);
            Double cooldownLeft = (cooldownMillis - (now - last)) / 1000.0;
            if (cooldownLeft > 0) {
                event.setCancelled(true);
                return;
            }
        }
        if (dataMother.has(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG)) {
            long last = dataMother.get(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG);
            Double cooldownLeft = (cooldownMillis - (now - last)) / 1000.0;
            if (cooldownLeft > 0) {
                event.setCancelled(true);
                return;
            }
        }

        String gender1 = getGender(father);
        String gender2 = getGender(mother);

        boolean motherNeutered = isNeutered(mother);
        boolean fatherNeutered = isNeutered(father);

        if (motherNeutered || fatherNeutered) {
            event.setCancelled(true);
            return;
        }

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
        dataFather.set(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG, now);
        dataMother.set(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG, now);

        father.setAge(0);
        mother.setAge(0);
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
        AttributeInstance attribute = horse.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attribute != null ? attribute.getBaseValue() : 0.0;
    }

    private double getSpeed(AbstractHorse horse) {
        AttributeInstance attribute = horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        return attribute != null ? attribute.getBaseValue() : 0.0;
    }

    private double getJump(AbstractHorse horse) {
        AttributeInstance attribute = horse.getAttribute(Attribute.valueOf("HORSE_JUMP_STRENGTH"));
        return attribute != null ? attribute.getBaseValue() : 0.0;
    }

    private void setHealth(AbstractHorse horse, double value) {
        AttributeInstance attr = horse.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(value);
        }
        horse.setHealth(value);
    }

    private void setSpeed(AbstractHorse horse, double value) {
        AttributeInstance attribute = horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
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
