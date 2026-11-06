package me.luisgamedev.betterhorses.api.events;

import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired for each trample target before damage, knockback, or a hit cooldown is applied.
 * Cancelling prevents all three effects for this hit. Knockback vectors are copied on
 * input and output; use {@link #setKnockback(Vector)} to change the event's value.
 * Runs synchronously on the server thread, or on Folia's region owning the rider,
 * horse, and target. Region changes invalidate a pending hit before it is applied.
 */
public final class HorseTrampleEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player rider;
    private final AbstractHorse horse;
    private final LivingEntity target;
    private double damage;
    private Vector knockback;
    private int cooldownTicks;
    private boolean cancelled;

    public HorseTrampleEvent(@NotNull Player rider, @NotNull AbstractHorse horse,
                             @NotNull LivingEntity target, double damage, @NotNull Vector knockback) {
        this(rider, horse, target, damage, knockback, 0);
    }

    public HorseTrampleEvent(@NotNull Player rider, @NotNull AbstractHorse horse,
                             @NotNull LivingEntity target, double damage,
                             @NotNull Vector knockback, int cooldownTicks) {
        this.rider = Objects.requireNonNull(rider, "rider");
        this.horse = Objects.requireNonNull(horse, "horse");
        this.target = Objects.requireNonNull(target, "target");
        setDamage(damage);
        setKnockback(knockback);
        setCooldownTicks(cooldownTicks);
    }

    public @NotNull Player getPlayer() { return rider; }
    public @NotNull Player getRider() { return rider; }
    public @NotNull AbstractHorse getHorse() { return horse; }
    public @NotNull LivingEntity getTarget() { return target; }
    public double getDamage() { return damage; }

    /** @throws IllegalArgumentException if the damage is negative or nonfinite */
    public void setDamage(double damage) {
        if (!Double.isFinite(damage) || damage < 0) throw new IllegalArgumentException("Damage must be finite and nonnegative");
        this.damage = damage;
    }

    public @NotNull Vector getKnockback() { return knockback.clone(); }

    /** @throws IllegalArgumentException if any vector component is nonfinite */
    public void setKnockback(@NotNull Vector knockback) {
        Objects.requireNonNull(knockback, "knockback");
        double x = knockback.getX(), y = knockback.getY(), z = knockback.getZ();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Knockback components must be finite");
        }
        this.knockback = new Vector(x, y, z);
    }

    public int getCooldownTicks() { return cooldownTicks; }

    /** @throws IllegalArgumentException if the cooldown is negative */
    public void setCooldownTicks(int cooldownTicks) {
        if (cooldownTicks < 0) throw new IllegalArgumentException("Cooldown ticks must be nonnegative");
        this.cooldownTicks = cooldownTicks;
    }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
