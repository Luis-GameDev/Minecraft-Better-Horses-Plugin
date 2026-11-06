package me.luisgamedev.betterhorses.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Test void refreshSupportsPaperMapWithNonRemovableEntryIterators() throws Exception {
        Map<String, Command> entries = new DirectlyMutableCommandMap();
        SimpleCommandMap map = commandMap(entries);
        Command horse = command("horse"), foreign = command("foreign");
        horse.setAliases(List.of("oldalias"));
        map.register("BetterHorses", horse);
        entries.put("shared", foreign);
        entries.put("foreign:shared", foreign);

        HorseCommandAliases.refresh(horse, map, "BetterHorses", List.of("newalias", "shared"));
        assertNull(map.getCommand("oldalias"));
        assertNull(map.getCommand("betterhorses:oldalias"));
        assertSame(horse, map.getCommand("horse"));
        assertSame(horse, map.getCommand("betterhorses:horse"));
        assertSame(horse, map.getCommand("newalias"));
        assertSame(horse, map.getCommand("betterhorses:newalias"));
        assertSame(horse, map.getCommand("betterhorses:shared"));
        assertSame(foreign, map.getCommand("shared"));
        assertSame(foreign, map.getCommand("foreign:shared"));

        HorseCommandAliases.refresh(horse, map, "BetterHorses", List.of());
        assertNull(map.getCommand("newalias"));
        assertNull(map.getCommand("betterhorses:newalias"));
        assertNull(map.getCommand("betterhorses:shared"));
        assertSame(horse, map.getCommand("horse"));
        assertSame(foreign, map.getCommand("shared"));
        assertSame(foreign, map.getCommand("foreign:shared"));
    }

    /** Models Paper's live facade: direct mutations work; iterator removal is unsupported. */
    private static final class DirectlyMutableCommandMap extends AbstractMap<String, Command> {
        private final Map<String, Command> backing = new HashMap<>();

        @Override public Command put(String key, Command value) { return backing.put(key, value); }
        @Override public Command get(Object key) { return backing.get(key); }
        @Override public Command remove(Object key) { return backing.remove(key); }

        @Override public Set<String> keySet() {
            // Paper's forwarding map iterates command names, not the registered alias labels.
            return new AbstractSet<>() {
                @Override public int size() { return backing.size(); }

                @Override public Iterator<String> iterator() {
                    Iterator<Command> commands = new ArrayList<>(backing.values()).iterator();
                    return new Iterator<>() {
                        @Override public boolean hasNext() { return commands.hasNext(); }
                        @Override public String next() { return commands.next().getName(); }
                    };
                }
            };
        }

        @Override public Set<Entry<String, Command>> entrySet() {
            List<Entry<String, Command>> snapshot = new ArrayList<>();
            backing.forEach((key, value) -> snapshot.add(new SimpleImmutableEntry<>(key, value)));
            return new AbstractSet<>() {
                @Override public int size() { return snapshot.size(); }

                @Override public Iterator<Entry<String, Command>> iterator() {
                    Iterator<Entry<String, Command>> entries = snapshot.iterator();
                    return new Iterator<>() {
                        @Override public boolean hasNext() { return entries.hasNext(); }
                        @Override public Entry<String, Command> next() { return entries.next(); }
                    };
                }
            };
        }
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
