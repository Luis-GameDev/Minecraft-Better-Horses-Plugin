package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.traits.TraitRegistry;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractHorse;
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
        if (!(player.getVehicle() instanceof AbstractHorse mount)) return;
        if (!SupportedMountType.isSupported(mount)) return;

        PersistentDataContainer data = mount.getPersistentDataContainer();
        NamespacedKey traitKey = new NamespacedKey(BetterHorses.getInstance(), "trait");
        if (!data.has(traitKey, PersistentDataType.STRING)) return;

        String trait = data.get(traitKey, PersistentDataType.STRING).toLowerCase();

        switch (trait) {
            case "frosthooves":
                TraitRegistry.activateFrostHooves(player, mount);
                break;
            case "featherhooves":
                TraitRegistry.activateFeatherHooves(player, mount);
                break;
            case "fireheart":
                TraitRegistry.activateFireheart(player, mount);
                break;
        }
    }
}
