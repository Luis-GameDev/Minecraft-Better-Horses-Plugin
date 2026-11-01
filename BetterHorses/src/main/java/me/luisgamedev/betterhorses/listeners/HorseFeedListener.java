package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.training.TrainingManager;
import me.luisgamedev.betterhorses.utils.MountConfig;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Permission checks precede vanilla feeding; training is awarded only after its successful result. */
public class HorseFeedListener implements Listener, ListenerLifecycle {
    private final Map<AttemptKey, Object> pending = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    @EventHandler(ignoreCancelled = true)
    public void onHorseFeed(PlayerInteractEntityEvent event) {
        if (event.isCancelled() || !(event.getRightClicked() instanceof AbstractHorse horse)) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        SupportedMountType mountType = SupportedMountType.fromEntity(horse).orElse(null);
        if (mountType == null || !mountType.isEnabled(config) || item == null || !isFood(mountType, item.getType())) return;
        if (!player.hasPermission(PermissionUtils.FEED)) {
            event.setCancelled(true);
            return;
        }
        if (!horse.isAdult() && MountConfig.isGrowthEnabled(config, mountType)) {
            event.setCancelled(true);
            return;
        }
        if (!horse.isTamed() || !horse.isAdult()) return;
        long cooldownMillis = milliseconds(config.getLong("settings.breeding-cooldown", 0L));
        if (player.hasPermission(PermissionUtils.COOLDOWN_BYPASS) || cooldownMillis == 0L) {
            // Vanilla installs its own positive breeding age after the birth event.
            // Exemptions must clear that wait before the next native food interaction.
            if (horse.getAge() > 0) horse.setAge(0);
            return;
        }
        PersistentDataContainer data = horse.getPersistentDataContainer();
        String gender = data.getOrDefault(BetterHorseKeys.GENDER, PersistentDataType.STRING, "UNKNOWN");
        if (config.getBoolean("settings.male-ignore-cooldown", false) && "male".equalsIgnoreCase(gender)) {
            horse.setAge(0);
            return;
        }
        long lastBreed = data.getOrDefault(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG, 0L);
        if (lastBreed > 0L && System.currentTimeMillis() - lastBreed < cooldownMillis) {
            event.setCancelled(true);
            return;
        }
        data.remove(BetterHorseKeys.COOLDOWN);
        horse.setAge(0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void observeFeeding(PlayerInteractEntityEvent event) {
        if (!running || event.isCancelled() || !(event.getRightClicked() instanceof AbstractHorse horse)) return;
        Player player = event.getPlayer();
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        SupportedMountType type = SupportedMountType.fromEntity(horse).orElse(null);
        ItemStack food = player.getInventory().getItem(event.getHand());
        if (food == null || type == null || !type.isEnabled(config) || !isFood(type, food.getType()) || !horse.isTamed()
                || !player.hasPermission(PermissionUtils.FEED) || !player.hasPermission(PermissionUtils.TRAINING)
                || !TrainingManager.isTrainingEnabled(config) || !TrainingManager.isCategoryEnabled(config, "feeding")
                || TrainingManager.getFoodTrainingValue(food.getType()) <= 0.0D) return;
        if (!horse.isAdult() && MountConfig.isGrowthEnabled(config, type)) return;

        FeedingAttempt attempt = new FeedingAttempt(food.clone(), horse.getHealth(), horse.getAge(), horse.getDomestication(),
                horse.getLoveModeTicks(), horse.getTicksLived(), player.getGameMode());
        AttemptKey key = new AttemptKey(player.getUniqueId(), event.getHand());
        // InteractAtEntity and InteractEntity can both describe one native interaction.
        Object ticket = new Object();
        pending.put(key, ticket);
        Runnable retired = () -> pending.remove(key, ticket);
        try {
            scheduleConfirmation(player, horse, () -> {
                if (!pending.remove(key, ticket) || !running || event.isCancelled() || !ownsHorse(horse)) return;
                if (!player.isOnline() || player.isDead() || !horse.isValid() || horse.isDead() || !horse.isTamed()
                        || player.getGameMode() != attempt.mode() || !player.getWorld().equals(horse.getWorld())
                        || !WorldAccessPolicy.isEnabled(horse.getWorld())
                        || player.getLocation().distanceSquared(horse.getLocation()) > 64.0D
                        || !player.hasPermission(PermissionUtils.FEED) || !player.hasPermission(PermissionUtils.TRAINING)) return;
                FileConfiguration current = BetterHorses.getInstance().getConfig();
                if (!type.isEnabled(current) || !TrainingManager.isTrainingEnabled(current)
                        || !TrainingManager.isCategoryEnabled(current, "feeding")) return;
                if (!attempt.accepted(horse, player.getInventory().getItem(event.getHand()))) return;
                tryAddFeedingTraining(horse, current, TrainingManager.getFoodTrainingValue(attempt.food().getType()),
                        System.currentTimeMillis());
            }, retired);
        } catch (RuntimeException exception) {
            retired.run();
            throw exception;
        }
    }

    protected void scheduleConfirmation(Player player, AbstractHorse horse, Runnable action, Runnable retired) {
        Bukkit.getScheduler().runTask(BetterHorses.getInstance(), action);
    }

    protected boolean ownsHorse(AbstractHorse horse) { return true; }

    static boolean isFood(SupportedMountType mountType, Material material) {
        return switch (mountType) {
            case CAMEL -> material == Material.CACTUS;
            case LLAMA -> material == Material.WHEAT || material == Material.HAY_BLOCK;
            case SKELETON_HORSE, ZOMBIE_HORSE -> false;
            default -> material == Material.GOLDEN_APPLE || material == Material.ENCHANTED_GOLDEN_APPLE
                    || material == Material.SUGAR || material == Material.HAY_BLOCK || material == Material.WHEAT
                    || material == Material.APPLE || material == Material.GOLDEN_CARROT;
        };
    }

    private void tryAddFeedingTraining(AbstractHorse horse, FileConfiguration config, double value, long now) {
        if (!Double.isFinite(value) || value <= 0.0D) return;
        PersistentDataContainer data = horse.getPersistentDataContainer();
        long cooldownMillis = milliseconds(config.getLong("training.categories.feeding.cooldown-seconds", 0L));
        long previous = data.getOrDefault(BetterHorseKeys.TRAINING_FEED_COOLDOWN, PersistentDataType.LONG, 0L);
        if (cooldownMillis > 0L && previous > 0L && now - previous < cooldownMillis) return;
        TrainingManager.addFeedingUnits(horse, value);
        if (cooldownMillis > 0L) data.set(BetterHorseKeys.TRAINING_FEED_COOLDOWN, PersistentDataType.LONG, now);
        horse.getWorld().spawnParticle(Particle.COMPOSTER, horse.getEyeLocation(), 5);
    }

    private static long milliseconds(long seconds) {
        return seconds <= 0L ? 0L : seconds > Long.MAX_VALUE / 1000L ? Long.MAX_VALUE : seconds * 1000L;
    }

    @Override public void onConfigurationReload() { pending.clear(); }
    @Override public void shutdown() { running = false; pending.clear(); }

    private record AttemptKey(UUID player, EquipmentSlot hand) { }

    record FeedingAttempt(ItemStack food, double health, int age, int temper, int loveTicks, int ticks, GameMode mode) {
        boolean accepted(AbstractHorse horse, ItemStack remaining) {
            // Native age progress by itself is not evidence that feeding was accepted.
            int elapsed = Math.max(1, horse.getTicksLived() - ticks);
            boolean effect = horse.getHealth() > health || horse.getDomestication() > temper
                    || horse.getLoveModeTicks() > loveTicks || (age < 0 && (long) horse.getAge() - age > elapsed);
            if (!effect) return false;
            if (mode == GameMode.CREATIVE) return true;
            if (remaining == null || remaining.getAmount() == 0 || remaining.getType() == Material.AIR
                    || remaining.getType() == Material.CAVE_AIR || remaining.getType() == Material.VOID_AIR) return food.getAmount() == 1;
            return food.getAmount() - remaining.getAmount() == 1 && food.isSimilar(remaining);
        }
    }
}
