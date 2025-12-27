package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.events.BetterHorseAbilityUseEvent;
import me.luisgamedev.betterhorses.traits.TraitRegistry;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
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

        if (!(vehicle instanceof AbstractHorse mount)) return;
        if (!SupportedMountType.isSupported(mount)) return;

        PersistentDataContainer data = mount.getPersistentDataContainer();
        if (!data.has(traitKey, PersistentDataType.STRING)) return;

        String trait = data.get(traitKey, PersistentDataType.STRING);
        if (trait == null) return;

        BetterHorseAbilityUseEvent abilityEvent = new BetterHorseAbilityUseEvent(player, mount, trait.toLowerCase());
        Bukkit.getPluginManager().callEvent(abilityEvent);
        if (abilityEvent.isCancelled()) {
            event.setCancelled(true);
            return;
        }

        String selectedTrait = abilityEvent.getTraitKey();
        switch (selectedTrait.toLowerCase()) {
            case "hellmare":
                TraitRegistry.activateHellmare(player, mount);
                break;
            case "dashboost":
                TraitRegistry.activateDashBoost(player, mount);
                break;
            case "kickback":
                TraitRegistry.activateKickback(player, mount);
                break;
            case "ghosthorse":
                TraitRegistry.activateGhostHorse(player, mount);
                break;
            case "revenantcurse":
                TraitRegistry.activateRevenantCurse(player, mount);
                break;
        }

        event.setCancelled(true); // Prevent item swap
    }
}
