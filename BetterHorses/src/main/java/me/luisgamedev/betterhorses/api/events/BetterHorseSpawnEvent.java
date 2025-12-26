package me.luisgamedev.betterhorses.api.events;

import org.bukkit.entity.AbstractHorse;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired whenever a BetterHorses mount is spawned, either from an item or a
 * natural spawn handled by the plugin.
 */
public class BetterHorseSpawnEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    public enum SpawnCause {
        ITEM,
        NATURAL
    }

    private final AbstractHorse horse;
    private final SpawnCause cause;
    private final ItemStack sourceItem;
    private boolean cancelled;

    public BetterHorseSpawnEvent(@NotNull AbstractHorse horse, @Nullable ItemStack sourceItem, @NotNull SpawnCause cause) {
        this.horse = horse;
        this.cause = cause;
        this.sourceItem = sourceItem;
    }

    public @NotNull AbstractHorse getHorse() {
        return horse;
    }

    public @NotNull SpawnCause getCause() {
        return cause;
    }

    public @Nullable ItemStack getSourceItem() {
        return sourceItem;
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
