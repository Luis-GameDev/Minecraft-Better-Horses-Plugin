package me.luisgamedev.listeners;

import me.luisgamedev.BetterHorses;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;

import java.util.Random;

public class HorseSpawnListener implements Listener {

    private static final String[] GENDERS = {"male", "female"};
    private final Random random = new Random();
    private final NamespacedKey genderKey = new NamespacedKey(JavaPlugin.getPlugin(BetterHorses.class), "gender");

    @EventHandler
    public void onHorseSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.HORSE) return;

        Horse horse = (Horse) event.getEntity();
        String gender = GENDERS[random.nextInt(GENDERS.length)];

        horse.getPersistentDataContainer().set(genderKey, PersistentDataType.STRING, gender);
    }
}
