package me.luisgamedev.betterhorses.api.events;

import org.bukkit.entity.AbstractHorse;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Fired after BetterHorses calculates the stats for a newly bred foal but
 * before they are applied. Listeners may modify the resulting stats, gender,
 * or trait, or cancel to stop the breeding entirely.
 */
public class BetterHorseBreedEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final AbstractHorse child;
    private final AbstractHorse father;
    private final AbstractHorse mother;
    private double health;
    private double speed;
    private double jump;
    private String gender;
    private @Nullable String trait;
    private boolean cancelled;

    public BetterHorseBreedEvent(@NotNull AbstractHorse child,
                                 @NotNull AbstractHorse father,
                                 @NotNull AbstractHorse mother,
                                 double health,
                                 double speed,
                                 double jump,
                                 @NotNull String gender,
                                 @Nullable String trait) {
        this.child = Objects.requireNonNull(child, "child");
        this.father = Objects.requireNonNull(father, "father");
        this.mother = Objects.requireNonNull(mother, "mother");
        this.health = health;
        this.speed = speed;
        this.jump = jump;
        this.gender = Objects.requireNonNull(gender, "gender");
        this.trait = trait;
    }

    public @NotNull AbstractHorse getChild() {
        return child;
    }

    public @NotNull AbstractHorse getFather() {
        return father;
    }

    public @NotNull AbstractHorse getMother() {
        return mother;
    }

    public double getHealth() {
        return health;
    }

    public void setHealth(double health) {
        this.health = health;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getJump() {
        return jump;
    }

    public void setJump(double jump) {
        this.jump = jump;
    }

    public @NotNull String getGender() {
        return gender;
    }

    public void setGender(@NotNull String gender) {
        this.gender = Objects.requireNonNull(gender, "gender");
    }

    public @Nullable String getTrait() {
        return trait;
    }

    public void setTrait(@Nullable String trait) {
        this.trait = trait;
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
