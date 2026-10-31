package me.luisgamedev.betterhorses.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.help.GenericCommandHelpTopic;
import org.bukkit.help.HelpMap;
import org.bukkit.help.HelpTopic;

/** Keeps Bukkit's cached /help usage in sync with the registered BetterHorses routes. */
public final class HorseCommandHelp {
    private HorseCommandHelp() { }

    public static void updateUsage(Command command, HelpMap helpMap, String usage) {
        String oldUsage = command.getUsage();
        command.setUsage(usage);
        HelpTopic topic = helpMap.getHelpTopic("/" + command.getLabel());
        // Custom help topic implementations own their rendering. Only refresh Bukkit's generic cache.
        if (topic == null || topic.getClass() != GenericCommandHelpTopic.class) return;
        String oldLine = usageLine(oldUsage, command.getLabel());
        String newLine = usageLine(usage, command.getLabel());
        String current = topic.getFullText(null);
        if (!oldLine.equals(newLine) && current.contains(oldLine)) {
            // Replace just the generated usage segment, retaining any administrator amendments.
            topic.amendTopic(null, current.replace(oldLine, newLine));
        }
    }

    private static String usageLine(String usage, String label) {
        return ChatColor.GOLD + "Usage: " + ChatColor.WHITE + usage.replace("<command>", label);
    }
}
