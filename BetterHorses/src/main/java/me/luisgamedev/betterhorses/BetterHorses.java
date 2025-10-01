package me.luisgamedev.betterhorses;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import me.luisgamedev.betterhorses.commands.CustomHorseCommand;
import me.luisgamedev.betterhorses.commands.HorseCommand;
import me.luisgamedev.betterhorses.commands.HorseCommandCompleter;
import me.luisgamedev.betterhorses.commands.HorseCreateTabCompleter;
import me.luisgamedev.betterhorses.growing.HorseGrowthManager;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.listeners.*;
import me.luisgamedev.betterhorses.tasks.TraitParticleTask;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.command.SimpleCommandMap;

import java.util.List;
import java.util.logging.Level;

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

        registerListeners();

        PluginCommand horseCommand = getCommand("horse");
        if (horseCommand != null) {
            horseCommand.setTabCompleter(new HorseCommandCompleter());
            horseCommand.setExecutor(new HorseCommand());
            applyHorseCommandAliases();
        }
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

    public void reloadPluginConfiguration() {
        reloadConfig();
        languageManager.reload();
        applyHorseCommandAliases();
    }

    public boolean isProtocolLibAvailable() {
        return protocolLibAvailable;
    }

    private void applyHorseCommandAliases() {
        PluginCommand horseCommand = getCommand("horse");
        if (horseCommand == null) {
            return;
        }
        List<String> aliases = getConfig().getStringList("command-aliases");
        horseCommand.setAliases(aliases);

        try {
            Object server = Bukkit.getServer();
            Object commandMapObject = server.getClass().getMethod("getCommandMap").invoke(server);
            if (commandMapObject instanceof SimpleCommandMap commandMap) {
                horseCommand.unregister(commandMap);
                commandMap.register(getDescription().getName(), horseCommand);
            } else {
                getLogger().warning("Unable to refresh horse command aliases because the command map is unavailable.");
            }
        } catch (ReflectiveOperationException exception) {
            getLogger().log(Level.WARNING, "Failed to refresh horse command aliases.", exception);
        }
    }

    private void registerListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        FileConfiguration config = getConfig();

        pluginManager.registerEvents(new HorseSpawnListener(), this);
        pluginManager.registerEvents(new HorseBreedListener(), this);
        pluginManager.registerEvents(new HorseFeedListener(), this);
        pluginManager.registerEvents(new HorseItemBlockerListener(), this);

        if (config.getBoolean("settings.allow-rightclick-spawn", true)) {
            pluginManager.registerEvents(new RightClickListener(), this);
        }

        if (config.getBoolean("settings.rider-invulnerable", false)) {
            pluginManager.registerEvents(new RiderInvulnerableListener(), this);
        }

        if (config.getBoolean("settings.fix-step-height", false)) {
            pluginManager.registerEvents(new HorseStepHeightListener(), this);
        }

        if (config.getBoolean("settings.mounted-damage-boost.enabled", false)) {
            pluginManager.registerEvents(new MountedDamageBoostListener(), this);
        }

        boolean traitsEnabled = config.getBoolean("traits.enabled", true);
        if (!traitsEnabled) {
            return;
        }

        if (isAnyTraitEnabled("hellmare", "dashboost", "kickback", "ghosthorse", "revenantcurse")) {
            pluginManager.registerEvents(new TraitActivationListener(), this);
        }

        if (isAnyTraitEnabled("frosthooves", "featherhooves", "fireheart")) {
            pluginManager.registerEvents(new PassiveTraitListener(), this);
        }

        if (config.getBoolean("traits.revenantcurse.enabled", false)) {
            pluginManager.registerEvents(new RevenantCurseListener(), this);
        }

        if (config.getBoolean("traits.skyburst.enabled", false)
                || config.getBoolean("traits.heavenhooves.enabled", false)) {
            pluginManager.registerEvents(new HorseJumpListener(), this);
        }
    }

    private boolean isAnyTraitEnabled(String... traits) {
        FileConfiguration config = getConfig();
        for (String trait : traits) {
            if (config.getBoolean("traits." + trait + ".enabled", false)) {
                return true;
            }
        }
        return false;
    }

}
