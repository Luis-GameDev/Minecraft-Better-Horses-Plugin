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
        event.getCommands().removeIf(command -> names.contains(unqualified(command)));
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (WorldAccessPolicy.isEnabled(event.getPlayer().getWorld())) return;
        String command = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (commandNames().contains(unqualified(command))) event.setCancelled(true);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        event.getPlayer().updateCommands();
    }

    private Set<String> commandNames() {
        Set<String> names = new HashSet<>(Set.of("horse", "horsecreate"));
        names.addAll(BetterHorses.getInstance().getConfig().getStringList("command-aliases"));
        names.replaceAll(name -> name.toLowerCase(Locale.ROOT));
        return names;
    }

    private String unqualified(String command) {
        int namespace = command.indexOf(':');
        return namespace >= 0 ? command.substring(namespace + 1) : command;
    }
}
