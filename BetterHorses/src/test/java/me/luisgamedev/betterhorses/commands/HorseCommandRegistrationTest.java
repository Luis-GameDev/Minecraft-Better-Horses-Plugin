package me.luisgamedev.betterhorses.commands;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HorseCommandRegistrationTest {
    @Test void disabledNeuteringHasNoRegisteredRouteOrCompletion() {
        HorseCommand command = new HorseCommand(false, false);
        HorseCommandCompleter completer = new HorseCommandCompleter(command);
        assertEquals(List.of("spawn", "despawn", "reload"), command.getRegisteredSubcommands());
        assertEquals(List.of(), completer.onTabComplete(senderWithEveryPermission(), null, "horse", new String[]{"neu"}));
        assertEquals(List.of("spawn", "despawn", "reload"), completer.onTabComplete(senderWithEveryPermission(), null, "horse", new String[]{""}));
    }

    @Test void reloadRemovesAndRestoresNeuteringWithoutReplacingTheCompleter() {
        HorseCommand command = new HorseCommand(true, false);
        HorseCommandCompleter completer = new HorseCommandCompleter(command);
        CommandSender sender = senderWithEveryPermission();
        assertEquals(List.of("neuter"), completer.onTabComplete(sender, null, "horse", new String[]{"ne"}));
        command.synchronizeSubcommands(false, false);
        assertFalse(command.getRegisteredSubcommands().contains("neuter"));
        assertEquals(List.of(), completer.onTabComplete(sender, null, "horse", new String[]{"ne"}));
        command.synchronizeSubcommands(true, true);
        assertEquals(List.of("spawn", "despawn", "neuter", "reload", "info"), command.getRegisteredSubcommands());
        assertEquals(List.of("neuter"), completer.onTabComplete(sender, null, "horse", new String[]{"NE"}));
    }

    @Test void registrationDoesNotGrantPermissionToTabComplete() {
        HorseCommand command = new HorseCommand(true, true);
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission")) return args[0].equals("betterhorses.spawn.command");
                    throw new AssertionError("Unexpected sender access: " + method);
                });
        assertEquals(List.of("spawn"), new HorseCommandCompleter(command).onTabComplete(sender, null, "horse", new String[]{""}));
    }

    private static CommandSender senderWithEveryPermission() {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission")) return true;
                    throw new AssertionError("Unexpected sender access: " + method);
                });
    }
}
