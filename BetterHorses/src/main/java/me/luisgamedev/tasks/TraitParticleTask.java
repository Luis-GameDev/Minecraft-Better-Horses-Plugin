package me.luisgamedev.tasks;

import me.luisgamedev.BetterHorses;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;

public class TraitParticleTask implements Runnable {

    private final BetterHorses plugin = BetterHorses.getInstance();
    private final NamespacedKey traitKey = new NamespacedKey(plugin, "trait");

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("settings.trait-particle-indicator", false)) return;

        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!(entity instanceof Horse horse)) continue;

                String trait = horse.getPersistentDataContainer().get(traitKey, PersistentDataType.STRING);
                if (trait == null) continue;

                switch (trait.toLowerCase()) {
                    case "skyburst":
                        spawn(horse, Particle.CLOUD, 1, 0.3, 0.1, 0.3);
                        break;
                    case "hellmare":
                        spawn(horse, Particle.FLAME, 4, 0.3, 0.1, 0.3);
                        break;
                    case "ghosthorse":
                        spawn(horse, Particle.SMOKE, 4, 0.25, 0.2, 0.25);
                        break;
                    case "dashboost":
                        spawn(horse, Particle.SWEEP_ATTACK, 2, 0.2, 0.1, 0.2);
                        break;
                    case "revenantcurse":
                        spawn(horse, Particle.SOUL, 3, 0.25, 0.3, 0.25);
                        break;
                    case "frosthooves":
                        spawn(horse, Particle.SNOWFLAKE, 3, 0.3, 0.1, 0.3);
                        break;
                    case "kickback":
                        spawn(horse, Particle.CRIT, 2, 0.25, 0.2, 0.25);
                        break;
                    case "featherhooves":
                        spawn(horse, Particle.CLOUD, 2, 0.2, 0.3, 0.2);
                        break;
                    case "fireheart":
                        spawn(horse, Particle.LAVA, 2, 0.2, 0.1, 0.2);
                        break;
                }
            }
        }
    }

    private void spawn(Horse horse, Particle particle, int amount, double dx, double dy, double dz) {
        horse.getWorld().spawnParticle(particle, horse.getLocation().add(0, 1.2, 0), amount, dx, dy, dz, 0.01);
    }
}
