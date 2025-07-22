package me.luisgamedev.betterhorses.utils;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.Bukkit;
import org.bukkit.boss.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownDisplay {

    private static final Map<UUID, BossBar> bossbars = new HashMap<>();

    public static void showCooldown(double cooldownSeconds, Player player, String abilityName) {
        FileConfiguration config = BetterHorses.getInstance().getConfig();
        boolean displayEnabled = config.getBoolean("traits.cooldown-display.enabled", true);
        boolean showBossbar = config.getBoolean("traits.cooldown-display.show-bossbar-indicator", false);
        boolean showHotbar = config.getBoolean("traits.cooldown-display.show-hotbar-indicator", true);

        if (!displayEnabled) return;

        if (showBossbar) {
            showBossbarCooldown(player, abilityName, cooldownSeconds);
        }

        if (showHotbar) {
            showHotbarMessage(player, abilityName, cooldownSeconds);
        }
    }

    private static void showBossbarCooldown(Player player, String abilityName, double cooldownSeconds) {
        UUID uuid = player.getUniqueId();

        BossBar bar = Bukkit.createBossBar(
                "§e" + abilityName + " §7Cooldown",
                BarColor.YELLOW,
                BarStyle.SOLID
        );

        bar.setProgress(1.0);
        bar.addPlayer(player);
        bar.setVisible(true);
        bossbars.put(uuid, bar);

        final double visibleDuration = Math.min(3.0, cooldownSeconds);
        final double updateInterval = 0.1;
        final int ticksPerUpdate = 2;
        final int totalTicks = (int) (visibleDuration / updateInterval);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= totalTicks) {
                    bar.removePlayer(player);
                    bar.setVisible(false);
                    bossbars.remove(uuid);
                    cancel();
                    return;
                }

                double remainingCooldownRatio = Math.max(0.0, (cooldownSeconds - ticks * updateInterval) / cooldownSeconds);
                bar.setProgress(remainingCooldownRatio);
                ticks++;
            }
        }.runTaskTimer(BetterHorses.getInstance(), 0, ticksPerUpdate);
    }

    private static void showHotbarMessage(Player player, String abilityName, double cooldownSeconds) {
        final double visibleDuration = Math.min(3.0, cooldownSeconds);
        final double updateInterval = 0.1;
        final int ticksPerUpdate = 2;
        final int totalTicks = (int) (visibleDuration / updateInterval);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= totalTicks) {
                    player.sendActionBar("");
                    cancel();
                    return;
                }

                String message = "§e" + abilityName + " §7Cooldown: §f" + Math.ceil(cooldownSeconds) + "s";
                player.sendActionBar(message);
                ticks++;
            }
        }.runTaskTimer(BetterHorses.getInstance(), 0, ticksPerUpdate);
    }
}
