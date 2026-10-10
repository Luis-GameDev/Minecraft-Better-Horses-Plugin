package me.luisgamedev.betterhorses.utils;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.scheduler.ScheduledTaskHandle;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
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
        BossBar bar = boss ? Bukkit.createBossBar(lang.getFormattedRaw(player,
                "messages.cooldown-message-bossbar", "%value%", ability), BarColor.YELLOW, BarStyle.SOLID) : null;
        if (bar != null) { bar.addPlayer(player); bar.setVisible(true); }
        long started = System.nanoTime();
        ScheduledTaskHandle task = plugin.scheduler().entityRepeating(player, 1L, 2L, scheduled -> {
            double elapsed = (System.nanoTime() - started) / 1_000_000_000.0;
            if (!player.isOnline() || elapsed >= Math.min(5.0, remaining) || remaining <= elapsed) {
                remove(id);
                scheduled.cancel();
                return;
            }
            if (bar != null) bar.setProgress(Math.max(0, Math.min(1, (remaining - elapsed) / full)));
            if (hotbar) player.sendActionBar(lang.getFormattedRaw(player, "messages.cooldown-message-hotbar",
                    "%value%", ability, "%remaining%", String.format(Locale.US, "%.1f", remaining - elapsed)));
        }, () -> DISPLAYS.remove(id));
        DISPLAYS.put(id, new Display(task, bar));
    }

    public static void cleanup(Player player) { remove(player.getUniqueId()); }

    public static void cleanupAll() { DISPLAYS.keySet().forEach(CooldownDisplay::remove); }

    private static void remove(UUID id) {
        Display display = DISPLAYS.remove(id);
        if (display == null) return;
        display.task.cancel();
        if (display.bar != null) { display.bar.removeAll(); display.bar.setVisible(false); }
    }

    private record Display(ScheduledTaskHandle task, BossBar bar) { }
}
