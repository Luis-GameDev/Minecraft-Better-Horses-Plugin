package me.luisgamedev.betterhorses.api.events;

import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired when a rider attempts to activate a BetterHorses trait ability (e.g.
 * Dash Boost, Hellmare, Ghost Horse). Cancelling this event prevents the
 * ability from running and keeps the previous cooldown state intact.
 */
public class BetterHorseAbilityUseEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final AbstractHorse mount;
    private String traitKey;
    private boolean cancelled;

    public BetterHorseAbilityUseEvent(@NotNull Player player, @NotNull Horse horse, @NotNull String traitKey) {
        this(player, (AbstractHorse) horse, traitKey);
    }

    public BetterHorseAbilityUseEvent(@NotNull Player player, @NotNull AbstractHorse mount, @NotNull String traitKey) {
        this.player = Objects.requireNonNull(player, "player");
        this.mount = Objects.requireNonNull(mount, "mount");
        this.traitKey = Objects.requireNonNull(traitKey, "traitKey");
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    /**
     * @return The mount as a Horse when applicable, or {@code null} for other supported mount types.
     */
    public Horse getHorse() {
        return mount instanceof Horse ? (Horse) mount : null;
    }

    public @NotNull AbstractHorse getMount() {
        return mount;
    }

    public @NotNull String getTraitKey() {
        return traitKey;
    }

    public void setTraitKey(@NotNull String traitKey) {
        this.traitKey = Objects.requireNonNull(traitKey, "traitKey");
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
