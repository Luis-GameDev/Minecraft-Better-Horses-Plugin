package me.luisgamedev.betterhorses.api.events;

import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

/**
 * Compatibility event for integrations using the original live-mount API.
 * It shares the unified event's handlers and cancellation state, so one live-mount
 * event reaches both APIs. Bukkit filters entity-only handlers from item events.
 *
 * @deprecated Listen to {@link BetterHorseNeuterEvent} and check {@link #isEntity()}.
 */
@Deprecated
public final class BetterHorseEntityNeuterEvent extends BetterHorseNeuterEvent {
    public BetterHorseEntityNeuterEvent(@NotNull Player player, @NotNull AbstractHorse horse,
                                       @NotNull EquipmentSlot hand) {
        super(player, horse, hand);
    }

    @Override public @NotNull Player getPlayer() { return super.getPlayer(); }
    @Override public @NotNull AbstractHorse getHorse() { return super.getHorse(); }
    @Override public @NotNull EquipmentSlot getHand() { return super.getHand(); }

    @Override
    public @NotNull HandlerList getHandlers() { return BetterHorseNeuterEvent.getHandlerList(); }

    public static HandlerList getHandlerList() { return BetterHorseNeuterEvent.getHandlerList(); }
}
