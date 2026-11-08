package me.luisgamedev.betterhorses.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.entity.Player;

/** Uses the public server API instead of version-sensitive packet facets. Call on the player's owner thread. */
public final class ActionBarSender {
    private ActionBarSender() { }

    public static void send(Player player, Component message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                ComponentSerializer.parse(GsonComponentSerializer.gson().serialize(message)));
    }
}
