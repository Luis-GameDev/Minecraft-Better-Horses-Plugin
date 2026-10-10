package me.luisgamedev.betterhorses.listeners;

import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns every BetterHorses listener instance and reconciles it with live configuration. */
public final class ListenerRegistrationManager {
    private final Consumer<Listener> registrar;
    private final Consumer<Listener> unregistrar;
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private final Map<String, Listener> registered = new LinkedHashMap<>();

    public ListenerRegistrationManager(Consumer<Listener> registrar) {
        this(registrar, HandlerList::unregisterAll);
    }

    ListenerRegistrationManager(Consumer<Listener> registrar, Consumer<Listener> unregistrar) {
        this.registrar = Objects.requireNonNull(registrar, "registrar");
        this.unregistrar = Objects.requireNonNull(unregistrar, "unregistrar");
    }

    public synchronized void define(String key, Supplier<? extends Listener> factory, BooleanSupplier enabled) {
        define(key, factory, enabled, registrar);
    }

    public synchronized void define(String key, Supplier<? extends Listener> factory, BooleanSupplier enabled,
                       Consumer<Listener> listenerRegistrar) {
        Objects.requireNonNull(key, "key");
        if (definitions.putIfAbsent(key, new Definition(factory, enabled, listenerRegistrar)) != null) {
            throw new IllegalArgumentException("Duplicate listener registration key: " + key);
        }
    }

    /** Register newly enabled listeners, retain unchanged instances, and remove disabled ones. */
    public synchronized void synchronize() {
        for (Map.Entry<String, Definition> entry : definitions.entrySet()) {
            String key = entry.getKey();
            Definition definition = entry.getValue();
            Listener current = registered.get(key);
            if (definition.enabled().getAsBoolean()) {
                if (current == null) {
                    current = Objects.requireNonNull(definition.factory().get(), "listener factory returned null");
                    definition.registrar().accept(current);
                    registered.put(key, current);
                } else if (current instanceof ListenerLifecycle lifecycle) {
                    lifecycle.onConfigurationReload();
                }
            } else if (current != null) {
                unregister(key, current);
            }
        }
    }

    public synchronized void shutdown() {
        for (Map.Entry<String, Listener> entry : new LinkedHashMap<>(registered).entrySet()) {
            unregister(entry.getKey(), entry.getValue());
        }
    }

    public synchronized boolean isRegistered(String key) {
        return registered.containsKey(key);
    }

    public synchronized int registeredCount() {
        return registered.size();
    }

    Listener registeredListener(String key) {
        return registered.get(key);
    }

    private void unregister(String key, Listener listener) {
        unregistrar.accept(listener);
        if (listener instanceof ListenerLifecycle lifecycle) {
            lifecycle.shutdown();
        }
        registered.remove(key);
    }

    private record Definition(Supplier<? extends Listener> factory, BooleanSupplier enabled,
                              Consumer<Listener> registrar) {
        private Definition {
            Objects.requireNonNull(factory, "factory");
            Objects.requireNonNull(enabled, "enabled");
            Objects.requireNonNull(registrar, "registrar");
        }
    }
}
