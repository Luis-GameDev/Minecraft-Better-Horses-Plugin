package me.luisgamedev.traits;

import me.luisgamedev.BetterHorses;
import me.luisgamedev.language.LanguageManager;
import me.luisgamedev.utils.ArmorHider;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TraitRegistry {

    private static final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    static LanguageManager lang = BetterHorses.getInstance().getLang();
    static FileConfiguration config = BetterHorses.getInstance().getConfig();

    public static void activateHellmare(Player player, Horse horse) {
        if (!config.getBoolean("traits.hellmare.enabled")) return;

        String key = "hellmare";
        if (isOnCooldown(horse, key)) return;

        int duration = config.getInt("traits.hellmare.duration", 10);
        int radius = config.getInt("traits.hellmare.radius", 1);
        player.sendMessage(lang.get("traits.hellmare-message"));

        PotionEffect fireResist = new PotionEffect(PotionEffectType.FIRE_RESISTANCE, duration * 20, 1, false, false, false);
        player.addPotionEffect(fireResist);
        horse.addPotionEffect(fireResist);

        setCooldown(horse, key, config.getInt("traits.hellmare.cooldown", 30));

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

                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        Location fireLoc = center.clone().add(dx, 0, dz);
                        Block ground = fireLoc.getBlock();
                        Block above = ground.getRelative(0, 1, 0);

                        if (ground.getType().isSolid() && above.getType() == Material.AIR) {
                            BlockIgniteEvent igniteEvent = new BlockIgniteEvent(
                                    above,
                                    BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL,
                                    player
                            );
                            Bukkit.getPluginManager().callEvent(igniteEvent);

                            if (!igniteEvent.isCancelled()) {
                                above.setType(Material.FIRE);
                            }
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
        if (!config.getBoolean("traits.fireheart.enabled")) return;

        horse.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 10000, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20, 0));
    }

    public static void activateDashBoost(Player player, Horse horse) {
        if (!config.getBoolean("traits.dashboost.enabled")) return;

        String key = "dashboost";
        if (isOnCooldown(horse, key)) return;

        int duration = config.getInt("traits.dashboost.duration", 5);
        double originalSpeed = horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getBaseValue();
        double boostedSpeed = originalSpeed * 1.5;

        horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(boostedSpeed);
        player.sendMessage(lang.get("traits.dashboost-message"));

        setCooldown(horse, key, config.getInt("traits.dashboost.cooldown", 30));

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
        if (!config.getBoolean("traits.featherhooves.enabled")) return;

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 10, 0));
        horse.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 10000, 0));
    }

    public static void activateGhostHorse(Player player, Horse horse) {
        if (!config.getBoolean("traits.ghosthorse.enabled")) return;

        String key = "ghosthorse";
        if (isOnCooldown(horse, key)) return;

        int duration = config.getInt("traits.ghosthorse.duration", 5);
        player.sendMessage(lang.get("traits.ghosthorse-message"));

        horse.setInvisible(true);
        player.setInvisible(true);
        ArmorHider.hide(player, horse);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (horse.isValid()) {
                    player.setInvisible(false);
                    horse.setInvisible(false);
                    ArmorHider.show(player, horse);
                }
            }
        }.runTaskLater(BetterHorses.getInstance(), duration * 20L);

        setCooldown(horse, key, config.getInt("traits.ghosthorse.cooldown", 30));
    }

    public static void activateSkyburst(Player player, Horse horse) {
        if (!config.getBoolean("traits.skyburst.enabled")) return;

        String key = "skyburst";
        double radius = config.getDouble("traits.skyburst.radius", 3.0);
        player.getWorld().spawnParticle(Particle.CLOUD, horse.getLocation(), 20, 0.5, 0.1, 0.5, 0.01);
        player.playSound(horse.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);

        for (Entity entity : horse.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity && entity != player && entity != horse) {
                EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(player, entity, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 0.0);
                Bukkit.getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    Vector velocity = entity.getVelocity();
                    velocity.setY(1);
                    entity.setVelocity(velocity);
                }
            }
        }
    }

    public static void activateRevenantCurse(Player player, Horse horse) {
        if (!config.getBoolean("traits.revenantcurse.enabled")) return;

        String key = "revenantcurse";
        if (isOnCooldown(horse, key)) return;

        int duration = config.getInt("traits.revenantcurse.duration", 5);
        player.sendMessage(lang.get("traits.revenantcurse-message"));

        horse.getPersistentDataContainer().set(
                new NamespacedKey(BetterHorses.getInstance(), "revenantcurse_active"),
                PersistentDataType.LONG,
                System.currentTimeMillis() + duration * 1000L
        );

        horse.getWorld().spawnParticle(Particle.WITCH, horse.getLocation(), 25, 0.6, 0.6, 0.6, 0.05);
        horse.getWorld().playSound(horse.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1, 0.8f);

        setCooldown(horse, key, config.getInt("traits.revenantcurse.cooldown", 30));
    }

    public static void activateKickback(Player player, Horse horse) {
        if (!config.getBoolean("traits.kickback.enabled")) return;

        String key = "kickback";
        if (isOnCooldown(horse, key)) return;

        double radius = config.getDouble("traits.kickback.radius", 2.5);
        double strength = config.getDouble("traits.kickback.strength", 1.5);

        for (Entity entity : horse.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity && entity != player) {
                EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(player, entity, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 0.0);
                Bukkit.getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    Vector knockback = entity.getLocation().toVector().subtract(horse.getLocation().toVector()).normalize().multiply(strength);
                    entity.setVelocity(knockback);
                }
            }
        }

        player.sendMessage(lang.get("traits.kickback-message"));
        setCooldown(horse, key, config.getInt("traits.kickback.cooldown", 10));
    }

    public static void activateFrostHooves(Player player, Horse horse) {
        if (!config.getBoolean("traits.frosthooves.enabled")) return;

        Location center = horse.getLocation().subtract(0, 1, 0);
        int radius = config.getInt("traits.frosthooves.radius", 3);

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Location loc = center.clone().add(x, 0, z);
                Block block = loc.getBlock();

                if (block.getType() == Material.WATER) {
                    EntityChangeBlockEvent frostEvent = new EntityChangeBlockEvent(
                            player,
                            block,
                            Material.FROSTED_ICE.createBlockData()
                    );
                    Bukkit.getPluginManager().callEvent(frostEvent);

                    if (!frostEvent.isCancelled()) {
                        block.setType(Material.FROSTED_ICE);
                    }
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
}
