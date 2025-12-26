package me.luisgamedev.betterhorses.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired when a BetterHorses item is about to be neutered via the {@code /horse neuter}
 * command. Cancelling this event will prevent the neutering from being applied to the
 * target item.
 */
public class BetterHorseNeuterEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private ItemStack horseItem;
    private boolean cancelled;

    public BetterHorseNeuterEvent(@NotNull Player player, @NotNull ItemStack horseItem) {
        this.player = Objects.requireNonNull(player, "player");
        this.horseItem = Objects.requireNonNull(horseItem, "horseItem");
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull ItemStack getHorseItem() {
        return horseItem;
    }

    public void setHorseItem(@NotNull ItemStack horseItem) {
        this.horseItem = Objects.requireNonNull(horseItem, "horseItem");
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
