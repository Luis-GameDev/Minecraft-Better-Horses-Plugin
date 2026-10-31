package me.luisgamedev.betterhorses.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HorseCommandAliasesTest {
    @Test void changingAndRemovingAliasesDeletesBothOldCommandMapEntries() throws Exception {
        Map<String, Command> entries = new HashMap<>();
        SimpleCommandMap map = commandMap(entries);
        Command horse = command("horse");
        HorseCommandAliases.refresh(horse, map, "BetterHorses", List.of("oldalias"));
        assertSame(horse, map.getCommand("oldalias"));
        assertSame(horse, map.getCommand("betterhorses:oldalias"));

        HorseCommandAliases.refresh(horse, map, "BetterHorses", List.of("newalias"));
        assertNull(map.getCommand("oldalias"));
        assertNull(map.getCommand("betterhorses:oldalias"));
        assertSame(horse, map.getCommand("newalias"));
        assertSame(horse, map.getCommand("betterhorses:newalias"));
        assertSame(horse, map.getCommand("horse"));

        HorseCommandAliases.refresh(horse, map, "BetterHorses", List.of());
        assertNull(map.getCommand("newalias"));
        assertNull(map.getCommand("betterhorses:newalias"));
        assertSame(horse, map.getCommand("horse"));
    }

    @Test void refreshingPreservesOtherPluginsCommandsAndNormalizesConfiguredAliases() throws Exception {
        Map<String, Command> entries = new HashMap<>();
        SimpleCommandMap map = commandMap(entries);
        Command horse = command("horse"), other = command("foreign");
        entries.put("shared", other);
        entries.put("foreign:shared", other);
        List<String> aliases = HorseCommandAliases.refresh(horse, map, "BetterHorses",
                List.of(" NewAlias ", "newalias", "shared", "bad alias", "bad:alias", "/bad"));
        assertEquals(List.of("newalias", "shared"), aliases);
        assertSame(other, map.getCommand("shared"));
        assertSame(other, map.getCommand("foreign:shared"));
        HorseCommandAliases.refresh(horse, map, "BetterHorses", List.of());
        assertSame(other, map.getCommand("shared"));
        assertSame(other, map.getCommand("foreign:shared"));
        assertFalse(entries.keySet().stream().anyMatch(name -> name.contains("newalias")));
    }

    private static Command command(String name) {
        return new Command(name) {
            @Override public boolean execute(CommandSender sender, String label, String[] args) { return true; }
        };
    }

    private static SimpleCommandMap commandMap(Map<String, Command> entries) throws Exception {
        Field singleton = Unsafe.class.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        SimpleCommandMap map = (SimpleCommandMap) ((Unsafe) singleton.get(null)).allocateInstance(SimpleCommandMap.class);
        Field known = SimpleCommandMap.class.getDeclaredField("knownCommands");
        known.setAccessible(true);
        known.set(map, entries);
        return map;
    }
}
