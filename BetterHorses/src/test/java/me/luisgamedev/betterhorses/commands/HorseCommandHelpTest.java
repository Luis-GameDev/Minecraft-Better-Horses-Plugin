package me.luisgamedev.betterhorses.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.help.GenericCommandHelpTopic;
import org.bukkit.help.HelpMap;
import org.bukkit.help.HelpTopic;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class HorseCommandHelpTest {
    @Test void reloadingUsageRemovesAndRestoresNeuteringInExistingBukkitHelp() {
        Command command = command();
        String enabled = "/<command> <spawn|despawn|neuter|reload>";
        String disabled = "/<command> <spawn|despawn|reload>";
        command.setUsage(enabled);
        HelpTopic topic = new GenericCommandHelpTopic(command);
        topic.amendTopic(null, "Administrator note\n<text>\nFooter");
        HelpMap help = help(topic);
        assertTrue(topic.getFullText(null).contains("neuter"));

        HorseCommandHelp.updateUsage(command, help, disabled);
        assertEquals(disabled, command.getUsage());
        assertFalse(topic.getFullText(null).contains("neuter"));
        assertTrue(topic.getFullText(null).startsWith("Administrator note\n"));
        assertTrue(topic.getFullText(null).endsWith("\nFooter"));

        HorseCommandHelp.updateUsage(command, help, enabled);
        assertEquals(enabled, command.getUsage());
        assertTrue(topic.getFullText(null).contains("/horse <spawn|despawn|neuter|reload>"));
        assertTrue(topic.getFullText(null).startsWith("Administrator note\n"));
        assertTrue(topic.getFullText(null).endsWith("\nFooter"));
    }

    @Test void customTopicImplementationsAreNotChanged() {
        Command command = command();
        command.setUsage("/<command> <neuter>");
        HelpTopic custom = new GenericCommandHelpTopic(command) { };
        String before = custom.getFullText(null);
        HorseCommandHelp.updateUsage(command, help(custom), "/<command> <spawn>");
        assertEquals(before, custom.getFullText(null));
        assertEquals("/<command> <spawn>", command.getUsage());
    }

    @Test void firstStartupBeforeHelpTopicsAreCreatedOnlyUpdatesTheCommand() {
        Command command = command();
        assertDoesNotThrow(() -> HorseCommandHelp.updateUsage(command, help(null), "/<command> <spawn>"));
        assertEquals("/<command> <spawn>", command.getUsage());
    }

    private static Command command() {
        return new Command("horse") {
            @Override public boolean execute(CommandSender sender, String commandLabel, String[] args) { return true; }
        };
    }

    private static HelpMap help(HelpTopic topic) {
        return (HelpMap) Proxy.newProxyInstance(HelpMap.class.getClassLoader(), new Class<?>[]{HelpMap.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getHelpTopic")) {
                        assertEquals("/horse", args[0]);
                        return topic;
                    }
                    throw new AssertionError("Unexpected help access: " + method);
                });
    }
}
