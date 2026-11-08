package me.luisgamedev.betterhorses.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class ActionBarSenderTest {
    @Test void publicActionBarTransportPreservesRgbFontAndFormatting() {
        Capture capture = new Capture();
        Component message = MiniMessage.miniMessage().deserialize(
                "<font:minecraft:uniform><#12ab34><bold>Dash 4.2</bold></#12ab34></font>");
        ActionBarSender.send(capture.player, message);

        assertSame(ChatMessageType.ACTION_BAR, capture.type);
        assertEquals("Dash 4.2", BaseComponent.toPlainText(capture.message));
        String json = ComponentSerializer.toString(capture.message).toLowerCase(java.util.Locale.ROOT);
        assertTrue(json.contains("#12ab34"), json);
        assertTrue(json.contains("minecraft:uniform"), json);
        assertTrue(json.contains("\"bold\":true"), json);
        assertEquals(1, capture.sends);
    }

    @Test void emptyComponentClearsTheSameActionBarChannel() {
        Capture capture = new Capture();
        ActionBarSender.send(capture.player, Component.empty());
        assertSame(ChatMessageType.ACTION_BAR, capture.type);
        assertEquals("", BaseComponent.toPlainText(capture.message));
        assertEquals(1, capture.sends);
    }

    private static final class Capture {
        ChatMessageType type;
        BaseComponent[] message;
        int sends;
        final Player.Spigot sender = new Player.Spigot() {
            @Override public void sendMessage(ChatMessageType target, BaseComponent... components) {
                type = target;
                message = components;
                sends++;
            }
        };
        final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[]{Player.class}, (proxy, method, args) -> {
                    if (method.getName().equals("spigot")) return sender;
                    if (method.getName().equals("toString")) return "ActionBarPlayer";
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    if (method.getName().equals("equals")) return proxy == args[0];
                    throw new AssertionError("Unexpected player call: " + method.getName());
                });
    }
}
