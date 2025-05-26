package me.luisgamedev;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import me.luisgamedev.language.LanguageManager;
import me.luisgamedev.commands.CustomHorseCommand;
import me.luisgamedev.commands.HorseCommand;
import me.luisgamedev.commands.HorseCommandCompleter;
import me.luisgamedev.commands.HorseCreateTabCompleter;
import me.luisgamedev.listeners.*;
import me.luisgamedev.tasks.TraitParticleTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class BetterHorses extends JavaPlugin {

    private static BetterHorses instance;
    private LanguageManager languageManager;
    private boolean protocolLibAvailable = false;
    private ProtocolManager protocolManager;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
            protocolLibAvailable = true;
            protocolManager = ProtocolLibrary.getProtocolManager();
            getLogger().info("Successfully connected to ProtocolLib.");
        } else {
            getLogger().info(
                    "Please install ProtocolLib Version 5.3 for all features to work properly. " +
                    "Running BetterHorses without ProtocolLib is no problem, but will result in some features being disabled."
            );
        }
        instance = this;
        saveDefaultConfig();
        languageManager = new LanguageManager(this);
        getServer().getPluginManager().registerEvents(new HorseSpawnListener(), this);
        getServer().getPluginManager().registerEvents(new HorseBreedListener(), this);
        getServer().getPluginManager().registerEvents(new TraitActivationListener(), this);
        getServer().getPluginManager().registerEvents(new PassiveTraitListener(), this);
        getServer().getPluginManager().registerEvents(new RevenantCurseListener(), this);
        getServer().getPluginManager().registerEvents(new HorseJumpListener(), this);
        getCommand("horse").setTabCompleter(new HorseCommandCompleter());
        getCommand("horse").setExecutor(new HorseCommand());
        getCommand("horsecreate").setExecutor(new CustomHorseCommand());
        getCommand("horsecreate").setTabCompleter(new HorseCreateTabCompleter());

        Bukkit.getScheduler().runTaskTimer(
                this,
                new TraitParticleTask(),
                20L, // delay 1s
                20L  // repeat every 1s
        );
    }

    public static BetterHorses getInstance() {
        return instance;
    }

    public LanguageManager getLang() {
        return languageManager;
    }

    public boolean isProtocolLibAvailable() {
        return protocolLibAvailable;
    }
}
