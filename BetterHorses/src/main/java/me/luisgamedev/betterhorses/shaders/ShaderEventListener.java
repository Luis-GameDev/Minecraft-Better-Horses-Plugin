package me.luisgamedev.betterhorses.shaders;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.Location;

/** State-change signals; all evaluation is deferred to the player's scheduler. */
final class ShaderEventListener implements Listener {
    private final BetterHorses plugin;
    private final PostProcessingManager manager;
    private final Listener optional = new Listener() { };

    ShaderEventListener(BetterHorses plugin, PostProcessingManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    void synchronize() {
        shutdown();
        if (!manager.isActive()) return;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (manager.uses("frozen", "onfire", "placeholder")) {
            registerOptional("EntityInsideBlockEvent", event -> {
                Entity entity = (Entity) event.getClass().getMethod("getEntity").invoke(event);
                if (entity instanceof Player player) {
                    Block block = (Block) event.getClass().getMethod("getBlock").invoke(event);
                    manager.contact(player, block.getType());
                }
            });
        }
        if (manager.uses("biome", "placeholder")) {
            // Paper enables movement event work when a listener is registered. Only opt in
            // when mounted location/placeholder rules actually need those signals.
            registerOptional("EntityMoveEvent", event -> {
                Entity entity = (Entity) event.getClass().getMethod("getEntity").invoke(event);
                if (entity instanceof Player) return;
                Location from = (Location) event.getClass().getMethod("getFrom").invoke(event);
                Location to = (Location) event.getClass().getMethod("getTo").invoke(event);
                if (blockChanged(from, to)) manager.requestMountUpdate(entity);
            });
        }
        if (manager.uses("mounted", "unmounted", "gender", "mounttype", "traited", "owner", "growth", "neutered", "placeholder")) {
            registerOptional("EntityRemoveFromWorldEvent", event -> {
                Entity entity = (Entity) event.getClass().getMethod("getEntity").invoke(event);
                if (entity instanceof Player player) manager.requestUpdate(player);
                else manager.requestMountUpdate(entity);
            });
        }
    }

    void shutdown() {
        HandlerList.unregisterAll(this);
        HandlerList.unregisterAll(optional);
    }

    @SuppressWarnings("unchecked")
    private void registerOptional(String name, Signal signal) {
        for (String prefix : new String[]{"io.papermc.paper.event.entity.", "com.destroystokyo.paper.event.entity."}) {
            try {
                Class<?> candidate = Class.forName(prefix + name, false, plugin.getClass().getClassLoader());
                if (!Event.class.isAssignableFrom(candidate)) continue;
                Class<? extends Event> type = (Class<? extends Event>) candidate;
                plugin.getServer().getPluginManager().registerEvent(type, optional, EventPriority.MONITOR,
                        (ignored, event) -> {
                            if (!type.isInstance(event)) return;
                            try { signal.accept(event); }
                            catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                                manager.optionalEventFailure(name);
                            }
                        }, plugin, true);
                return;
            } catch (ClassNotFoundException ignored) {
                // Supported forks can retain the old Paper event package.
            } catch (RuntimeException | LinkageError exception) {
                manager.optionalEventFailure(name);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player) manager.requestUpdate(player);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) manager.requestUpdate(player);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom(), to = event.getTo();
        if (to == null) return;
        if (from.getWorld() != to.getWorld() || from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            if (manager.uses("frozen", "placeholder")) manager.resetTransitions(event.getPlayer());
            manager.activity(event.getPlayer(), "frozen", "onfire", "swimming");
            if (blockChanged(from, to)) manager.activity(event.getPlayer(), "biome", "world", "environment");
        } else if (from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch()) manager.activity(event.getPlayer());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { reset(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) { reset(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) { reset(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) { manager.activity(event.getPlayer(), "sneaking"); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) { manager.activity(event.getPlayer(), "sprinting"); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFly(PlayerToggleFlightEvent event) { manager.activity(event.getPlayer(), "flying"); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) { reset(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player) manager.activity(player, "gliding");
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwim(EntityToggleSwimEvent event) {
        if (event.getEntity() instanceof Player player) manager.activity(player, "swimming");
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (event.getEntity() instanceof Player player) manager.activity(player, "onfire");
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) manager.activity(player, "onfire", "frozen");
        if (event instanceof EntityDamageByEntityEvent attack) {
            if (attack.getDamager() instanceof Player player) manager.activity(player);
            else if (attack.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) manager.activity(player);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player player) manager.requestUpdate(player);
        else manager.requestMountUpdate(event.getEntity());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player) manager.activity(player);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) manager.activity(player);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotion(EntityPotionEffectEvent event) {
        if (event.getEntity() instanceof Player player) manager.activity(player);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) { manager.activity(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) { manager.activity(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) { manager.activity(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPermissions(PlayerCommandSendEvent event) { manager.activity(event.getPlayer(), "permission"); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) reset(player);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) reset(player);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) reset(player);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (manager.uses("placeholder") || (manager.uses("biome") && blockChanged(event.getFrom(), event.getTo()))) manager.requestMountUpdate(event.getVehicle());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent event) {
        if (manager.uses("weather", "placeholder")) manager.worldChanged(event.getWorld(), false);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThunder(ThunderChangeEvent event) {
        if (manager.uses("weather", "placeholder")) manager.worldChanged(event.getWorld(), false);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { powderBlock(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { powderBlock(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) { powderBlock(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBlock(EntityChangeBlockEvent event) { powderBlock(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) { powderBlocks(event.blockList()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) { powderBlocks(event.blockList()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) { powderBlock(event.getToBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) { powderBlocks(event.getBlocks()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) { powderBlocks(event.getBlocks()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) { powderBlock(event.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) { powderBlock(event.getBlock()); }

    private void powderBlocks(java.util.List<Block> blocks) {
        if (!manager.uses("frozen", "placeholder")) return;
        for (Block block : blocks) {
            if (block.getType() == Material.POWDER_SNOW) { manager.worldChanged(block.getWorld(), true); return; }
        }
    }
    private void powderBlock(Block block) {
        if (block.getType() == Material.POWDER_SNOW && manager.uses("frozen", "placeholder")) manager.worldChanged(block.getWorld(), true);
    }
    private void reset(Player player) { manager.resetTransitions(player); manager.requestUpdate(player); }
    private static boolean blockChanged(Location from, Location to) {
        return to != null && (from.getWorld() != to.getWorld() || from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ());
    }
    private interface Signal { void accept(Event event) throws ReflectiveOperationException; }
}
