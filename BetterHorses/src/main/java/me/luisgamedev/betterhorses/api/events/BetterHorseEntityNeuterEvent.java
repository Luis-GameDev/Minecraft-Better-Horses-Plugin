package me.luisgamedev.betterhorses.api.events;

import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired before a player neuters a live mount with shears. Cancelling prevents
 * BetterHorses from changing the mount, wearing the shears, or playing feedback.
 * Protection integrations can cancel this event independently of item neutering.
 */
public final class BetterHorseEntityNeuterEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final AbstractHorse horse;
    private final EquipmentSlot hand;
    private boolean cancelled;

    public BetterHorseEntityNeuterEvent(@NotNull Player player, @NotNull AbstractHorse horse,
                                       @NotNull EquipmentSlot hand) {
        this.player = Objects.requireNonNull(player, "player");
        this.horse = Objects.requireNonNull(horse, "horse");
        this.hand = Objects.requireNonNull(hand, "hand");
    }

    public @NotNull Player getPlayer() { return player; }
    public @NotNull AbstractHorse getHorse() { return horse; }
    public @NotNull EquipmentSlot getHand() { return hand; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
