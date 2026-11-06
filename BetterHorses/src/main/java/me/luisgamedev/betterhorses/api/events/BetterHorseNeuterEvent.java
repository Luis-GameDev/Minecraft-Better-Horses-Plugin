package me.luisgamedev.betterhorses.api.events;

import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Fired before neutering either a horse item or a live mount. Cancelling prevents
 * neutering and any associated tool wear or feedback. Use {@link #getTargetType()}
 * before accessing the nullable item/entity target.
 */
public class BetterHorseNeuterEvent extends Event implements Cancellable {

    public enum TargetType { ITEM, ENTITY }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final TargetType targetType;
    private final @Nullable AbstractHorse horse;
    private final EquipmentSlot hand;
    private @Nullable ItemStack horseItem;
    private boolean cancelled;

    public BetterHorseNeuterEvent(@NotNull Player player, @NotNull ItemStack horseItem) {
        this.player = Objects.requireNonNull(player, "player");
        this.horseItem = Objects.requireNonNull(horseItem, "horseItem");
        this.targetType = TargetType.ITEM;
        this.horse = null;
        this.hand = EquipmentSlot.HAND;
    }

    public BetterHorseNeuterEvent(@NotNull Player player, @NotNull AbstractHorse horse) {
        this(player, horse, EquipmentSlot.HAND);
    }

    public BetterHorseNeuterEvent(@NotNull Player player, @NotNull AbstractHorse horse,
                                 @NotNull EquipmentSlot hand) {
        this.player = Objects.requireNonNull(player, "player");
        this.horse = Objects.requireNonNull(horse, "horse");
        this.hand = Objects.requireNonNull(hand, "hand");
        this.targetType = TargetType.ENTITY;
        this.horseItem = null;
    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull TargetType getTargetType() { return targetType; }
    public boolean isItem() { return targetType == TargetType.ITEM; }
    public boolean isEntity() { return targetType == TargetType.ENTITY; }

    /** @return the item target, or {@code null} when neutering a live mount */
    public @Nullable ItemStack getHorseItem() {
        return horseItem;
    }

    /**
     * Replace the item being neutered, retaining the item API used by existing integrations.
     * @throws IllegalStateException for an entity-target event
     */
    public void setHorseItem(@NotNull ItemStack horseItem) {
        if (!isItem()) throw new IllegalStateException("A live mount event has no horse item");
        this.horseItem = Objects.requireNonNull(horseItem, "horseItem");
    }

    /** @return the live mount target, or {@code null} when neutering an item */
    public @Nullable AbstractHorse getHorse() { return horse; }

    /** @return the tool hand for live mounts; {@link EquipmentSlot#HAND} for item commands */
    public @NotNull EquipmentSlot getHand() { return hand; }

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
