package me.luisgamedev.commands;

import me.luisgamedev.BetterHorses;
import org.bukkit.*;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class CustomHorseCommand {

    public static boolean createHorseItem(Player player, String[] args) {

        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /horse create <health> <speed> <jump> [gender] [name]");
            return true;
        }

        double health, speed, jump;
        try {
            health = Double.parseDouble(args[1]);
            speed = Double.parseDouble(args[2]);
            jump = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Health, speed, and jump must be numbers.");
            return true;
        }

        String gender = args.length >= 5 ? args[4].toLowerCase() : (Math.random() < 0.5 ? "male" : "female");
        String genderSymbol = gender.equals("male") ? "♂" : gender.equals("female") ? "♀" : "?";

        String name = args.length >= 6 ? ChatColor.GOLD + args[5] : ChatColor.GOLD + "Horse " + genderSymbol;

        ItemStack item = new ItemStack(Material.SADDLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(
                ChatColor.GRAY + "Gender: " + genderSymbol,
                ChatColor.GRAY + String.format("Health: %.2f", health),
                ChatColor.GRAY + String.format("Speed: %.4f", speed),
                ChatColor.GRAY + String.format("Jump: %.4f", jump)
        ));

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(new NamespacedKey(BetterHorses.getInstance(), "gender"), PersistentDataType.STRING, gender);
        data.set(new NamespacedKey(BetterHorses.getInstance(), "health"), PersistentDataType.DOUBLE, health);
        data.set(new NamespacedKey(BetterHorses.getInstance(), "current_health"), PersistentDataType.DOUBLE, health);
        data.set(new NamespacedKey(BetterHorses.getInstance(), "speed"), PersistentDataType.DOUBLE, speed);
        data.set(new NamespacedKey(BetterHorses.getInstance(), "jump"), PersistentDataType.DOUBLE, jump);
        data.set(new NamespacedKey(BetterHorses.getInstance(), "owner"), PersistentDataType.STRING, player.getUniqueId().toString());
        data.set(new NamespacedKey(BetterHorses.getInstance(), "name"), PersistentDataType.STRING, name.replace(ChatColor.GOLD.toString(), ""));
        data.set(new NamespacedKey(BetterHorses.getInstance(), "style"), PersistentDataType.STRING, Horse.Style.WHITE.name());
        data.set(new NamespacedKey(BetterHorses.getInstance(), "color"), PersistentDataType.STRING, Horse.Color.CREAMY.name());

        item.setItemMeta(meta);
        player.getInventory().addItem(item);
        player.sendMessage(ChatColor.AQUA + "Custom horse item created.");
        return true;
    }
}
