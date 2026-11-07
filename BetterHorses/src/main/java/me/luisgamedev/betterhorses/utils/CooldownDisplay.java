package me.luisgamedev.betterhorses.utils;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.scheduler.ScheduledTaskHandle;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Player-owned cooldown UI; one display task can exist for each player. */
public final class CooldownDisplay {
    private static final ConcurrentMap<UUID, Display> DISPLAYS = new ConcurrentHashMap<>();

    private CooldownDisplay() { }

    public static void showCooldown(double remaining, double full, Player player, String ability) {
        BetterHorses plugin = BetterHorses.getInstance();
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("settings.cooldown-display.enabled", true)) return;
        UUID id = player.getUniqueId();
        remove(id);
        boolean boss = config.getBoolean("settings.cooldown-display.show-bossbar-indicator", false);
        boolean hotbar = config.getBoolean("settings.cooldown-display.show-hotbar-indicator", true);
        LanguageManager lang = plugin.getLang();
        BossBar bar = boss ? BossBar.bossBar(lang.getFormattedRawComponent(player,
                "messages.cooldown-message-bossbar", "%value%", ability),
                (float) Math.max(0, Math.min(1, remaining / full)), BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS) : null;
        if (bar != null) plugin.getAudiences().player(player).showBossBar(bar);
        long started = System.nanoTime();
        ScheduledTaskHandle task = plugin.scheduler().entityRepeating(player, 1L, 2L, scheduled -> {
            double elapsed = (System.nanoTime() - started) / 1_000_000_000.0;
            if (!player.isOnline() || elapsed >= Math.min(5.0, remaining) || remaining <= elapsed) {
                remove(id);
                scheduled.cancel();
                return;
            }
            if (bar != null) bar.progress((float) Math.max(0, Math.min(1, (remaining - elapsed) / full)));
            if (hotbar) plugin.getAudiences().player(player).sendActionBar(lang.getFormattedRawComponent(player, "messages.cooldown-message-hotbar",
                    "%value%", ability, "%remaining%", String.format(Locale.US, "%.1f", remaining - elapsed)));
        }, () -> DISPLAYS.remove(id));
        DISPLAYS.put(id, new Display(task, bar, player));
    }

    public static void cleanup(Player player) { remove(player.getUniqueId()); }

    public static void cleanupAll() { DISPLAYS.keySet().forEach(CooldownDisplay::remove); }

    private static void remove(UUID id) {
        Display display = DISPLAYS.remove(id);
        if (display == null) return;
        display.task.cancel();
        if (display.bar != null) BetterHorses.getInstance().getAudiences().player(display.player).hideBossBar(display.bar);
    }

    private record Display(ScheduledTaskHandle task, BossBar bar, Player player) { }
}
