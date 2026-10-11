package me.luisgamedev.betterhorses.shaders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Reconciles this plugin's effects without taking ownership of existing effects. */
public final class ShaderEffectState {
    public interface Effects {
        List<String> values() throws ReflectiveOperationException;

        boolean add(String key) throws ReflectiveOperationException;

        boolean remove(String key) throws ReflectiveOperationException;
    }

    private final Set<String> owned = new HashSet<>();

    /**
     * Adds every matching effect and removes only effects previously added by this instance.
     * Our effects use a consistent order, regardless of the order of the matching rules.
     */
    public void synchronize(Collection<String> desired, Effects effects)
            throws ReflectiveOperationException {
        Objects.requireNonNull(desired, "desired");
        Objects.requireNonNull(effects, "effects");
        Set<String> orderedDesired = new TreeSet<>();
        for (String key : desired) {
            orderedDesired.add(Objects.requireNonNull(key, "effect key"));
        }

        List<String> current = new ArrayList<>(effects.values());
        owned.retainAll(current);

        for (String key : new TreeSet<>(owned)) {
            if (!orderedDesired.contains(key)) {
                remove(key, effects, current);
            }
        }
        for (String key : orderedDesired) {
            if (!current.contains(key)) {
                add(key, effects, current);
            }
        }

        List<String> currentOwned = current.stream()
                .filter(key -> owned.contains(key) && orderedDesired.contains(key))
                .toList();
        List<String> orderedOwned = new ArrayList<>(new TreeSet<>(currentOwned));
        if (!currentOwned.equals(orderedOwned)) {
            // Reordering never clears the full player list: other plugins' effects stay intact.
            for (String key : currentOwned) {
                remove(key, effects, current);
            }
            for (String key : orderedOwned) {
                if (!current.contains(key)) {
                    add(key, effects, current);
                }
            }
        }
    }

    /** Removes only effects successfully added by this instance. */
    public void clear(Effects effects) throws ReflectiveOperationException {
        Objects.requireNonNull(effects, "effects");
        List<String> current = new ArrayList<>(effects.values());
        owned.retainAll(current);
        for (String key : new TreeSet<>(owned)) {
            remove(key, effects, current);
        }
    }

    private void add(String key, Effects effects, List<String> current)
            throws ReflectiveOperationException {
        if (effects.add(key)) {
            // Record each success immediately, including when a later operation throws.
            owned.add(key);
            current.add(key);
        }
    }

    private void remove(String key, Effects effects, List<String> current)
            throws ReflectiveOperationException {
        if (effects.remove(key)) {
            owned.remove(key);
            current.removeIf(key::equals);
        }
    }
}
