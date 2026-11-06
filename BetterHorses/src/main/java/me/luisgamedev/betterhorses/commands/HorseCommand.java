package me.luisgamedev.betterhorses.commands;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.NeuteringPolicy;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public class HorseCommand implements CommandExecutor {
    private volatile Map<String, PlayerSubcommand> playerSubcommands;

    public HorseCommand() {
        synchronizeSubcommands();
    }

    HorseCommand(boolean neuteringEnabled, boolean debugEnabled) {
        synchronizeSubcommands(neuteringEnabled, debugEnabled);
    }

    /** Rebuild the registered routes after configuration reload, without registering disabled actions. */
    public void synchronizeSubcommands() {
        BetterHorses plugin = BetterHorses.getInstance();
        synchronizeSubcommands(NeuteringPolicy.isEnabled(plugin.getConfig()), plugin.isDebugModeEnabled());
    }

    void synchronizeSubcommands(boolean neuteringEnabled, boolean debugEnabled) {
        Map<String, PlayerSubcommand> commands = new LinkedHashMap<>();
        commands.put("spawn", new PlayerSubcommand(PermissionUtils.SPAWN_COMMAND, "SPAWN", RespawnCommand::spawnHorseFromItem));
        commands.put("despawn", new PlayerSubcommand(PermissionUtils.DESPAWN, "DESPAWN", DespawnCommand::despawnHorseToItem));
        if (neuteringEnabled) commands.put("neuter", new PlayerSubcommand("betterhorses.neuter", "NEUTER", HorseNeuterCommand::handle));
        if (debugEnabled) commands.put("info", new PlayerSubcommand(PermissionUtils.INFO, "INFO", HorseInfoCommand::handle));
        playerSubcommands = java.util.Collections.unmodifiableMap(commands);
    }

    /** The subcommands exposed by this executor; /horse remains registered when neutering is disabled. */
    public List<String> getRegisteredSubcommands() {
        List<String> commands = new ArrayList<>(playerSubcommands.keySet());
        int info = commands.indexOf("info");
        commands.add(info < 0 ? commands.size() : info, "reload");
        return List.copyOf(commands);
    }

    public List<String> getAvailableSubcommands(CommandSender sender) {
        Map<String, PlayerSubcommand> snapshot = playerSubcommands;
        return getRegisteredSubcommands().stream().filter(name -> name.equals("reload")
                ? sender.hasPermission("betterhorses.reload")
                : snapshot.containsKey(name) && sender.hasPermission(snapshot.get(name).permission())).toList();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !WorldAccessPolicy.isEnabled(player.getWorld())) return true;
        BetterHorses plugin = BetterHorses.getInstance();
        LanguageManager lang = plugin.getLang();
        OfflinePlayer audience = sender instanceof Player senderPlayer ? senderPlayer : null;

        if (args.length == 0) {
            sendHelp(sender, audience, "messages.horse-usage");
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        plugin.debugLog("HORSE_COMMAND", "RECEIVED", true,
                "Sender=" + sender.getName() + ", subcommand=" + subcommand);

        if (subcommand.equals("reload")) {
            if (!sender.hasPermission("betterhorses.reload")) {
                lang.sendFormatted(sender, audience, "messages.insufficient-permission", "%command%", "/horse reload");
                plugin.debugLog("HORSE_COMMAND", "RELOAD_PERMISSION", false,
                        "Sender " + sender.getName() + " lacks betterhorses.reload");
                return true;
            }

            plugin.reloadPluginConfiguration();
            lang.send(sender, audience, "messages.config-reloaded");
            plugin.debugLog("HORSE_COMMAND", "RELOAD", true,
                    "Configuration reloaded by " + sender.getName());
            return true;
        }

        PlayerSubcommand registered = playerSubcommands.get(subcommand);
        if (registered == null) {
            sendHelp(sender, audience, "messages.unknown-subcommand");
            plugin.debugLog("HORSE_COMMAND", "UNKNOWN_SUBCOMMAND", false,
                    "Sender " + sender.getName() + " used unknown subcommand: " + subcommand);
            return true;
        }

        if (!(sender instanceof Player player)) {
            lang.send(sender, audience, "messages.only-players");
            plugin.debugLog("HORSE_COMMAND", "PLAYER_REQUIRED", false,
                    "Non-player sender tried subcommand " + subcommand);
            return true;
        }

        if (!player.hasPermission(registered.permission())) {
            lang.sendFormatted(player, "messages.insufficient-permission", "%command%", "/horse " + subcommand);
            plugin.debugLog("HORSE_COMMAND", registered.debugPrefix() + "_PERMISSION", false,
                    "Player " + player.getName() + " lacks " + registered.permission());
            return true;
        }
        return registered.action().apply(player);
    }

    private void sendHelp(CommandSender sender, OfflinePlayer audience, String key) {
        LanguageManager lang = BetterHorses.getInstance().getLang();
        String subcommands = String.join("|", getRegisteredSubcommands());
        if (!playerSubcommands.containsKey("neuter")) {
            // Keep existing customized language files with the original literal command list usable.
            lang.sendFormatted(sender, audience, key, "%subcommands%", subcommands, "|neuter", "", "neuter|", "");
        } else {
            lang.sendFormatted(sender, audience, key, "%subcommands%", subcommands);
        }
    }

    private record PlayerSubcommand(String permission, String debugPrefix, Function<Player, Boolean> action) { }
}
