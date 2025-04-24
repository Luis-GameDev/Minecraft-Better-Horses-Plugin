package me.luisgamedev;

import me.luisgamedev.commands.CustomHorseCommand;
import me.luisgamedev.commands.HorseCommand;
import me.luisgamedev.commands.HorseCommandCompleter;
import me.luisgamedev.commands.HorseCreateTabCompleter;
import me.luisgamedev.listeners.HorseBreedListener;
import me.luisgamedev.listeners.HorseSpawnListener;
import me.luisgamedev.listeners.PassiveTraitListener;
import me.luisgamedev.listeners.TraitActivationListener;
import org.bukkit.plugin.java.JavaPlugin;

public class BetterHorses extends JavaPlugin {

    private static BetterHorses instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new HorseSpawnListener(), this);
        getServer().getPluginManager().registerEvents(new HorseBreedListener(), this);
        getServer().getPluginManager().registerEvents(new TraitActivationListener(), this);
        getServer().getPluginManager().registerEvents(new PassiveTraitListener(), this);
        getCommand("horse").setTabCompleter(new HorseCommandCompleter());
        getCommand("horse").setExecutor(new HorseCommand());
        getCommand("horsecreate").setExecutor(new CustomHorseCommand());
        getCommand("horsecreate").setTabCompleter(new HorseCreateTabCompleter());

    }

    public static BetterHorses getInstance() {
        return instance;
    }
}
