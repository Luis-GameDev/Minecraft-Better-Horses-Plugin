package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.training.TrainingManager;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Reconciles persisted training data when it becomes available or configuration changes. */
public final class TrainingDataLifecycleListener implements Listener, ListenerLifecycle {
    private final BetterHorses plugin;
    private final NamespacedKey statModelKey;
    private final Map<UUID, BukkitTask> horses = new HashMap<>();
    private final Map<UUID, BukkitTask> players = new HashMap<>();
    private BukkitTask reconciliation;
    private boolean active;

    public TrainingDataLifecycleListener(BetterHorses plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.statModelKey = new NamespacedKey(plugin, "training_stat_model");
    }

    /** Listener registration is owned by ListenerRegistrationManager. */
    public void start() {
        if (active) return;
        active = true;
        requestReconciliation();
    }

    @Override
    public void onConfigurationReload() {
        if (active) requestReconciliation();
    }

    @Override
    public void shutdown() {
        active = false;
        if (reconciliation != null) reconciliation.cancel();
        reconciliation = null;
        horses.values().forEach(BukkitTask::cancel);
        players.values().forEach(BukkitTask::cancel);
        horses.clear();
        players.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) { requestHorse(event.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoaded(EntitiesLoadEvent event) { event.getEntities().forEach(this::requestHorse); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { requestPlayer(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) requestPlayer(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) { requestPlayer(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) requestPlayer(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) requestPlayer(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) requestPlayer(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) { requestPlayer(event.getPlayer()); }

    private void requestReconciliation() {
        if (!active || reconciliation != null) return;
        reconciliation = new BukkitRunnable() {
            @Override
            public void run() {
                reconciliation = null;
                if (!active) return;
                for (World world : Bukkit.getWorlds()) {
                    if (!WorldAccessPolicy.isEnabled(world)) continue;
                    for (AbstractHorse horse : world.getEntitiesByClass(AbstractHorse.class)) requestHorse(horse);
                }
                for (Player player : Bukkit.getOnlinePlayers()) requestPlayer(player);
            }
        }.runTask(plugin);
    }

    private void requestHorse(Entity entity) {
        if (!active || !(entity instanceof AbstractHorse horse)) return;
        UUID id = horse.getUniqueId();
        if (horses.containsKey(id)) return;
        horses.put(id, new BukkitRunnable() {
            @Override
            public void run() {
                horses.remove(id);
                if (!active || !horse.isValid() || horse.isDead() || !WorldAccessPolicy.isEnabled(horse.getWorld())) return;
                SupportedMountType type = SupportedMountType.fromEntity(horse).orElse(null);
                if (type == null || !type.isEnabled(plugin.getConfig())) return;
                me.luisgamedev.betterhorses.traits.TraitRegistry.reconcileTemporaryState(horse);
                HorseStepHeightListener.reconcileTemporaryState(horse);
                if (hasTrainingData(horse.getPersistentDataContainer())) TrainingManager.recalculateAndApplyBonuses(horse);
            }
        }.runTask(plugin));
    }

    private boolean hasTrainingData(PersistentDataContainer data) {
        return data.has(statModelKey, PersistentDataType.INTEGER)
                || data.has(BetterHorseKeys.BASE_HEALTH, PersistentDataType.DOUBLE)
                || data.has(BetterHorseKeys.BASE_SPEED, PersistentDataType.DOUBLE)
                || data.has(BetterHorseKeys.BASE_JUMP, PersistentDataType.DOUBLE)
                || data.has(BetterHorseKeys.TRAINING_RIDING_UNITS, PersistentDataType.DOUBLE)
                || data.has(BetterHorseKeys.TRAINING_BRUSHING_UNITS, PersistentDataType.DOUBLE)
                || data.has(BetterHorseKeys.TRAINING_FEEDING_UNITS, PersistentDataType.DOUBLE);
    }

    private void requestPlayer(Player player) {
        if (!active) return;
        UUID id = player.getUniqueId();
        if (players.containsKey(id)) return;
        players.put(id, new BukkitRunnable() {
            @Override
            public void run() {
                players.remove(id);
                if (!active || !player.isOnline() || !WorldAccessPolicy.isEnabled(player.getWorld())) return;
                Inventory inventory = player.getInventory();
                normalizeInventory(inventory, player);
                if (!active) return;
                Inventory top = player.getOpenInventory().getTopInventory();
                if (top != inventory) normalizeInventory(top, player);
                if (!active) return;
                ItemStack cursor = player.getItemOnCursor();
                if (BetterHorsesAPI.normalizeStoredStats(cursor, player)) player.setItemOnCursor(cursor);
            }
        }.runTask(plugin));
    }

    private void normalizeInventory(Inventory inventory, Player player) {
        for (int slot = 0; active && slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (BetterHorsesAPI.normalizeStoredStats(item, player)) inventory.setItem(slot, item);
        }
    }
}
