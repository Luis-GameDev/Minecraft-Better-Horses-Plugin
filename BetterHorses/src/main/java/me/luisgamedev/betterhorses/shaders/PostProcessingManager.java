package me.luisgamedev.betterhorses.shaders;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Event-driven effects: one coalesced update per player, with no repeating task. */
public final class PostProcessingManager implements Listener {
    private final BetterHorses plugin;
    private final ShaderPlayerScheduler scheduler;
    private final ShaderEventListener events;
    private final ConcurrentMap<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Set<String> warnings = ConcurrentHashMap.newKeySet();
    private final AtomicReference<Settings> settings = new AtomicReference<>();
    private volatile boolean closed;

    public PostProcessingManager(BetterHorses plugin) {
        this.plugin = plugin;
        scheduler = new ShaderPlayerScheduler(plugin);
        events = new ShaderEventListener(plugin, this);
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        reload();
    }

    public void reload() {
        if (closed) return;
        warnings.clear();
        FileConfiguration config = plugin.getConfig();
        PostEffectAccess access = null;
        ShaderRuleEngine rules = null;
        if (config.getBoolean("postprocessing-shaders.enabled", false)) {
            access = PostEffectAccess.detect(Player.class, Bukkit.getBukkitVersion()).orElse(null);
            if (access == null) {
                plugin.getLogger().info("Postprocessing shaders are inactive: Minecraft 26.3 and the Paper player post-effects API are required.");
            } else {
                try {
                    rules = ShaderRuleEngine.compile(ShaderConfiguration.loadRules(new File(plugin.getDataFolder(), "config.yml")),
                            config.getString("postprocessing-shaders.default-namespace", "minecraft"), this::warnOnce);
                } catch (IOException | InvalidConfigurationException exception) {
                    warnOnce("The shader rules could not be read from config.yml; postprocessing shaders are inactive.");
                }
            }
        }
        settings.set(new Settings(access, rules, config));
        events.synchronize();
        // Existing sessions also need an update when the feature/rules become inactive.
        sessions.values().forEach(session -> requestUpdate(session.player));
        if (active(settings.get())) List.copyOf(Bukkit.getOnlinePlayers()).forEach(this::requestUpdate);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Session previous = sessions.get(player.getUniqueId());
        if (previous != null && previous.player != player && sessions.remove(player.getUniqueId(), previous)) {
            // The old connection's callbacks may retire after the replacement has joined.
            previous.cancelTransitions();
        }
        requestUpdate(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.player == event.getPlayer()
                && sessions.remove(event.getPlayer().getUniqueId(), session)) {
            session.cancelTransitions();
            cleanup(session);
        }
    }

    /** Safe from any thread. Context and effects are read only in the queued player callback. */
    public void requestUpdate(Player player) {
        if (closed || player == null) return;
        Settings current = settings.get();
        UUID id = player.getUniqueId();
        Session session = sessions.compute(id, (ignored, existing) -> {
            if (existing != null) return existing;
            return active(current) ? new Session(player, current.access) : null;
        });
        if (session == null || session.player != player || !session.pending.compareAndSet(false, true)) return;
        try {
            scheduler.execute(player, () -> update(session), () -> sessions.remove(id, session));
        } catch (RuntimeException | LinkageError exception) {
            session.pending.set(false);
            sessions.remove(id, session);
            warnOnce("A player shader update could not be scheduled by the server.");
        }
    }

    /** Call on the mount's owning thread, after mutating its data. */
    public void requestMountUpdate(Entity mount) {
        if (!closed && mount != null && active(settings.get())) {
            for (Entity passenger : mount.getPassengers()) {
                if (passenger instanceof Player player) requestUpdate(player);
            }
        }
    }

    boolean uses(String... conditions) {
        Settings current = settings.get();
        if (!active(current)) return false;
        Set<String> dependencies = current.rules.conditions();
        for (String condition : conditions) if (dependencies.contains(condition)) return true;
        return false;
    }

    boolean isActive() { return !closed && active(settings.get()); }

    void optionalEventFailure(String name) { warnOnce("The optional shader event " + name + " could not be used by the server."); }

    void activity(Player player, String... conditions) {
        if (uses("placeholder", "permission") || uses(conditions)) requestUpdate(player);
    }

    void worldChanged(World world, boolean resetFreezing) {
        if (closed || !active(settings.get())) return;
        // A server snapshot can be enumerated globally; world checks stay on each player's thread.
        List.copyOf(Bukkit.getOnlinePlayers()).forEach(player -> {
            try {
                scheduler.execute(player, () -> {
                    if (!closed && player.getWorld() == world) {
                        if (resetFreezing) resetTransitions(player);
                        requestUpdate(player);
                    }
                }, () -> { });
            } catch (RuntimeException | LinkageError exception) {
                warnOnce("A player shader update could not be scheduled by the server.");
            }
        });
    }

