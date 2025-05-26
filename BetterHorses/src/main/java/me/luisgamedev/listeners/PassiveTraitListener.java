package me.luisgamedev.listeners;

import me.luisgamedev.BetterHorses;
import me.luisgamedev.traits.TraitRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class PassiveTraitListener implements Listener {

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!(player.getVehicle() instanceof Horse horse)) return;

        PersistentDataContainer data = horse.getPersistentDataContainer();
        NamespacedKey traitKey = new NamespacedKey(BetterHorses.getInstance(), "trait");
        if (!data.has(traitKey, PersistentDataType.STRING)) return;

        String trait = data.get(traitKey, PersistentDataType.STRING).toLowerCase();

        switch (trait) {
            case "frosthooves":
                TraitRegistry.activateFrostHooves(player, horse);
                break;
            case "featherhooves":
                TraitRegistry.activateFeatherHooves(player, horse);
                break;
            case "fireheart":
                TraitRegistry.activateFireheart(player, horse);
                break;
        }
    }
}
