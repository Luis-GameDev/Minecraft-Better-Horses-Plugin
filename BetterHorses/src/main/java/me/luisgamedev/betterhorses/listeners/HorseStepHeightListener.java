package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Owns a temporary riding adjustment without persisting a changed native base on Paper/Folia. */
public class HorseStepHeightListener implements Listener, ListenerLifecycle {
    static final UUID MODIFIER_ID = UUID.fromString("a7e2da1a-1f39-4dc6-850a-f786c44a6ef3");
    static final NamespacedKey ORIGINAL_KEY = new NamespacedKey("betterhorses", "step_height_original");
    static final NamespacedKey APPLIED_KEY = new NamespacedKey("betterhorses", "step_height_applied");
    private static final Map<UUID, AbstractHorse> HORSES = new ConcurrentHashMap<>();
    private static final Method TRANSIENT_MODIFIER = transientModifierMethod();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getMount() instanceof AbstractHorse horse)) return;
        if (!SupportedMountType.isSupported(horse) || !enabled()) return;
        applyStepHeight(horse);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDismounted() instanceof AbstractHorse horse)) return;
        // Camels can have another rider. The departing entity is still in the passenger list during the event.
        if (horse.getPassengers().stream().noneMatch(passenger -> passenger instanceof Player && passenger != event.getEntity())) {
            restoreStepHeight(horse);
        }
    }

    /** Called on the horse's owning thread by the always-active entity lifecycle reconciler. */
    public static void reconcileTemporaryState(AbstractHorse horse) {
        AbstractHorse tracked = HORSES.get(horse.getUniqueId());
        boolean ridden = horse.getPassengers().stream().anyMatch(Player.class::isInstance);
        if (tracked == horse) {
            if (!enabled() || !ridden || !SupportedMountType.isSupported(horse)) restoreStepHeight(horse);
            return;
        }
        // A previous plugin instance may have left a live transient modifier. Its UUID is plugin-specific.
        restoreStepHeight(horse);
        if (ridden && enabled() && SupportedMountType.isSupported(horse)) applyStepHeight(horse);
    }

    static void applyStepHeight(AbstractHorse horse) {
        AttributeInstance attribute = getStepHeight(horse);
        if (attribute == null) return;
        restoreStepHeight(horse);
        AttributeModifier modifier = new AttributeModifier(MODIFIER_ID, "betterhorses.step-height",
                1.1D - attribute.getBaseValue(), AttributeModifier.Operation.ADD_NUMBER);
        boolean transientApplied = false;
        if (TRANSIENT_MODIFIER != null) {
            try {
                TRANSIENT_MODIFIER.invoke(attribute, modifier);
                transientApplied = true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                removeOwnedModifier(attribute);
            }
        }
        if (!transientApplied) {
            // Spigot lacks addTransientModifier. Save a recovery record before changing its native base.
            PersistentDataContainer data = horse.getPersistentDataContainer();
            data.set(ORIGINAL_KEY, PersistentDataType.DOUBLE, attribute.getBaseValue());
            data.set(APPLIED_KEY, PersistentDataType.DOUBLE, 1.1D);
            attribute.setBaseValue(1.1D);
        }
        HORSES.put(horse.getUniqueId(), horse);
    }

    static void restoreStepHeight(AbstractHorse horse) {
        HORSES.remove(horse.getUniqueId(), horse);
        AttributeInstance attribute = getStepHeight(horse);
        if (attribute == null) return;
        removeOwnedModifier(attribute);
        PersistentDataContainer data = horse.getPersistentDataContainer();
        Double original = data.get(ORIGINAL_KEY, PersistentDataType.DOUBLE);
        Double applied = data.get(APPLIED_KEY, PersistentDataType.DOUBLE);
        if (original != null && Double.isFinite(original)
                && applied != null && Double.compare(attribute.getBaseValue(), applied) == 0) {
            attribute.setBaseValue(original);
        }
        data.remove(ORIGINAL_KEY);
        data.remove(APPLIED_KEY);
    }

    private static void removeOwnedModifier(AttributeInstance attribute) {
        for (AttributeModifier modifier : List.copyOf(attribute.getModifiers())) {
            if (MODIFIER_ID.equals(modifier.getUniqueId())) attribute.removeModifier(modifier);
        }
    }

    private static AttributeInstance getStepHeight(AbstractHorse horse) {
        try { return horse.getAttribute(AttributeResolver.generic("STEP_HEIGHT")); }
        catch (IllegalArgumentException | NoSuchFieldError ignored) { return null; }
    }

    private static boolean enabled() {
        BetterHorses plugin = BetterHorses.getInstance();
        return plugin != null && plugin.getConfig().getBoolean("settings.fix-step-height", false);
    }

    private static Method transientModifierMethod() {
        try { return AttributeInstance.class.getMethod("addTransientModifier", AttributeModifier.class); }
        catch (ReflectiveOperationException ignored) { return null; }
    }

    public static void cleanupAll() {
        BetterHorses plugin = BetterHorses.getInstance();
        for (Map.Entry<UUID, AbstractHorse> entry : Map.copyOf(HORSES).entrySet()) {
            AbstractHorse horse = entry.getValue();
            HORSES.remove(entry.getKey(), horse);
            if (org.bukkit.Bukkit.isOwnedByCurrentRegion(horse)) {
                if (horse.isValid()) restoreStepHeight(horse);
            } else if (plugin != null && plugin.isEnabled()) {
                plugin.scheduler().entity(horse, 1L, ignored -> {
                    if (horse.isValid()) restoreStepHeight(horse);
                }, () -> { });
            }
            // Disabled plugins cannot enqueue work for foreign regions. Paper's transient modifier
            // is never saved and disappears on unload; owner-thread reconciliation removes stale
            // live modifiers when BetterHorses is enabled again.
        }
    }

    @Override public void shutdown() { cleanupAll(); }
}
