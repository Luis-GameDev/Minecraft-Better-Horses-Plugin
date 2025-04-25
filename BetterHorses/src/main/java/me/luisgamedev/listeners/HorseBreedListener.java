package me.luisgamedev.listeners;

import me.luisgamedev.BetterHorses;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class HorseBreedListener implements Listener {

    @EventHandler
    public void onHorseBreed(EntityBreedEvent event) {
        if (!(event.getMother() instanceof Horse) || !(event.getFather() instanceof Horse)) return;

        Horse mother = (Horse) event.getMother();
        Horse father = (Horse) event.getFather();

        NamespacedKey genderKey = new NamespacedKey(BetterHorses.getInstance(), "gender");
        NamespacedKey neuterKey = new NamespacedKey(BetterHorses.getInstance(), "neutered");

        String motherGender = getGender(mother, genderKey);
        String fatherGender = getGender(father, genderKey);

        boolean motherNeutered = isNeutered(mother, neuterKey);
        boolean fatherNeutered = isNeutered(father, neuterKey);

        boolean allowSameGender = BetterHorses.getInstance().getConfig().getBoolean("settings.allow-same-gender-breeding", false);
        if (!allowSameGender && motherGender.equals(fatherGender)) {
            event.setCancelled(true);
            return;
        }

        if (motherNeutered || fatherNeutered) {
            event.setCancelled(true);
        }
    }

    private String getGender(Horse horse, NamespacedKey key) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        return data.getOrDefault(key, PersistentDataType.STRING, "unknown");
    }

    private boolean isNeutered(Horse horse, NamespacedKey key) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        return data.has(key, PersistentDataType.BYTE) && data.get(key, PersistentDataType.BYTE) == (byte) 1;
    }
}
