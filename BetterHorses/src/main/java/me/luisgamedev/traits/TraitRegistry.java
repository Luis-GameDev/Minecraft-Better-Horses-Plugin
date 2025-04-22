package me.luisgamedev.traits;

import me.luisgamedev.BetterHorses;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class TraitRegistry {

    private static final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public static void activateHellmare(Player player, Horse horse) {
        int duration = getConfig().getInt("traits.hellmare.duration", 10);
        player.sendMessage(ChatColor.GOLD + "Hellmare activated! 🔥");

        PotionEffect fireResist = new PotionEffect(PotionEffectType.FIRE_RESISTANCE, duration * 20, 1, false, false, false);
        player.addPotionEffect(fireResist);
        horse.addPotionEffect(fireResist);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!horse.isValid()) {
                    cancel();
                    return;
                }

                Location center = horse.getLocation().clone().subtract(0, 1, 0);
                World world = center.getWorld();

                world.spawnParticle(Particle.FLAME, horse.getLocation(), 10, 0.4, 0.2, 0.4, 0.01);

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Location fireLoc = center.clone().add(dx, 0, dz);
                        Block ground = fireLoc.getBlock();
                        Block above = ground.getRelative(0, 1, 0);

                        if (ground.getType().isSolid() && above.getType() == Material.AIR) {
                            above.setType(Material.FIRE);
                        }
                    }
                }

                ticks++;
                if (ticks >= duration * 20 / 5) {
                    cancel();
                }
            }
        }.runTaskTimer(BetterHorses.getInstance(), 0, 5);
    }


    public static void activateFireheart(Player player, Horse horse) {
        int duration = getConfig().getInt("traits.fireheart.duration", 30);

        horse.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, duration * 10000, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, duration * 20, 0));
    }

    public static void activateDashBoost(Player player, Horse horse) {
        String key = "dashboost";
        if (isOnCooldown(horse, key)) return;

        int duration = getConfig().getInt("traits.dashboost.duration", 5);
        double originalSpeed = horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getBaseValue();
        double boostedSpeed = originalSpeed * 1.5;

        horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(boostedSpeed);
        player.sendMessage(ChatColor.AQUA + "Dash Boost activated!");

        setCooldown(horse, key, getConfig().getInt("traits.dashboost.cooldown", 30));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (horse.isValid()) {
                    horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(originalSpeed);
                }
            }
        }.runTaskLater(BetterHorses.getInstance(), duration * 20L);
    }

    public static void activateFeatherHooves(Player player, Horse horse) {
        int duration = getConfig().getInt("traits.featherhooves.duration", 10);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, duration * 10, 0));
        horse.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, duration * 10000, 0));
    }

    public static void activateGhostHorse(Player player, Horse horse) {
        String key = "ghosthorse";
        if (isOnCooldown(horse, key)) return;

        int duration = getConfig().getInt("traits.ghosthorse.duration", 5);
        player.sendMessage(ChatColor.GRAY + "You and your horse have become ghostly...");

        horse.setInvisible(true);
        player.setInvisible(true);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (horse.isValid()) {
                    player.setInvisible(false);
                    horse.setInvisible(false);
                }
            }
        }.runTaskLater(BetterHorses.getInstance(), duration * 20L);

        setCooldown(horse, key, getConfig().getInt("traits.ghosthorse.cooldown", 30));
    }


    public static void activateKickback(Player player, Horse horse) {
        String key = "kickback";
        if (isOnCooldown(horse, key)) return;

        double radius = getConfig().getDouble("traits.kickback.radius", 2.5);
        double strength = getConfig().getDouble("traits.kickback.strength", 1.5);

        for (Entity entity : horse.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity && entity != player) {
                Vector knockback = entity.getLocation().toVector().subtract(horse.getLocation().toVector()).normalize().multiply(strength);
                entity.setVelocity(knockback);
            }
        }

        player.sendMessage(ChatColor.YELLOW + "Kickback activated!");
        setCooldown(horse, key, getConfig().getInt("traits.kickback.cooldown", 10));
    }

    public static void activateFrostHooves(Player player, Horse horse) {
        Location center = horse.getLocation().subtract(0, 1, 0);
        int radius = 3;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Location checkLoc = center.clone().add(x, 0, z);
                Block block = checkLoc.getBlock();

                if (block.getType() == Material.WATER) {
                    block.setType(Material.FROSTED_ICE);
                }
            }
        }
    }


    private static boolean isOnCooldown(Horse horse, String key) {
        UUID id = horse.getUniqueId();
        Map<String, Long> horseCooldowns = cooldowns.get(id);
        if (horseCooldowns == null) return false;

        long now = System.currentTimeMillis();
        long until = horseCooldowns.getOrDefault(key, 0L);
        return now < until;
    }

    private static void setCooldown(Horse horse, String key, int seconds) {
        UUID id = horse.getUniqueId();
        cooldowns.putIfAbsent(id, new HashMap<>());
        cooldowns.get(id).put(key, System.currentTimeMillis() + seconds * 1000L);
    }

    private static org.bukkit.configuration.file.FileConfiguration getConfig() {
        return BetterHorses.getInstance().getConfig();
    }
}