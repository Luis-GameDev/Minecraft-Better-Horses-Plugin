package me.luisgamedev.betterhorses.api.events;

import org.bukkit.entity.AbstractHorse;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired whenever a BetterHorses mount is converted back into an item.
 */
public class BetterHorseDespawnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final AbstractHorse horse;
    private final ItemStack resultItem;
    private boolean cancelled;

    public BetterHorseDespawnEvent(@NotNull AbstractHorse horse, @Nullable ItemStack resultItem) {
        this.horse = horse;
        this.resultItem = resultItem;
    }

    public @NotNull AbstractHorse getHorse() {
        return horse;
    }

    /**
        * Item stack that will be given to the player. May be null if the horse
        * was not yet converted to an item when the event is fired.
        */
    public @Nullable ItemStack getResultItem() {
        return resultItem;
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
