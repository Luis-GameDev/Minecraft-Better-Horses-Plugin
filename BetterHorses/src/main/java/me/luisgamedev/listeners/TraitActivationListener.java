package me.luisgamedev.listeners;

import me.luisgamedev.BetterHorses;
import me.luisgamedev.traits.TraitRegistry;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class TraitActivationListener implements Listener {

    private final NamespacedKey traitKey = new NamespacedKey(BetterHorses.getInstance(), "trait");

    @EventHandler
    public void onTraitKeyPressed(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Entity vehicle = player.getVehicle();

        if (!(vehicle instanceof Horse horse)) return;

        PersistentDataContainer data = horse.getPersistentDataContainer();
        if (!data.has(traitKey, PersistentDataType.STRING)) return;

        String trait = data.get(traitKey, PersistentDataType.STRING);
        if (trait == null) return;

        switch (trait.toLowerCase()) {
            case "hellmare":
                TraitRegistry.activateHellmare(player, horse);
                break;
            case "dashboost":
                TraitRegistry.activateDashBoost(player, horse);
                break;
            case "kickback":
                TraitRegistry.activateKickback(player, horse);
                break;
            case "ghosthorse":
                TraitRegistry.activateGhostHorse(player, horse);
                break;
            case "revenantcurse":
                TraitRegistry.activateRevenantCurse(player, horse);
                break;
        }

        event.setCancelled(true); // Prevent item swap
    }
}
