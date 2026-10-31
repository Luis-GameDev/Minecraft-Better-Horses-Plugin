package me.luisgamedev.betterhorses.commands;

import org.bukkit.command.Command;
import org.bukkit.command.SimpleCommandMap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Refreshes only entries owned by the same command instance, retaining other plugins' commands. */
public final class HorseCommandAliases {
    private HorseCommandAliases() { }

    public static List<String> refresh(Command command, SimpleCommandMap commandMap, String owner, List<String> configured)
            throws ReflectiveOperationException {
        List<String> aliases = normalize(configured);
        Map<String, Command> commands = knownCommands(commandMap);
        synchronized (commandMap) {
            // Paper can expose a command-map facade whose iterators cannot remove entries.
            // Read actual entry labels: Paper's key-set iterator may return command names.
            List<String> ownedLabels = new ArrayList<>();
            for (Map.Entry<String, Command> entry : commands.entrySet()) {
                if (entry.getValue() == command) ownedLabels.add(entry.getKey());
            }
            for (String label : ownedLabels) {
                if (commands.get(label) == command) {
                    commands.remove(label);
                }
            }
            command.unregister(commandMap);
            command.setAliases(new ArrayList<>(aliases));
            commandMap.register(owner, command);
        }
        return aliases;
    }

    private static List<String> normalize(List<String> configured) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        for (String value : configured) {
            if (value == null) continue;
            String alias = value.trim().toLowerCase(Locale.ROOT);
            if (alias.isEmpty() || alias.indexOf(':') >= 0 || alias.indexOf('/') >= 0
                    || alias.chars().anyMatch(Character::isWhitespace)) continue;
            aliases.add(alias);
        }
        return List.copyOf(aliases);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Command> knownCommands(SimpleCommandMap commandMap) throws ReflectiveOperationException {
        // Spigot 1.20.4 does not expose Paper's public getKnownCommands method.
        Field field = SimpleCommandMap.class.getDeclaredField("knownCommands");
        field.setAccessible(true);
        return (Map<String, Command>) field.get(commandMap);
    }
}
