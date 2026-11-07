package me.luisgamedev.betterhorses.utils;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.language.LanguageManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class CooldownDisplay {

    private static final Map<UUID, BossBar> bossbars = new HashMap<>();
    private static final Map<UUID, BukkitRunnable> hotbarTasks = new HashMap<>();
    private static final Map<UUID, BukkitRunnable> bossbarTasks = new HashMap<>();

    public static void showCooldown(double remainingSeconds, double fullCooldownSeconds, Player player, String abilityName) {
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        LanguageManager lang = BetterHorses.getInstance().getLang();
        boolean displayEnabled = config.getBoolean("settings.cooldown-display.enabled", true);
        boolean showBossbar = config.getBoolean("settings.cooldown-display.show-bossbar-indicator", false);
        boolean showHotbar = config.getBoolean("settings.cooldown-display.show-hotbar-indicator", true);

        if (!displayEnabled) return;

        UUID uuid = player.getUniqueId();

        if (showBossbar) {
            if (bossbarTasks.containsKey(uuid)) {
                bossbarTasks.get(uuid).cancel();
                bossbarTasks.remove(uuid);
            }
            if (bossbars.containsKey(uuid)) {
                BetterHorses.getInstance().getAudiences().player(player).hideBossBar(bossbars.get(uuid));
                bossbars.remove(uuid);
            }
            showBossbarCooldown(player, abilityName, remainingSeconds, fullCooldownSeconds, lang);
        }

        if (showHotbar) {
            if (hotbarTasks.containsKey(uuid)) {
                hotbarTasks.get(uuid).cancel();
                hotbarTasks.remove(uuid);
            }
            showHotbarMessage(player, abilityName, remainingSeconds, lang);
        }
    }

    private static void showBossbarCooldown(Player player, String abilityName, double remaining, double fullCooldown, LanguageManager lang) {
        UUID uuid = player.getUniqueId();
        double updateInterval = 0.1;
        int ticksPerUpdate = 2;
        double maxDisplayDuration = 5.0;
        double displayTime = Math.min(maxDisplayDuration, remaining);

        double initialProgress = Math.max(0.0, Math.min(1.0, remaining / fullCooldown));
        BossBar bar = BossBar.bossBar(lang.getFormattedRawComponent(player,
                "messages.cooldown-message-bossbar", "%value%", abilityName),
                (float) initialProgress, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        BetterHorses.getInstance().getAudiences().player(player).showBossBar(bar);
        bossbars.put(uuid, bar);

        BukkitRunnable task = new BukkitRunnable() {
            double secondsShown = 0;

            @Override
            public void run() {
                if (!player.isOnline() || secondsShown >= displayTime || remaining - secondsShown <= 0) {
                    BetterHorses.getInstance().getAudiences().player(player).hideBossBar(bar);
                    bossbars.remove(uuid);
                    bossbarTasks.remove(uuid);
                    cancel();
                    return;
                }

                double progress = Math.max(0.0, Math.min(1.0, (remaining - secondsShown) / fullCooldown));
                bar.progress((float) progress);
                secondsShown += updateInterval;
            }
        };

        task.runTaskTimer(BetterHorses.getInstance(), 0, ticksPerUpdate);
        bossbarTasks.put(uuid, task);
    }

    private static void sendActionBar(Player player, Component message) {
        BetterHorses.getInstance().getAudiences().player(player)
                .sendActionBar(message);
    }

    private static void showHotbarMessage(Player player, String abilityName, double secondsLeft, LanguageManager lang) {
        UUID uuid = player.getUniqueId();
        double updateInterval = 0.1;
        int ticksPerUpdate = 2;
        double displayTime = Math.min(5.0, secondsLeft);

        BukkitRunnable task = new BukkitRunnable() {
            double secondsShown = 0;

            @Override
            public void run() {
                if (!player.isOnline() || secondsShown >= displayTime || secondsLeft - secondsShown <= 0) {
                    sendActionBar(player, Component.empty());
                    hotbarTasks.remove(uuid);
                    cancel();
                    return;
                }

                Component message = lang.getFormattedRawComponent(
                        player,
                        "messages.cooldown-message-hotbar",
                        "%value%", abilityName,
                        "%remaining%", String.format(Locale.US, "%.1f", secondsLeft - secondsShown)
                );
                sendActionBar(player, message);
                secondsShown += updateInterval;
            }
        };

        task.runTaskTimer(BetterHorses.getInstance(), 0, ticksPerUpdate);
        hotbarTasks.put(uuid, task);
    }
}
