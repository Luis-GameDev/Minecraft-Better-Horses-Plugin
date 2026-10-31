package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.traits.TraitRegistry;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Short, owned passive effects are refreshed even for an idle mounted rider. */
public class PassiveTraitListener implements Listener, ListenerLifecycle {
    private final BetterHorses plugin = BetterHorses.getInstance();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public PassiveTraitListener() { discoverMountedPlayers(); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player) later(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) stop(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) { stop(event.getPlayer().getUniqueId()); }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        ensure(player);
        if (player.getVehicle() instanceof AbstractHorse horse
                && org.bukkit.Bukkit.isOwnedByCurrentRegion(horse) && trait(horse).equals("frosthooves") && TraitRegistry.passiveAllowed(player, horse, "frosthooves"))
            TraitRegistry.activateFrostHooves(player, horse);
    }

    private void later(Player player) {
        if (plugin.isEnabled()) plugin.scheduler().entity(player, 1L, ignored -> ensure(player), () -> { });
    }

    private void ensure(Player player) {
        if (!(player.getVehicle() instanceof AbstractHorse mount)) { stop(player.getUniqueId()); return; }
        UUID id = player.getUniqueId();
        Session previous = sessions.get(id);
        if (previous != null && previous.mount != mount) stop(id);
        if (!refresh(player, mount)) { stop(id); return; }
        if (previous != null && previous.mount == mount) return;
        Session session = new Session(player, mount);
        sessions.put(id, session);
        var task = plugin.scheduler().entityRepeating(player, 1L, 10L, scheduled -> {
            if (!refresh(player, mount)) { stop(id); scheduled.cancel(); }
        }, () -> { if (sessions.remove(id, session)) me.luisgamedev.betterhorses.traits.OwnedPotionEffects.forget(id); });
        session.cancel = task::cancel;
    }

    private boolean refresh(Player player, AbstractHorse mount) {
        if (!org.bukkit.Bukkit.isOwnedByCurrentRegion(mount)) return false;
        if (!player.isOnline() || player.isDead() || player.getVehicle() != mount || !mount.isValid()
                || !SupportedMountType.isSupported(mount) || !WorldAccessPolicy.isEnabled(mount.getWorld())) return false;
        String trait = trait(mount);
        if (!TraitRegistry.passiveAllowed(player, mount, trait)) return false;
        switch (trait) {
            case "fireheart" -> TraitRegistry.activateFireheart(player, mount);
            case "featherhooves" -> TraitRegistry.activateFeatherHooves(player, mount);
            case "heavenhooves" -> TraitRegistry.activateHeavenHooves(player, mount, null);
            case "frosthooves" -> { }
            default -> { return false; }
        }
        return true;
    }

    private String trait(AbstractHorse mount) {
        return mount.getPersistentDataContainer().getOrDefault(BetterHorseKeys.TRAIT, PersistentDataType.STRING, "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    private void stop(UUID id) {
        Session session = sessions.remove(id);
        if (session == null) return;
        session.cancel.run();
        clear(session.player);
        clear(session.mount);
    }

    private void clear(LivingEntity entity) {
        Runnable action = () -> {
            if (entity.isValid()) TraitRegistry.clearPassiveEffects(entity);
            else me.luisgamedev.betterhorses.traits.OwnedPotionEffects.forget(entity.getUniqueId());
        };
        if (org.bukkit.Bukkit.isOwnedByCurrentRegion(entity)) action.run();
        else if (plugin.isEnabled()) plugin.scheduler().entity(entity, 1L, ignored -> action.run(),
                () -> me.luisgamedev.betterhorses.traits.OwnedPotionEffects.forget(entity.getUniqueId()));
    }

    private void discoverMountedPlayers() {
        for (Player player : java.util.List.copyOf(org.bukkit.Bukkit.getOnlinePlayers())) later(player);
    }

    @Override public void onConfigurationReload() {
        shutdown();
        discoverMountedPlayers();
    }

    @Override public void shutdown() {
        for (UUID id : java.util.List.copyOf(sessions.keySet())) stop(id);
    }

    private static final class Session {
        final Player player;
        final AbstractHorse mount;
        Runnable cancel = () -> { };
        Session(Player player, AbstractHorse mount) { this.player = player; this.mount = mount; }
    }
}
