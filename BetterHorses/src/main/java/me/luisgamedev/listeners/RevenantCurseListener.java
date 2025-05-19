package me.luisgamedev.listeners;

import me.luisgamedev.BetterHorses;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class RevenantCurseListener implements Listener {

    private static final NamespacedKey CURSE_KEY = new NamespacedKey(BetterHorses.getInstance(), "revenantcurse_active");

    @EventHandler
    public void onHorseOrRiderHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;

        if (event.getEntity() instanceof Horse horse) {
            if (hasActiveCurse(horse)) {
                applyDebuff(attacker);
            }
            return;
        }

        if (event.getEntity() instanceof Player rider) {
            if (rider.getVehicle() instanceof Horse horse && hasActiveCurse(horse)) {
                applyDebuff(attacker);
            }
        }
    }

    private boolean hasActiveCurse(Horse horse) {
        if (!horse.getPersistentDataContainer().has(CURSE_KEY, PersistentDataType.LONG)) return false;
        long until = horse.getPersistentDataContainer().get(CURSE_KEY, PersistentDataType.LONG);
        return System.currentTimeMillis() <= until;
    }

    private void applyDebuff(LivingEntity attacker) {
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 3));
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 30, 3)); // Nausea
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 30, 6));
    }
}
