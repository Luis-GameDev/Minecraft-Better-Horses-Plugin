package me.luisgamedev.betterhorses.traits;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import me.luisgamedev.betterhorses.utils.CooldownDisplay;
import org.bukkit.*;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.HorseJumpEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import me.luisgamedev.betterhorses.scheduler.ScheduledTaskHandle;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

public class TraitRegistry {

    private static final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private static LanguageManager lang() { return BetterHorses.getInstance().getLang(); }
    private static FileConfiguration config() { return BetterHorses.getInstance().getConfig(); }
    private static final Map<UUID, Double> dashBoostOriginalSpeeds = new ConcurrentHashMap<>();
    private static final Map<UUID, ScheduledTaskHandle> dashBoostTasks = new ConcurrentHashMap<>();
    private static final Set<UUID> activeGhostHorsePlayers = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> activeGhostHorseMounts = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, ScheduledTaskHandle> ghostHorseTasks = new ConcurrentHashMap<>();
    private static final Map<UUID, AbstractHorse> boostedHorses = new ConcurrentHashMap<>();
    private static final Map<UUID, AbstractHorse> ghostHorses = new ConcurrentHashMap<>();
    private static final Map<UUID, Player> ghostPlayers = new ConcurrentHashMap<>();

    public static void activateHellmare(Player player, AbstractHorse horse) {
        FileConfiguration config = config();
        if (!config.getBoolean("traits.hellmare.enabled") || !allowed(player, horse)) return;

        String key = "hellmare";
        if (isOnCooldown(horse, key)) {
            showCooldownBar(player, horse, key);
            return;
        }

        int duration = config.getInt("traits.hellmare.duration", 10);
        int radius = config.getInt("traits.hellmare.radius", 1);
        lang().send(player, "traits.hellmare-message");

        PotionEffect fireResist = new PotionEffect(PotionEffectType.FIRE_RESISTANCE, duration * 20, 1, false, false, false);
        player.addPotionEffect(fireResist);
        horse.addPotionEffect(fireResist);

        setCooldown(horse, key, config.getInt("traits.hellmare.cooldown", 30));

        AtomicInteger ticks = new AtomicInteger();
        BetterHorses.getInstance().scheduler().entityRepeating(horse, 1L, 5L, scheduled -> {
                if (!allowed(player, horse)) {
                    scheduled.cancel();
                    return;
                }
                Location center = horse.getLocation().clone().subtract(0, 1, 0);
                World world = center.getWorld();
                Particle hellmareParticle = TraitParticleResolver.getTraitParticle("hellmare", Particle.FLAME);
                world.spawnParticle(hellmareParticle, horse.getLocation(), 10, 0.4, 0.2, 0.4, 0.01);

                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        Location fireLoc = center.clone().add(dx, 0, dz);
                        BetterHorses.getInstance().scheduler().region(fireLoc, 1L, ignored -> {
                            Block ground = fireLoc.getBlock();
                            Block above = ground.getRelative(0, 1, 0);
                            if (!ground.getType().isSolid() || above.getType() != Material.AIR) return;
                            BlockIgniteEvent igniteEvent = new BlockIgniteEvent(
                                    above,
                                    BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL,
                                    player
                            );
                            Bukkit.getPluginManager().callEvent(igniteEvent);
                            if (!igniteEvent.isCancelled()) {
                                above.setType(Material.FIRE);
                            }
                        });
                    }
                }

                if (ticks.incrementAndGet() >= duration * 20 / 5) {
                    scheduled.cancel();
                }
        }, () -> { });
    }

    public static void activateFireheart(Player player, AbstractHorse horse) {
        FileConfiguration config = config();
        if (!config.getBoolean("traits.fireheart.enabled")) return;
        horse.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 10000, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20, 0));
    }

    public static void activateHeavenHooves(Player player, AbstractHorse horse, Event event) {
        FileConfiguration config = config();
        if (!config.getBoolean("traits.heavenhooves.enabled")) return;

        horse.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 10000, 0));

        if (!(event instanceof HorseJumpEvent jumpEvent)) return;

        double power = jumpEvent.getPower();

        jumpEvent.setCancelled(true);

        double baseUp = config.getDouble("traits.heavenhooves.strength");
        double extraUp = power * 0.6;

        double forwardStrength = 0.4;
        Vector forward = horse.getLocation().getDirection().normalize().multiply(forwardStrength);

        forward.setY(baseUp + extraUp);

        horse.setVelocity(forward);

        if (config.getBoolean("traits.heavenhooves.particles")) {
            horse.getWorld().spawnParticle(
                    Particle.CLOUD,
                    horse.getLocation().add(0, 1.5, 0),
                    8,
                    0.3, 0.2, 0.3,
                    0.01
            );
        }

        horse.getWorld().playSound(horse.getLocation(), Sound.BLOCK_WOOL_PLACE, 1f, 1f);
    }



    public static void activateDashBoost(Player player, AbstractHorse horse) {
        FileConfiguration config = config();
        if (!config.getBoolean("traits.dashboost.enabled")) return;

        String key = "dashboost";
        if (isOnCooldown(horse, key)) {
            showCooldownBar(player, horse, key);
            return;
        }

        int duration = config.getInt("traits.dashboost.duration", 5);
        AttributeInstance speedAttr = horse.getAttribute(AttributeResolver.generic("MOVEMENT_SPEED"));
        if (speedAttr == null) return;

        double originalSpeed = speedAttr.getBaseValue();
        dashBoostOriginalSpeeds.putIfAbsent(horse.getUniqueId(), originalSpeed);
        boostedHorses.put(horse.getUniqueId(), horse);
        double boostedSpeed = originalSpeed * 1.5;

        speedAttr.setBaseValue(boostedSpeed);
        lang().send(player, "traits.dashboost-message");

        setCooldown(horse, key, config.getInt("traits.dashboost.cooldown", 30));

        ScheduledTaskHandle task = BetterHorses.getInstance().scheduler().entity(horse, duration * 20L, ignored -> revertDashBoostIfActive(horse), () -> clearDashBoostState(horse.getUniqueId()));
        dashBoostTasks.put(horse.getUniqueId(), task);
    }

    public static void revertDashBoostIfActive(AbstractHorse horse) {
        if (horse == null) return;

        UUID horseId = horse.getUniqueId();
        ScheduledTaskHandle task = dashBoostTasks.remove(horseId);
        if (task != null) {
            task.cancel();
        }

        Double storedOriginal = dashBoostOriginalSpeeds.remove(horseId);
        boostedHorses.remove(horseId);
        if (storedOriginal == null) return;

        if (!horse.isValid()) return;

        AttributeInstance speedAttr = horse.getAttribute(AttributeResolver.generic("MOVEMENT_SPEED"));
        if (speedAttr != null) {
            speedAttr.setBaseValue(storedOriginal);
        }
    }

    public static void activateFeatherHooves(Player player, AbstractHorse horse) {
        FileConfiguration config = config();
        if (!config.getBoolean("traits.featherhooves.enabled")) return;
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 10, 0));
        horse.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 10000, 0));
    }

    public static void activateGhostHorse(Player player, AbstractHorse horse) {
        FileConfiguration config = config();
        if (!config.getBoolean("traits.ghosthorse.enabled")) return;

        String key = "ghosthorse";
        if (isOnCooldown(horse, key)) {
            showCooldownBar(player, horse, key);
            return;
        }

        int duration = config.getInt("traits.ghosthorse.duration", 5);
        lang().send(player, "traits.ghosthorse-message");

        PotionEffect effect = new PotionEffect(PotionEffectType.INVISIBILITY, Math.max(1, duration * 20), 0, false, false, false);
        horse.addPotionEffect(effect, false);
        player.addPotionEffect(effect, false);
        activeGhostHorsePlayers.add(player.getUniqueId());
        activeGhostHorseMounts.add(horse.getUniqueId());
        ghostPlayers.put(player.getUniqueId(), player);
        ghostHorses.put(horse.getUniqueId(), horse);

        ScheduledTaskHandle task = BetterHorses.getInstance().scheduler().entity(horse, duration * 20L, ignored -> endGhostHorseIfActive(player, horse), () -> clearGhostState(player.getUniqueId(), horse.getUniqueId()));
        ghostHorseTasks.put(horse.getUniqueId(), task);

        setCooldown(horse, key, config.getInt("traits.ghosthorse.cooldown", 30));
    }

    public static void endGhostHorseIfActive(Player player, AbstractHorse horse) {
        if (player == null || horse == null) return;

        UUID horseId = horse.getUniqueId();
        UUID playerId = player.getUniqueId();
        boolean horseWasActive = activeGhostHorseMounts.remove(horseId);
        boolean playerWasActive = activeGhostHorsePlayers.remove(playerId);
        ghostPlayers.remove(playerId);
        ghostHorses.remove(horseId);
        if (!horseWasActive && !playerWasActive) return;

        ScheduledTaskHandle task = ghostHorseTasks.remove(horseId);
        if (task != null) {
            task.cancel();
        }

        // Each effect is inspected and removed only on its entity's owning thread. A
        // stronger/longer external effect is retained.
        BetterHorses.getInstance().scheduler().entity(player, 1L, ignored -> {
            PotionEffect playerEffect = player.getPotionEffect(PotionEffectType.INVISIBILITY);
            if (playerEffect != null && playerEffect.getAmplifier() == 0 && playerEffect.getDuration() <= 2)
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }, () -> { });
        BetterHorses.getInstance().scheduler().entity(horse, 1L, ignored -> {
            PotionEffect horseEffect = horse.getPotionEffect(PotionEffectType.INVISIBILITY);
            if (horseEffect != null && horseEffect.getAmplifier() == 0 && horseEffect.getDuration() <= 2) horse.removePotionEffect(PotionEffectType.INVISIBILITY);
        }, () -> { });
    }

    public static void activateSkyburst(Player player, AbstractHorse horse) {
        FileConfiguration config = config();
        if (!config.getBoolean("traits.skyburst.enabled")) return;

        double radius = config.getDouble("traits.skyburst.radius", 3.0);
        player.getWorld().spawnParticle(Particle.CLOUD, horse.getLocation(), 20, 0.5, 0.1, 0.5, 0.01);
        player.playSound(horse.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);

        for (Entity entity : horse.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity target && entity != player && entity != horse) {
                UUID world = horse.getWorld().getUID();
                BetterHorses.getInstance().scheduler().entity(target, 1L, ignored -> {
                if (!target.isValid() || !target.getWorld().getUID().equals(world)) return;
                EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(player, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 0.0);
                Bukkit.getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
                    Vector velocity = target.getVelocity();
                    velocity.setY(1);
                    target.setVelocity(velocity);
                }
                }, () -> { });
            }
        }
    }

    public static void activateRevenantCurse(Player player, AbstractHorse horse) {
        FileConfiguration config = config();
        if (!config.getBoolean("traits.revenantcurse.enabled")) return;

        String key = "revenantcurse";
        if (isOnCooldown(horse, key)) {
            showCooldownBar(player, horse, key);
            return;
        }

        int duration = config.getInt("traits.revenantcurse.duration", 5);
        lang().send(player, "traits.revenantcurse-message");

        horse.getPersistentDataContainer().set(
                new NamespacedKey(BetterHorses.getInstance(), "revenantcurse_active"),
                PersistentDataType.LONG,
                System.currentTimeMillis() + duration * 1000L
        );

        Particle revenantParticle = TraitParticleResolver.getTraitParticle("revenantcurse", Particle.WITCH);
        horse.getWorld().spawnParticle(revenantParticle, horse.getLocation(), 25, 0.6, 0.6, 0.6, 0.05);
        horse.getWorld().playSound(horse.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1, 0.8f);

        setCooldown(horse, key, config.getInt("traits.revenantcurse.cooldown", 30));
    }

    public static void activateKickback(Player player, AbstractHorse horse) {
        FileConfiguration config = config();
        if (!config.getBoolean("traits.kickback.enabled")) return;

        String key = "kickback";
        if (isOnCooldown(horse, key)) {
            showCooldownBar(player, horse, key);
            return;
        }

        double radius = config.getDouble("traits.kickback.radius", 2.5);
        double strength = config.getDouble("traits.kickback.strength", 1.5);
        Location origin = horse.getLocation();

        for (Entity entity : horse.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity target && entity != player) {
                BetterHorses.getInstance().scheduler().entity(target, 1L, ignored -> {
                if (!target.isValid() || target.getWorld() != origin.getWorld()
                        || target.getLocation().distanceSquared(origin) > radius * radius * 3) return;
                EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(player, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 0.0);
                Bukkit.getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    Vector knockback = target.getLocation().toVector().subtract(origin.toVector()).normalize().multiply(strength);
                    target.setVelocity(knockback);
                }
                }, () -> { });
            }
        }

        lang().send(player, "traits.kickback-message");
        setCooldown(horse, key, config.getInt("traits.kickback.cooldown", 10));
    }

    public static void activateFrostHooves(Player player, AbstractHorse horse) {
        FileConfiguration config = config();
        if (!config.getBoolean("traits.frosthooves.enabled")) return;

        Location center = horse.getLocation().subtract(0, 1, 0);
        int radius = config.getInt("traits.frosthooves.radius", 3);

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Location loc = center.clone().add(x, 0, z);
                BetterHorses.getInstance().scheduler().region(loc, 1L, ignored -> {
                    Block block = loc.getBlock();
                    if (block.getType() != Material.WATER) return;
                    EntityChangeBlockEvent frostEvent = new EntityChangeBlockEvent(
                            player,
                            block,
                            Material.FROSTED_ICE.createBlockData()
                    );
                    Bukkit.getPluginManager().callEvent(frostEvent);

                    if (!frostEvent.isCancelled()) {
                        block.setType(Material.FROSTED_ICE);
                        Location restoreAt = block.getLocation();
                        BetterHorses.getInstance().scheduler().region(restoreAt, 20L * 15L, restoreTask -> {
                            Block current = restoreAt.getBlock();
                            if (current.getType() == Material.FROSTED_ICE) current.setType(Material.WATER);
                        });
                    }
                });
            }
        }
    }

    private static void showCooldownBar(Player player, AbstractHorse horse, String key) {
        FileConfiguration config = config();
        int fullCooldown = config.getInt("traits." + key + ".cooldown", 30);
        long now = System.currentTimeMillis();
        long until = cooldowns.get(horse.getUniqueId()).getOrDefault(key, 0L);
        double secondsLeft = (until - now) / 1000.0;
        String name = lang().getRaw(player, "traits." + key);

        if (secondsLeft > 0) {
            CooldownDisplay.showCooldown(secondsLeft, fullCooldown, player, name);
        }
    }

    private static boolean isOnCooldown(AbstractHorse horse, String key) {
        UUID id = horse.getUniqueId();
        Map<String, Long> horseCooldowns = cooldowns.get(id);
        if (horseCooldowns == null) return false;

        long now = System.currentTimeMillis();
        long until = horseCooldowns.getOrDefault(key, 0L);
        if (now < until) return true;
        horseCooldowns.remove(key);
        if (horseCooldowns.isEmpty()) cooldowns.remove(id);
        return false;
    }

    private static void setCooldown(AbstractHorse horse, String key, int seconds) {
        UUID id = horse.getUniqueId();
        cooldowns.putIfAbsent(id, new ConcurrentHashMap<>());
        cooldowns.get(id).put(key, System.currentTimeMillis() + seconds * 1000L);
    }

    private static boolean allowed(Player player, AbstractHorse horse) {
        return player != null && player.isOnline() && horse != null && horse.isValid()
                && player.getWorld().equals(horse.getWorld()) && WorldAccessPolicy.isEnabled(horse.getWorld())
                && config().getBoolean("traits.enabled", true);
    }

    public static void cleanupAll() {
        dashBoostTasks.values().forEach(ScheduledTaskHandle::cancel);
        ghostHorseTasks.values().forEach(ScheduledTaskHandle::cancel);
        boostedHorses.forEach((id, horse) -> {
            Double original = dashBoostOriginalSpeeds.get(id);
            BetterHorses.getInstance().scheduler().entity(horse, 1L, ignored -> {
                AttributeInstance speed = horse.getAttribute(AttributeResolver.generic("MOVEMENT_SPEED"));
                if (original != null && speed != null) speed.setBaseValue(original);
                clearDashBoostState(id);
            }, () -> clearDashBoostState(id));
        });
        ghostHorses.forEach((id, horse) -> BetterHorses.getInstance().scheduler().entity(horse, 1L, ignored -> {
            PotionEffect effect = horse.getPotionEffect(PotionEffectType.INVISIBILITY);
            if (effect != null && effect.getAmplifier() == 0) horse.removePotionEffect(PotionEffectType.INVISIBILITY);
        }, () -> ghostHorses.remove(id)));
        ghostPlayers.forEach((id, player) -> BetterHorses.getInstance().scheduler().entity(player, 1L, ignored -> {
            PotionEffect effect = player.getPotionEffect(PotionEffectType.INVISIBILITY);
            if (effect != null && effect.getAmplifier() == 0) player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }, () -> ghostPlayers.remove(id)));
        cooldowns.clear();
        activeGhostHorsePlayers.clear();
        activeGhostHorseMounts.clear();
        dashBoostTasks.clear();
        ghostHorseTasks.clear();
    }

    private static void clearDashBoostState(UUID id) {
        dashBoostTasks.remove(id); dashBoostOriginalSpeeds.remove(id); boostedHorses.remove(id);
    }

    private static void clearGhostState(UUID playerId, UUID horseId) {
        activeGhostHorsePlayers.remove(playerId); activeGhostHorseMounts.remove(horseId);
        ghostPlayers.remove(playerId); ghostHorses.remove(horseId); ghostHorseTasks.remove(horseId);
    }
}
