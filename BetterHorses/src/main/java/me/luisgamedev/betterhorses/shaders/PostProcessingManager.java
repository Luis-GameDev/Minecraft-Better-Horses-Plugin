package me.luisgamedev.betterhorses.shaders;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/** Optional, version-gated player effects. No horse data or other effects are overwritten. */
public final class PostProcessingManager implements Listener {
    private final BetterHorses plugin;
    private final ShaderPlayerScheduler scheduler;
    private final ConcurrentMap<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Set<String> warnings = ConcurrentHashMap.newKeySet();
    private final AtomicReference<Settings> settings = new AtomicReference<>();
    private volatile boolean closed;

    public PostProcessingManager(BetterHorses plugin) {
        this.plugin = plugin;
        scheduler = new ShaderPlayerScheduler(plugin);
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
        long period = Math.max(1L, Math.min(1200L, config.getLong("postprocessing-shaders.update-interval-ticks", 10L)));
        settings.set(new Settings(access, rules, config, period));
        if (active(settings.get()) || !sessions.isEmpty()) {
            List.copyOf(Bukkit.getOnlinePlayers()).forEach(player -> scheduler.execute(player,
                    () -> refresh(player), () -> forget(player)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!closed && active(settings.get())) scheduler.execute(event.getPlayer(),
                () -> refresh(event.getPlayer()), () -> forget(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.player != event.getPlayer()) return;
        if (session != null) sessions.remove(event.getPlayer().getUniqueId(), session);
        if (session != null) {
            cleanup(session);
            session.cancel();
        }
    }

    private void refresh(Player player) {
        Settings current = settings.get();
        Session existing = sessions.get(player.getUniqueId());
        if (existing != null && existing.player != player) return;
        if (closed || !player.isOnline() || !active(current)) {
            if (existing != null) {
                cleanup(existing);
                existing.cancel();
                sessions.remove(player.getUniqueId(), existing);
            }
            return;
        }
        Session session = existing == null ? new Session(player, current.access) : existing;
        if (existing == null) sessions.put(player.getUniqueId(), session);
        if (session.cancelTask == null || session.period != current.period) {
            session.cancel();
            session.period = current.period;
            session.cancelTask = scheduler.repeat(player, current.period, () -> tick(session),
                    () -> sessions.remove(player.getUniqueId(), session));
            if (session.cancelTask == null) sessions.remove(player.getUniqueId(), session);
        }
    }

    private void tick(Session session) {
        Settings current = settings.get();
        if (closed || !active(current) || !session.player.isOnline()) {
            cleanup(session);
            session.cancel();
            sessions.remove(session.player.getUniqueId(), session);
            return;
        }
        try {
            Set<String> desired = session.player.isDead() || !WorldAccessPolicy.isEnabled(session.player.getWorld())
                    ? Set.of() : current.rules.evaluate(new BukkitShaderContext(session.player, current.config, this::warnOnce));
            session.effects.synchronize(desired, session.access());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            // Unexpected/incompatible implementations must not throw from the server's scheduler.
            warnOnce("Postprocessing shaders were deactivated because the player effects API or a condition could not be used.");
            settings.compareAndSet(current, new Settings(null, null, current.config, current.period));
            cleanup(session);
        }
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
        sessions.values().forEach(session -> {
            session.cancel();
            scheduler.cleanupOnShutdown(session.player, () -> cleanup(session));
        });
        sessions.clear();
    }

    private static boolean active(Settings settings) {
        return settings != null && settings.access != null && settings.rules != null && !settings.rules.isEmpty();
    }

    private void forget(Player player) {
        sessions.computeIfPresent(player.getUniqueId(), (id, session) -> session.player == player ? null : session);
    }

    private void warnOnce(String warning) {
        if (warnings.size() < 64 && warnings.add(warning)) plugin.getLogger().warning("[Shaders] " + warning);
    }

    private record Settings(PostEffectAccess access, ShaderRuleEngine rules, FileConfiguration config, long period) { }

    private static final class Session {
        final Player player;
        final PostEffectAccess bridge;
        final ShaderEffectState effects = new ShaderEffectState();
        long period;
        Runnable cancelTask;

        Session(Player player, PostEffectAccess bridge) { this.player = player; this.bridge = bridge; }

        void cancel() {
            if (cancelTask != null) cancelTask.run();
            cancelTask = null;
        }

        ShaderEffectState.Effects access() {
            return new ShaderEffectState.Effects() {
                public List<String> values() throws ReflectiveOperationException { return bridge.values(player); }
                public boolean add(String key) throws ReflectiveOperationException { return bridge.add(player, key); }
                public boolean remove(String key) throws ReflectiveOperationException { return bridge.remove(player, key); }
            };
        }
    }
}