    /** Called by state-change events, on the player's thread. */
    void resetTransitions(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.player != player) return;
        session.powderSnowContact = false;
        session.freezeAttempted = false;
        session.freezeObservation = Integer.MIN_VALUE;
        session.cancelFreeze();
    }

    /** Paper block-contact events signal starts/changes, without evaluating unchanged rules. */
    void contact(Player player, Material material) {
        if (!uses("frozen", "onfire", "placeholder")) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null || session.player != player) { requestUpdate(player); return; }
        boolean changed = false;
        if (material == Material.POWDER_SNOW && !session.powderSnowContact) {
            session.powderSnowContact = true;
            session.freezeAttempted = false;
            session.cancelFreeze();
            changed = true;
        }
        if ((uses("frozen", "placeholder") && session.frozen != player.isFrozen())
                || (uses("onfire", "placeholder") && session.onFire != (player.getFireTicks() > 0))) changed = true;
        if (changed) requestUpdate(player);
    }

    private void update(Session session) {
        // Clear before evaluation: an event occurring during evaluation can queue another update.
        session.pending.set(false);
        if (sessions.get(session.player.getUniqueId()) != session) return;
        Settings current = settings.get();
        if (closed || !active(current) || !session.player.isOnline()) {
            session.cancelTransitions();
            cleanup(session);
            sessions.remove(session.player.getUniqueId(), session);
            return;
        }
        try {
            boolean allowed = !session.player.isDead() && WorldAccessPolicy.isEnabled(session.player.getWorld());
            Set<String> desired = allowed ? current.rules.evaluate(new BukkitShaderContext(session.player, current.config, this::warnOnce)) : Set.of();
            session.effects.synchronize(desired, session.access());
            if (allowed) planTransitions(session, current); else session.cancelTransitions();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce("Postprocessing shaders were deactivated because the player effects API or a condition could not be used.");
            if (settings.compareAndSet(current, new Settings(null, null, current.config))) {
                events.shutdown();
                // There are no periodic callbacks: explicitly clean every idle player's effects.
                sessions.values().forEach(other -> requestUpdate(other.player));
            }
            session.cancelTransitions();
            cleanup(session);
        }
    }

    private void planTransitions(Session session, Settings current) {
        Set<String> dependencies = current.rules.conditions();
        Player player = session.player;
        long now = player.getTicksLived();
        if (dependencies.contains("onfire") || dependencies.contains("placeholder")) {
            int remaining = player.getFireTicks();
            session.onFire = remaining > 0;
            long end = now + Math.max(0L, remaining) + 1L;
            if (remaining <= 0) session.cancelFire();
            else if (session.fireCheck == null || Math.abs(session.fireAt - end) > 1L) {
                session.cancelFire();
                session.fireAt = end;
                session.fireCheck = scheduler.schedule(player, (long) remaining + 1L, () -> {
                    session.fireCheck = null;
                    requestUpdate(player);
                }, () -> sessions.remove(player.getUniqueId(), session));
            }
        } else session.cancelFire();
        if (dependencies.contains("frozen") || dependencies.contains("placeholder")) {
            int ticks = player.getFreezeTicks();
            int maximum = Math.max(1, player.getMaxFreezeTicks());
            session.frozen = player.isFrozen();
            if (!session.freezeInitialized) {
                session.powderSnowContact = player.getLocation().getBlock().getType() == Material.POWDER_SNOW;
                session.freezeInitialized = true;
            }
            if (session.powderSnowContact && !session.frozen && !session.freezeAttempted) {
                session.freezeAttempted = true;
                scheduleFreeze(session, Math.max(1L, (long) maximum - ticks + 1L));
            } else if (!session.powderSnowContact && session.frozen && session.freezeObservation != ticks) {
                session.freezeObservation = ticks;
                // Vanilla thaws by two frozen ticks per entity tick. A locked unchanged
                // counter is checked once, rather than becoming a recurring poll.
                scheduleFreeze(session, Math.max(1L, ((long) ticks - maximum + 2L) / 2L + 1L));
            } else if (!session.frozen && !session.powderSnowContact) session.cancelFreeze();
        } else session.cancelFreeze();
    }

    private void scheduleFreeze(Session session, long delay) {
        session.cancelFreeze();
        session.freezeCheck = scheduler.schedule(session.player, delay, () -> {
            session.freezeCheck = null;
            requestUpdate(session.player);
        }, () -> sessions.remove(session.player.getUniqueId(), session));
    }

    private void cleanup(Session session) {
        try {
            session.effects.clear(session.access());
            session.bridge.flush(session.player);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce("Some BetterHorses postprocessing effects could not be removed by the server API.");
        }
    }

    public void shutdown() {
        closed = true;
        events.shutdown();
        sessions.values().forEach(session -> {
            session.cancelTransitions();
            scheduler.cleanupOnShutdown(session.player, () -> cleanup(session));
        });
        sessions.clear();
    }

    private static boolean active(Settings settings) {
        return settings != null && settings.access != null && settings.rules != null && !settings.rules.isEmpty();
    }

    private void warnOnce(String warning) {
        if (warnings.size() < 64 && warnings.add(warning)) plugin.getLogger().warning("[Shaders] " + warning);
    }

    private record Settings(PostEffectAccess access, ShaderRuleEngine rules, FileConfiguration config) { }

    private static final class Session {
        final Player player;
        final PostEffectAccess bridge;
        final ShaderEffectState effects = new ShaderEffectState();
        final AtomicBoolean pending = new AtomicBoolean();
        Runnable fireCheck;
        Runnable freezeCheck;
        long fireAt;
        boolean onFire;
        boolean frozen;
        boolean powderSnowContact;
        boolean freezeInitialized;
        boolean freezeAttempted;
        int freezeObservation = Integer.MIN_VALUE;

        Session(Player player, PostEffectAccess bridge) { this.player = player; this.bridge = bridge; }
        void cancelFire() { if (fireCheck != null) fireCheck.run(); fireCheck = null; }
        void cancelFreeze() { if (freezeCheck != null) freezeCheck.run(); freezeCheck = null; }
        void cancelTransitions() { cancelFire(); cancelFreeze(); }
        ShaderEffectState.Effects access() {
            return new ShaderEffectState.Effects() {
                public List<String> values() throws ReflectiveOperationException { return bridge.values(player); }
                public boolean add(String key) throws ReflectiveOperationException { return bridge.add(player, key); }
                public boolean remove(String key) throws ReflectiveOperationException { return bridge.remove(player, key); }
            };
        }
    }
}
