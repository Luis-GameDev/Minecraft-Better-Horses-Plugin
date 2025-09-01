package me.luisgamedev.betterhorses;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import me.luisgamedev.betterhorses.growing.HorseGrowthManager;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.commands.CustomHorseCommand;
import me.luisgamedev.betterhorses.commands.HorseCommand;
import me.luisgamedev.betterhorses.commands.HorseCommandCompleter;
import me.luisgamedev.betterhorses.commands.HorseCreateTabCompleter;
import me.luisgamedev.betterhorses.listeners.*;
import me.luisgamedev.betterhorses.tasks.TraitParticleTask;
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
        getServer().getPluginManager().registerEvents(new RightClickListener(), this);
        getServer().getPluginManager().registerEvents(new HorseFeedListener(), this);
        getServer().getPluginManager().registerEvents(new RiderInvulnerableListener(), this);
        getServer().getPluginManager().registerEvents(new HorseStepHeightListener(), this);
        getServer().getPluginManager().registerEvents(new HorseItemBlockerListener(), this);

        getCommand("horse").setTabCompleter(new HorseCommandCompleter());
        getCommand("horse").setExecutor(new HorseCommand());
        getCommand("horsecreate").setExecutor(new CustomHorseCommand());
        getCommand("horsecreate").setTabCompleter(new HorseCreateTabCompleter());

        new HorseGrowthManager(this).start();

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
