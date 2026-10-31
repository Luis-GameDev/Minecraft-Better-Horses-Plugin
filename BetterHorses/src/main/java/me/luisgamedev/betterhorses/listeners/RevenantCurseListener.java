package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import me.luisgamedev.betterhorses.utils.RuntimeCompat;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class RevenantCurseListener implements Listener {

    private static final NamespacedKey CURSE_KEY = new NamespacedKey(BetterHorses.getInstance(), "revenantcurse_active");

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHorseOrRiderHit(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || !(event.getDamager() instanceof LivingEntity attacker)) return;

        if (event.getEntity() instanceof AbstractHorse horse) {
            if (hasActiveCurse(horse)) {
                applyDebuff(attacker);
            }
            return;
        }

        if (event.getEntity() instanceof Player rider) {
            if (rider.getVehicle() instanceof AbstractHorse horse && SupportedMountType.isSupported(horse) && hasActiveCurse(horse)) {
                applyDebuff(attacker);
            }
        }
    }

    private boolean hasActiveCurse(AbstractHorse horse) {
        if (!SupportedMountType.isSupported(horse)) return false;
        if (!horse.getPersistentDataContainer().has(CURSE_KEY, PersistentDataType.LONG)) return false;
        long until = horse.getPersistentDataContainer().get(CURSE_KEY, PersistentDataType.LONG);
        return System.currentTimeMillis() <= until;
    }

    private void applyDebuff(LivingEntity attacker) {
        if (!org.bukkit.Bukkit.isOwnedByCurrentRegion(attacker)) {
            BetterHorses.getInstance().scheduler().entity(attacker, 1L, ignored -> {
                if (attacker.isValid() && !attacker.isDead()) applyDebuff(attacker);
            }, () -> { });
            return;
        }
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 3));
        attacker.addPotionEffect(new PotionEffect(RuntimeCompat.potion("NAUSEA", "CONFUSION"), 30, 3)); // Nausea
        attacker.addPotionEffect(new PotionEffect(RuntimeCompat.potion("SLOWNESS", "SLOW"), 30, 6));
    }
}
