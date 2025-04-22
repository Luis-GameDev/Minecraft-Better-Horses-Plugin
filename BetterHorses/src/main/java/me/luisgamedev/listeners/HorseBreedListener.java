package me.luisgamedev.listeners;

import me.luisgamedev.BetterHorses;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Horse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;

public class HorseBreedListener implements Listener {

    private final NamespacedKey genderKey = new NamespacedKey(JavaPlugin.getPlugin(BetterHorses.class), "gender");

    @EventHandler
    public void onHorseBreed(EntityBreedEvent event) {
        if (!(event.getEntity() instanceof Horse child)) return;
        if (!(event.getFather() instanceof Horse father)) return;
        if (!(event.getMother() instanceof Horse mother)) return;

        String gender1 = father.getPersistentDataContainer().get(genderKey, PersistentDataType.STRING);
        String gender2 = mother.getPersistentDataContainer().get(genderKey, PersistentDataType.STRING);

        if (gender1 == null || gender2 == null || gender1.equalsIgnoreCase(gender2)) {
            event.setCancelled(true);
            return;
        }

        FileConfiguration config = BetterHorses.getInstance().getConfig();
        double mutationHealth = config.getDouble("mutation-factor.health");
        double mutationSpeed = config.getDouble("mutation-factor.speed");
        double mutationJump = config.getDouble("mutation-factor.jump");
        double maxHealth = config.getDouble("max-stats.health");
        double maxSpeed = config.getDouble("max-stats.speed");
        double maxJump = config.getDouble("max-stats.jump");

        double childHealth = mutate(avg(getHealth(father), getHealth(mother)), mutationHealth, maxHealth);
        double childSpeed = mutate(avg(getSpeed(father), getSpeed(mother)), mutationSpeed, maxSpeed);
        double childJump = mutate(avg(getJump(father), getJump(mother)), mutationJump, maxJump);

        setHealth(child, childHealth);
        setSpeed(child, childSpeed);
        setJump(child, childJump);

        String gender = Math.random() < 0.5 ? "male" : "female";
        child.getPersistentDataContainer().set(genderKey, PersistentDataType.STRING, gender);
    }

    private double avg(double a, double b) {
        return (a + b) / 2.0;
    }

    private double mutate(double base, double factor, double max) {
        double mutation = (Math.random() * 2 - 1) * factor;
        return Math.min(base + mutation, max);
    }

    private double getHealth(Horse horse) {
        return horse.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue();
    }

    private double getSpeed(Horse horse) {
        return horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getBaseValue();
    }

    private double getJump(Horse horse) {
        return horse.getAttribute(Attribute.HORSE_JUMP_STRENGTH).getBaseValue();
    }

    private void setHealth(Horse horse, double value) {
        AttributeInstance attr = horse.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        attr.setBaseValue(value);
        horse.setHealth(value);
    }

    private void setSpeed(Horse horse, double value) {
        horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(value);
    }

    private void setJump(Horse horse, double value) {
        horse.getAttribute(Attribute.HORSE_JUMP_STRENGTH).setBaseValue(value);
    }
}
