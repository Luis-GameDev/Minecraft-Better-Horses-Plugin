package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityDismountEvent;

public class HorseStepHeightListener implements Listener {

    @EventHandler
    public void onMount(EntityMountEvent event) {
        if (!(event.getMount() instanceof AbstractHorse horse)) return;
        if (!SupportedMountType.isSupported(horse)) return;

        FileConfiguration config = BetterHorses.getInstance().getConfig();
        if (!config.getBoolean("settings.fix-step-height", false)) return;

        setStepHeightSafe(horse, 1.1);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getDismounted() instanceof AbstractHorse horse)) return;
        if (!SupportedMountType.isSupported(horse)) return;

        FileConfiguration config = BetterHorses.getInstance().getConfig();
        if (!config.getBoolean("settings.fix-step-height", false)) return;

        setStepHeightSafe(horse, 1);
    }

    private void setStepHeightSafe(AbstractHorse horse, double stepHeight) {
        try {
            Attribute attr = Attribute.valueOf("GENERIC_STEP_HEIGHT");
            AttributeInstance instance = horse.getAttribute(attr);
            if (instance != null) {
                instance.setBaseValue(stepHeight);
            }
        } catch (IllegalArgumentException | NoSuchFieldError ignored) {
        }
    }
}
