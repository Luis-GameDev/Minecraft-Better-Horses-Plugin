package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.inspection.HorseInspectionManager;
import me.luisgamedev.betterhorses.inspection.HorseInspectionText;
import me.luisgamedev.betterhorses.inspection.HorseNeuteringService;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.NeuteringPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.logging.Level;

/** Handles deliberate main-hand gestures after protection plugins have checked the interaction. */
public final class HorseInteractionListener implements Listener {
    private final BetterHorses plugin;
    private final HorseInspectionManager inspections;
    private final HorseInspectionText text;
    private final HorseNeuteringService neutering;

    public HorseInteractionListener(BetterHorses plugin, HorseInspectionManager inspections) {
        this.plugin = plugin;
        this.inspections = inspections;
        text = new HorseInspectionText(plugin);
        neutering = new HorseNeuteringService(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.isCancelled() || event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof AbstractHorse horse)) return;
        Player player = event.getPlayer();
        // A synchronous dual-entity interaction is safe only while both owners are this region.
        if (!Bukkit.isOwnedByCurrentRegion(player) || !Bukkit.isOwnedByCurrentRegion(horse)) return;
        SupportedMountType type = SupportedMountType.fromEntity(horse).orElse(null);
        if (type == null || !type.isEnabled(plugin.getConfig()) || !horse.isValid() || horse.isDead()) return;

        if (!player.isSneaking()) {
            // A display is a real passenger: remove it before vanilla checks whether the horse is occupied.
            inspections.hideMount(horse);
            return;
        }

        Material held = player.getInventory().getItemInMainHand().getType();
        if (held == Material.SHEARS && NeuteringPolicy.isEnabled(plugin.getConfig())
                && plugin.getConfig().getBoolean("horse-neutering.shears-enabled", false)) {
            event.setCancelled(true);
            if (neutering.neuter(player, horse, EquipmentSlot.HAND) && Bukkit.isOwnedByCurrentRegion(horse)) {
                inspections.hideMount(horse);
            }
            return;
        }
        if (!held.isAir() || !plugin.getConfig().getBoolean("horse-inspection.enabled", true)) return;
        if (plugin.getConfig().getBoolean("horse-inspection.only-tamed", true) && !horse.isTamed()) return;
        if (!player.hasPermission("betterhorses.inspect")) return;

        event.setCancelled(true);
        try {
            inspections.show(player, horse, text.render(player, horse));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not format horse inspection text; check horse-inspection in language.yml.", exception);
        }
    }
}
