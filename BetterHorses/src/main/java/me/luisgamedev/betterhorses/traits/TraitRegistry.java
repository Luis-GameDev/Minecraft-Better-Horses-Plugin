package me.luisgamedev.betterhorses.traits;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import me.luisgamedev.betterhorses.utils.CooldownDisplay;
import org.bukkit.*;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.persistence.PersistentDataContainer;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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

    private static LanguageManager lang() { return BetterHorses.getInstance().getLang(); }
    private static FileConfiguration config() { return BetterHorses.getInstance().getConfig(); }
    private static final UUID DASH_MODIFIER = UUID.fromString("ef1d94d8-a92c-4bc7-8c27-a18275d60e24");
    private static final String COOLDOWN_PREFIX = "ability_cooldown_";
    private static final Map<UUID, ScheduledTaskHandle> dashBoostTasks = new ConcurrentHashMap<>();
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
        if (!passiveAllowed(player, horse, "fireheart")) return;
        OwnedPotionEffects.apply(player, PotionEffectType.FIRE_RESISTANCE, 40);
        OwnedPotionEffects.apply(horse, PotionEffectType.FIRE_RESISTANCE, 40);
    }

    public static void activateHeavenHooves(Player player, AbstractHorse horse, Event event) {
        FileConfiguration config = config();
        if (!config.getBoolean("traits.heavenhooves.enabled")) return;

        OwnedPotionEffects.apply(horse, PotionEffectType.SLOW_FALLING, 40);

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
        if (!config.getBoolean("traits.dashboost.enabled") || !allowed(player, horse)) return;
        String key = "dashboost";
        if (isOnCooldown(horse, key)) { showCooldownBar(player, horse, key); return; }
        AttributeInstance speed = horse.getAttribute(AttributeResolver.generic("MOVEMENT_SPEED"));
        if (speed == null) return;
        revertDashBoostIfActive(horse);
        int duration = Math.max(1, config.getInt("traits.dashboost.duration", 5));
        AttributeModifier modifier = new AttributeModifier(DASH_MODIFIER, "BetterHorses.dashboost", .5D,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        // Paper offers a non-persistent modifier. Spigot's portable fallback is removed on load
        // using the same owned UUID and the durable expiry marker below.
        try { speed.getClass().getMethod("addTransientModifier", AttributeModifier.class).invoke(speed, modifier); }
        catch (ReflectiveOperationException | LinkageError unavailable) { speed.addModifier(modifier); }
        horse.getPersistentDataContainer().set(dashExpiryKey(), PersistentDataType.LONG,
                System.currentTimeMillis() + duration * 1000L);
        boostedHorses.put(horse.getUniqueId(), horse);
        lang().send(player, "traits.dashboost-message");
        setCooldown(horse, key, config.getInt("traits.dashboost.cooldown", 30));
        ScheduledTaskHandle task = BetterHorses.getInstance().scheduler().entity(horse, Math.max(1L, duration * 20L),
                ignored -> revertDashBoostIfActive(horse), () -> clearDashBoostState(horse.getUniqueId()));
        dashBoostTasks.put(horse.getUniqueId(), task);
    }

    public static void revertDashBoostIfActive(AbstractHorse horse) {
        if (horse == null) return;
        clearDashBoostState(horse.getUniqueId());
        // Invalid/unloaded entities retain their marker until their next owning-thread load.
        if (!horse.isValid()) return;
        AttributeInstance speed = horse.getAttribute(AttributeResolver.generic("MOVEMENT_SPEED"));
        if (speed != null) for (AttributeModifier modifier : List.copyOf(speed.getModifiers())) {
            if (modifier.getUniqueId().equals(DASH_MODIFIER)) speed.removeModifier(modifier);
        }
        horse.getPersistentDataContainer().remove(dashExpiryKey());
    }

    /** Run on the horse's owning thread before training reconciliation after a load/re-enable. */
    public static void reconcileTemporaryState(AbstractHorse horse) {
        Long until = horse.getPersistentDataContainer().get(dashExpiryKey(), PersistentDataType.LONG);
        if (until != null && (until <= System.currentTimeMillis() || !dashBoostTasks.containsKey(horse.getUniqueId())))
            revertDashBoostIfActive(horse);
    }

    private static NamespacedKey dashExpiryKey() { return new NamespacedKey(BetterHorses.getInstance(), "dashboost_expires_at"); }

    private static void clearDashBoostState(UUID id) {
        var task = dashBoostTasks.remove(id);
        if (task != null) task.cancel();
        boostedHorses.remove(id);
    }

    public static void activateFeatherHooves(Player player, AbstractHorse horse) {
        if (!passiveAllowed(player, horse, "featherhooves")) return;
        OwnedPotionEffects.apply(player, PotionEffectType.SLOW_FALLING, 40);
        OwnedPotionEffects.apply(horse, PotionEffectType.SLOW_FALLING, 40);
    }

    public static boolean passiveAllowed(Player player, AbstractHorse horse, String trait) {
        return allowed(player, horse) && config().getBoolean("traits." + trait + ".enabled", false)
                && PermissionUtils.canUseTrait(player, trait);
    }

    public static void clearPassiveEffects(LivingEntity entity) {
        OwnedPotionEffects.clear(entity, PotionEffectType.FIRE_RESISTANCE);
        OwnedPotionEffects.clear(entity, PotionEffectType.SLOW_FALLING);
    }

    public static void activateGhostHorse(Player player, AbstractHorse horse) {
        FileConfiguration config = config();
        if (!config.getBoolean("traits.ghosthorse.enabled") || !allowed(player, horse)) return;
        String key = "ghosthorse";
        if (isOnCooldown(horse, key)) { showCooldownBar(player, horse, key); return; }
        int duration = Math.max(1, config.getInt("traits.ghosthorse.duration", 5));
        lang().send(player, "traits.ghosthorse-message");
        OwnedPotionEffects.apply(horse, PotionEffectType.INVISIBILITY, duration * 20);
        OwnedPotionEffects.apply(player, PotionEffectType.INVISIBILITY, duration * 20);
        ghostPlayers.put(player.getUniqueId(), player);
        ghostHorses.put(horse.getUniqueId(), horse);
        ScheduledTaskHandle task = BetterHorses.getInstance().scheduler().entity(horse, Math.max(1L, duration * 20L),
                ignored -> endGhostHorseIfActive(player, horse), () -> clearGhostState(player.getUniqueId(), horse.getUniqueId()));
        ghostHorseTasks.put(horse.getUniqueId(), task);
        setCooldown(horse, key, config.getInt("traits.ghosthorse.cooldown", 30));
    }

    public static void endGhostHorseIfActive(Player player, AbstractHorse horse) {
        if (player == null || horse == null) return;
        clearGhostState(player.getUniqueId(), horse.getUniqueId());
        runOwned(player, () -> OwnedPotionEffects.clear(player, PotionEffectType.INVISIBILITY));
        runOwned(horse, () -> OwnedPotionEffects.clear(horse, PotionEffectType.INVISIBILITY));
    }

    private static void clearGhostState(UUID playerId, UUID horseId) {
        ghostPlayers.remove(playerId); ghostHorses.remove(horseId);
        var task = ghostHorseTasks.remove(horseId);
        if (task != null) task.cancel();
    }

    public static void activateSkyburst(Player player, AbstractHorse horse) {
        FileConfiguration config = config();
        if (!config.getBoolean("traits.skyburst.enabled")) return;

        double radius = config.getDouble("traits.skyburst.radius", 3.0);
        player.getWorld().spawnParticle(Particle.CLOUD, horse.getLocation(), 20, 0.5, 0.1, 0.5, 0.01);
        player.playSound(horse.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);

        for (Entity entity : horse.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity target && entity != player && entity != horse) {
                Location origin = horse.getLocation().clone();
                BetterHorses.getInstance().scheduler().entity(target, 1L, ignored -> {
                if (!pendingHitAllowed(player, horse, target, origin, radius)) return;
                EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(player, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 0.0);
                Bukkit.getPluginManager().callEvent(event);
                if (!event.isCancelled() && pendingHitAllowed(player, horse, target, origin, radius)) {
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

        Particle revenantParticle = TraitParticleResolver.getTraitParticle("revenantcurse", "WITCH", "SPELL_WITCH");
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
                if (!pendingHitAllowed(player, horse, target, origin, radius)) return;
                EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(player, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, 0.0);
                Bukkit.getPluginManager().callEvent(event);

                if (!event.isCancelled() && pendingHitAllowed(player, horse, target, origin, radius)) {
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

    private static boolean pendingHitAllowed(Player player, AbstractHorse horse, LivingEntity target,
                                             Location origin, double radius) {
        // A target callback does not own a rider or mount which has since teleported away.
        if (!Bukkit.isOwnedByCurrentRegion(player) || !Bukkit.isOwnedByCurrentRegion(horse)
                || !Bukkit.isOwnedByCurrentRegion(target)) return false;
        if (!allowed(player, horse) || !target.isValid() || target.isDead()
                || target.getWorld() != origin.getWorld() || horse.getWorld() != origin.getWorld()) return false;
        Location current = target.getLocation();
        return Math.abs(current.getX() - origin.getX()) <= radius
                && Math.abs(current.getY() - origin.getY()) <= radius
                && Math.abs(current.getZ() - origin.getZ()) <= radius;
    }

    private static void showCooldownBar(Player player, AbstractHorse horse, String key) {
        FileConfiguration config = config();
        int fullCooldown = config.getInt("traits." + key + ".cooldown", 30);
        long now = System.currentTimeMillis();
        long until = horse.getPersistentDataContainer().getOrDefault(cooldownKey(key), PersistentDataType.LONG, 0L);
        double secondsLeft = (until - now) / 1000.0;
        String name = lang().getRaw(player, "traits." + key);

        if (secondsLeft > 0) {
            CooldownDisplay.showCooldown(secondsLeft, fullCooldown, player, name);
        }
    }

    private static boolean isOnCooldown(AbstractHorse horse, String key) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        long until = data.getOrDefault(cooldownKey(key), PersistentDataType.LONG, 0L);
        if (System.currentTimeMillis() < until) return true;
        data.remove(cooldownKey(key));
        return false;
    }

    private static void setCooldown(AbstractHorse horse, String key, int seconds) {
        horse.getPersistentDataContainer().set(cooldownKey(key), PersistentDataType.LONG,
                System.currentTimeMillis() + Math.max(0L, seconds) * 1000L);
    }

    private static NamespacedKey cooldownKey(String trait) {
        return new NamespacedKey(BetterHorses.getInstance(), COOLDOWN_PREFIX + trait);
    }

    /** Preserves active ability cooldowns across captures and new live entity UUIDs. */
    public static void copyCooldowns(PersistentDataContainer source, PersistentDataContainer target) {
        String namespace = BetterHorses.getInstance().getName().toLowerCase(java.util.Locale.ROOT);
        for (NamespacedKey key : source.getKeys()) {
            if (key.getNamespace().equals(namespace) && key.getKey().startsWith(COOLDOWN_PREFIX)
                    && source.has(key, PersistentDataType.LONG))
                target.set(key, PersistentDataType.LONG, source.get(key, PersistentDataType.LONG));
        }
    }

    private static boolean allowed(Player player, AbstractHorse horse) {
        return player != null && player.isOnline() && horse != null && horse.isValid()
                && player.getVehicle() == horse && player.getWorld().equals(horse.getWorld()) && WorldAccessPolicy.isEnabled(horse.getWorld())
                && config().getBoolean("traits.enabled", true);
    }

    public static void cleanupAll() {
        if (!BetterHorses.getInstance().isEnabled()) { shutdown(); return; }
        var horses = List.copyOf(boostedHorses.values());
        var ghostMounts = List.copyOf(ghostHorses.values());
        var players = List.copyOf(ghostPlayers.values());
        cancelTrackedTasks();
        horses.forEach(horse -> runOwned(horse, () -> revertDashBoostIfActive(horse)));
        ghostMounts.forEach(horse -> runOwned(horse, () -> OwnedPotionEffects.clear(horse, PotionEffectType.INVISIBILITY)));
        players.forEach(player -> runOwned(player, () -> OwnedPotionEffects.clear(player, PotionEffectType.INVISIBILITY)));
    }

    /** No disabled-plugin scheduling or foreign-region access during teardown. */
    public static void shutdown() {
        var horses = List.copyOf(boostedHorses.values());
        var ghostMounts = List.copyOf(ghostHorses.values());
        var players = List.copyOf(ghostPlayers.values());
        cancelTrackedTasks();
        for (AbstractHorse horse : horses) if (Bukkit.isOwnedByCurrentRegion(horse)) revertDashBoostIfActive(horse);
        for (AbstractHorse horse : ghostMounts) if (Bukkit.isOwnedByCurrentRegion(horse)) OwnedPotionEffects.clear(horse, PotionEffectType.INVISIBILITY);
        for (Player player : players) if (Bukkit.isOwnedByCurrentRegion(player)) OwnedPotionEffects.clear(player, PotionEffectType.INVISIBILITY);
        // Non-persistent modifiers never enter a saved entity. Any portable legacy modifier
        // retains its durable marker for owning-thread reconciliation at the next load.
    }

    private static void runOwned(Entity entity, Runnable action) {
        if (Bukkit.isOwnedByCurrentRegion(entity)) { action.run(); return; }
        if (BetterHorses.getInstance().isEnabled())
            BetterHorses.getInstance().scheduler().entity(entity, 1L, ignored -> action.run(), () -> OwnedPotionEffects.forget(entity.getUniqueId()));
    }

    private static void cancelTrackedTasks() {
        dashBoostTasks.values().forEach(task -> task.cancel());
        ghostHorseTasks.values().forEach(task -> task.cancel());
        dashBoostTasks.clear(); ghostHorseTasks.clear();
        boostedHorses.clear(); ghostHorses.clear(); ghostPlayers.clear();
    }
}
