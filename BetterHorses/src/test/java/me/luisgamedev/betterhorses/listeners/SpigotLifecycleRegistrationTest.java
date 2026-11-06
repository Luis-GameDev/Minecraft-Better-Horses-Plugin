package me.luisgamedev.betterhorses.listeners;

import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.*;

class SpigotLifecycleRegistrationTest {
    @Test void allBukkitHandlersCanBeScannedWhenPaperEventsAreUnavailable() throws Exception {
        String name = TrainingDataLifecycleListener.class.getName();
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{
                TrainingDataLifecycleListener.class.getProtectionDomain().getCodeSource().getLocation()},
                TrainingDataLifecycleListener.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String candidate, boolean resolve) throws ClassNotFoundException {
                if (candidate.startsWith("com.destroystokyo.paper.event.") || candidate.startsWith("io.papermc.paper.event.")) {
                    throw new ClassNotFoundException(candidate);
                }
                if (candidate.equals(name)) {
                    Class<?> type = findLoadedClass(candidate);
                    if (type == null) type = findClass(candidate);
                    if (resolve) resolveClass(type);
                    return type;
                }
                return super.loadClass(candidate, resolve);
            }
        }) {
            Class<?> listener = Class.forName(name, false, loader);
            assertDoesNotThrow(listener::getDeclaredMethods);
            assertNotNull(listener.getMethod("onSpawn", EntitySpawnEvent.class));
            assertNotNull(listener.getMethod("onEntitiesLoaded", EntitiesLoadEvent.class));
            assertNotNull(listener.getMethod("onJoin", PlayerJoinEvent.class));
        }
    }
}
