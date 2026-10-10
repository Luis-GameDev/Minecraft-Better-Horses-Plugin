package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Keeps BetterHorses commands entirely out of disabled worlds. */
public final class WorldCommandListener implements Listener {
    @EventHandler
    public void onCommandsSent(PlayerCommandSendEvent event) {
        if (WorldAccessPolicy.isEnabled(event.getPlayer().getWorld())) return;
        Set<String> names = commandNames();
        event.getCommands().removeIf(command -> isOurs(command, names));
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (WorldAccessPolicy.isEnabled(event.getPlayer().getWorld())) return;
        String command = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (isOurs(command, commandNames())) event.setCancelled(true);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        event.getPlayer().updateCommands();
    }

    private Set<String> commandNames() {
        Set<String> names = new HashSet<>(Set.of("horse", "horsecreate"));
        names.addAll(BetterHorses.getInstance().getConfig().getStringList("command-aliases"));
        Set<String> normalizedNames = new HashSet<>();
        for (String name : names) normalizedNames.add(name.toLowerCase(Locale.ROOT));
        return normalizedNames;
    }

    private boolean isOurs(String command, Set<String> names) {
        command = command.toLowerCase(Locale.ROOT);
        int namespace = command.indexOf(':');
        if (namespace < 0) return names.contains(command);
        String owner = command.substring(0, namespace);
        return (owner.equals("betterhorses") || owner.equals("betterhorses".toLowerCase(Locale.ROOT)))
                && names.contains(command.substring(namespace + 1));
    }
}
