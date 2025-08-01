package me.luisgamedev.listeners;

import me.luisgamedev.BetterHorses;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;

import java.util.Random;

public class HorseSpawnListener implements Listener {

    private static final String[] GENDERS = {"male", "female"};
    private final Random random = new Random();
    private final NamespacedKey genderKey = new NamespacedKey(JavaPlugin.getPlugin(BetterHorses.class), "gender");
    private final NamespacedKey growthKey = new NamespacedKey(JavaPlugin.getPlugin(BetterHorses.class), "growth_stage");

    @EventHandler
    public void onHorseSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.HORSE) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;

        Horse horse = (Horse) event.getEntity();

        if (BetterHorses.getInstance().getConfig().getBoolean("horse-growth-settings.enabled")) {
            horse.setAgeLock(true);
        }

        // Set gender
        String gender = GENDERS[random.nextInt(GENDERS.length)];
        PersistentDataContainer data = horse.getPersistentDataContainer();
        data.set(genderKey, PersistentDataType.STRING, gender);

        int stage = 10;

        if (BetterHorses.getInstance().getConfig().getBoolean("horse-growth-settings.enabled")) {
            // Set random growth stage
            stage = random.nextInt(11); // 0–10

            // Set scale
            double minScale = 0.7;
            double maxScale = BetterHorses.getInstance().getConfig().getDouble("horse-growth-settings.max-size", 1.3);
            double scale = minScale + ((maxScale - minScale) / 10.0) * stage;

            try {
                AttributeInstance attr = horse.getAttribute(Attribute.valueOf("SCALE"));
                if (attr != null) {
                    attr.setBaseValue(scale);
                }
            } catch (Exception ignored) {
                // SCALE not supported – fallback for older Paper versions
            }

            // Set to adult if threshold met
            int threshold = BetterHorses.getInstance().getConfig().getInt("horse-growth-settings.ride-and-breed-threshhold", 7);
            if (stage >= threshold && !horse.isAdult()) {
                horse.setAdult();
            } else if (stage < threshold) {
                horse.setBaby();
            }
        }

        data.set(growthKey, PersistentDataType.INTEGER, stage);
    }
}
