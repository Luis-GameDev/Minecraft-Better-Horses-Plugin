package me.luisgamedev.betterhorses.listeners;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.scheduler.ScheduledTaskHandle;
import me.luisgamedev.betterhorses.training.TrainingManager;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** One-shot reconciliation after configuration changes and when stored mounts become accessible. */
public final class TrainingDataLifecycleListener implements Listener, ListenerLifecycle {
    private final BetterHorses plugin;
    private final NamespacedKey modelKey;
    private final Set<UUID> pendingHorses = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<PendingTask> tasks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean snapshotPending = new AtomicBoolean();
    private volatile boolean running;
    private volatile boolean stopped;

    public TrainingDataLifecycleListener(BetterHorses plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        modelKey = new NamespacedKey(plugin, "training_stat_model");
    }

    /** Listener registration belongs to ListenerRegistrationManager. */
    public synchronized void start() {
        if (running || stopped) return;
        running = true;
        reconcileLoadedData();
    }

    @Override
    public void onConfigurationReload() {
        if (available()) reconcileLoadedData();
    }

    @Override
    public synchronized void shutdown() {
        if (stopped) return;
        stopped = true;
        running = false;
        tasks.forEach(PendingTask::cancel);
        tasks.clear();
        pendingHorses.clear();
        pendingPlayers.clear();
        snapshotPending.set(false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { reconcilePlayer(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) reconcilePlayer(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) { reconcilePlayer(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) reconcilePlayer(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) reconcilePlayer(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) reconcilePlayer(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) { reconcilePlayer(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) { reconcileHorse(event.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdded(EntityAddToWorldEvent event) { reconcileHorse(event.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!available()) return;
        for (Entity entity : event.getEntities()) reconcileHorse(entity);
    }

    private boolean available() { return running && !stopped && plugin.isEnabled(); }

    private void reconcileLoadedData() {
        if (!available() || !snapshotPending.compareAndSet(false, true)) return;
        PendingTask pending = task(() -> snapshotPending.set(false));
        if (pending == null) return;
        try {
            pending.attach(plugin.scheduler().global(ignored -> pending.run(() -> {
                for (Player player : Bukkit.getOnlinePlayers()) reconcilePlayer(player);
                Bukkit.getWorlds().forEach(world -> {
                    if (!WorldAccessPolicy.isEnabled(world)) return;
                    for (Chunk chunk : world.getLoadedChunks()) {
                        Location location = new Location(world, chunk.getX() << 4, 0, chunk.getZ() << 4);
                        PendingTask region = task(() -> {});
                        if (region == null) return;
                        try {
                            region.attach(plugin.scheduler().region(location, 1L, task -> region.run(() -> {
                                if (!Bukkit.isOwnedByCurrentRegion(location) || !chunk.isLoaded() || !chunk.isEntitiesLoaded()) return;
                                for (Entity entity : chunk.getEntities()) reconcileHorse(entity);
                            })));
                        } catch (RuntimeException exception) {
                            region.finish();
                            throw exception;
                        }
                    }
                });
            })));
        } catch (RuntimeException exception) {
            pending.finish();
            throw exception;
        }
    }

    private void reconcileHorse(Entity entity) {
        if (!available() || !(entity instanceof AbstractHorse horse)) return;
        UUID id = horse.getUniqueId();
        if (!pendingHorses.add(id)) return;
        PendingTask pending = task(() -> pendingHorses.remove(id));
        if (pending == null) return;
        try {
            pending.attach(plugin.scheduler().entity(horse, 1L, ignored -> pending.run(() -> {
                if (!horse.isValid() || horse.isDead() || !Bukkit.isOwnedByCurrentRegion(horse)
                        || !WorldAccessPolicy.isEnabled(horse.getWorld())) return;
                SupportedMountType type = SupportedMountType.fromEntity(horse).orElse(null);
                if (type == null || !type.isEnabled(plugin.getConfig())) return;
                me.luisgamedev.betterhorses.traits.TraitRegistry.reconcileTemporaryState(horse);
                HorseStepHeightListener.reconcileTemporaryState(horse);
                if (available() && hasStoredTraining(horse.getPersistentDataContainer())) TrainingManager.recalculateAndApplyBonuses(horse);
            }), pending::finish));
        } catch (RuntimeException exception) {
            pending.finish();
            throw exception;
        }
    }

    private boolean hasStoredTraining(PersistentDataContainer data) {
        return data.has(BetterHorseKeys.BASE_HEALTH, PersistentDataType.DOUBLE)
                || data.has(BetterHorseKeys.BASE_SPEED, PersistentDataType.DOUBLE)
                || data.has(BetterHorseKeys.BASE_JUMP, PersistentDataType.DOUBLE)
                || data.has(BetterHorseKeys.TRAINING_RIDING_UNITS, PersistentDataType.DOUBLE)
                || data.has(BetterHorseKeys.TRAINING_BRUSHING_UNITS, PersistentDataType.DOUBLE)
                || data.has(BetterHorseKeys.TRAINING_FEEDING_UNITS, PersistentDataType.DOUBLE)
                || data.has(modelKey, PersistentDataType.INTEGER);
    }

    private void reconcilePlayer(Player player) {
        if (!available()) return;
        UUID id = player.getUniqueId();
        if (!pendingPlayers.add(id)) return;
        PendingTask pending = task(() -> pendingPlayers.remove(id));
        if (pending == null) return;
        try {
            pending.attach(plugin.scheduler().entity(player, 1L, ignored -> pending.run(() -> {
                if (!player.isOnline() || !Bukkit.isOwnedByCurrentRegion(player)
                        || !WorldAccessPolicy.isEnabled(player.getWorld())) return;
                Inventory inventory = player.getInventory();
                normalizeInventory(inventory, player);
                if (!available() || !Bukkit.isOwnedByCurrentRegion(player)
                        || !WorldAccessPolicy.isEnabled(player.getWorld())) return;
                ItemStack cursor = player.getItemOnCursor();
                if (BetterHorsesAPI.normalizeStoredStats(cursor, player) && available()) player.setItemOnCursor(cursor);
                if (!available()) return;
                Inventory top = player.getOpenInventory().getTopInventory();
                if (top != inventory && isSafeVisibleInventory(top, player)) normalizeInventory(top, player);
            }), pending::finish));
        } catch (RuntimeException exception) {
            pending.finish();
            throw exception;
        }
    }

    private boolean isSafeVisibleInventory(Inventory inventory, Player player) {
        if (inventory == player.getEnderChest() || inventory.getType() == InventoryType.CRAFTING) return true;
        Location location = inventory.getLocation();
        if (location == null || !WorldAccessPolicy.isEnabled(location.getWorld()) || !Bukkit.isOwnedByCurrentRegion(location)) return false;
        if (inventory instanceof DoubleChestInventory chest) {
            return ownsLocation(chest.getLeftSide().getLocation()) && ownsLocation(chest.getRightSide().getLocation());
        }
        return true;
    }

    private boolean ownsLocation(Location location) {
        return location != null && WorldAccessPolicy.isEnabled(location.getWorld()) && Bukkit.isOwnedByCurrentRegion(location);
    }

    private void normalizeInventory(Inventory inventory, Player player) {
        for (int slot = 0; available() && slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (BetterHorsesAPI.normalizeStoredStats(item, player) && available()) inventory.setItem(slot, item);
        }
    }

    private PendingTask task(Runnable completion) {
        PendingTask pending = new PendingTask(completion);
        tasks.add(pending);
        if (!available()) {
            pending.cancel();
            return null;
        }
        return pending;
    }

    /** Own every bootstrap and entity task, including retirement and shutdown races. */
    private final class PendingTask {
        private final Runnable completion;
        private final AtomicBoolean done = new AtomicBoolean();
        private ScheduledTaskHandle handle;

        private PendingTask(Runnable completion) { this.completion = completion; }

        private synchronized void attach(ScheduledTaskHandle handle) {
            this.handle = handle;
            if (done.get() || !available()) handle.cancel();
            if (handle.isDone()) finish();
        }

        private void run(Runnable work) {
            try { if (!done.get() && available()) work.run(); }
            finally { finish(); }
        }

        private void finish() {
            if (done.compareAndSet(false, true)) {
                tasks.remove(this);
                completion.run();
            }
        }

        private synchronized void cancel() {
            if (handle != null) handle.cancel();
            finish();
        }
    }
}
