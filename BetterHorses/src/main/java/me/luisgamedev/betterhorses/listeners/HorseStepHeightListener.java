package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HorseStepHeightListener implements Listener, ListenerLifecycle {
    private static final Map<UUID, Double> ORIGINAL = new ConcurrentHashMap<>();
    private static final Map<UUID, AbstractHorse> HORSES = new ConcurrentHashMap<>();

    @EventHandler
    public void onMount(EntityMountEvent event) {
        if (!(event.getMount() instanceof AbstractHorse horse)) return;
        if (!SupportedMountType.isSupported(horse)) return;

        FileConfiguration config = BetterHorses.getInstance().getConfig();
        if (!config.getBoolean("settings.fix-step-height", false)) return;

        AttributeInstance instance = getStepHeight(horse);
        if (instance != null) {
            ORIGINAL.putIfAbsent(horse.getUniqueId(), instance.getBaseValue());
            HORSES.put(horse.getUniqueId(), horse);
            instance.setBaseValue(1.1);
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getDismounted() instanceof AbstractHorse horse)) return;
        if (!SupportedMountType.isSupported(horse)) return;

        FileConfiguration config = BetterHorses.getInstance().getConfig();
        if (!config.getBoolean("settings.fix-step-height", false)) return;

        restore(horse);
    }

    private void setStepHeightSafe(AbstractHorse horse, double stepHeight) {
        try {
            Attribute attr = AttributeResolver.generic("STEP_HEIGHT");
            AttributeInstance instance = horse.getAttribute(attr);
            if (instance != null) {
                instance.setBaseValue(stepHeight);
            }
        } catch (IllegalArgumentException | NoSuchFieldError ignored) {
        }
    }

    private static AttributeInstance getStepHeight(AbstractHorse horse) {
        try { return horse.getAttribute(AttributeResolver.generic("STEP_HEIGHT")); }
        catch (IllegalArgumentException | NoSuchFieldError ignored) { return null; }
    }

    private static void restore(AbstractHorse horse) {
        Double original = ORIGINAL.remove(horse.getUniqueId());
        HORSES.remove(horse.getUniqueId());
        AttributeInstance instance = getStepHeight(horse);
        if (original != null && instance != null && horse.isValid()) instance.setBaseValue(original);
    }

    public static void cleanupAll() {
        HORSES.forEach((id, horse) -> BetterHorses.getInstance().scheduler().entity(horse, 1L,
                ignored -> restore(horse), () -> { ORIGINAL.remove(id); HORSES.remove(id); }));
    }

    @Override
    public void shutdown() {
        cleanupAll();
    }
}
