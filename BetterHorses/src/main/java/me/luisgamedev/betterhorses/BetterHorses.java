package me.luisgamedev.betterhorses;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import me.luisgamedev.betterhorses.commands.CustomHorseCommand;
import me.luisgamedev.betterhorses.commands.HorseCommand;
import me.luisgamedev.betterhorses.commands.HorseCommandCompleter;
import me.luisgamedev.betterhorses.commands.HorseCreateTabCompleter;
import me.luisgamedev.betterhorses.growing.HorseGrowthManager;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.listeners.HorseMountListener;
import me.luisgamedev.betterhorses.listeners.*;
import me.luisgamedev.betterhorses.tasks.TraitParticleTask;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;

public class BetterHorses extends JavaPlugin {

    private static BetterHorses instance;
    private LanguageManager languageManager;
    private boolean protocolLibAvailable = false;
    private ProtocolManager protocolManager;
    private BukkitAudiences audiences;


    @Override
    public void onDisable() {
        if(audiences != null) audiences.close();
    }

    @Override
    public void onEnable() {
        instance = this;
        initializeConfigurationFiles();
        audiences = BukkitAudiences.create(this);
        debugLog("PLUGIN", "ENABLE_START", true, "Starting BetterHorses plugin bootstrap.");
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
        languageManager = new LanguageManager(this, audiences);

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
        debugLog("PLUGIN", "ENABLE_COMPLETE", true, "BetterHorses plugin enabled successfully.");
    }

    public static BetterHorses getInstance() {
        return instance;
    }

    public LanguageManager getLang() {
        return languageManager;
    }

    public BukkitAudiences getAudiences() {
        return audiences;
    }

    private void initializeConfigurationFiles() {
        saveDefaultConfig();
        reloadConfig();
        updateYamlWithMissingSections("config.yml", false);
        updateYamlWithMissingSections("language.yml", true);
        reloadConfig();
    }

    public void reloadPluginConfiguration() {
        updateYamlWithMissingSections("config.yml", false);
        updateYamlWithMissingSections("language.yml", true);
        reloadConfig();
        languageManager.reload();
        applyHorseCommandAliases();
        debugLog("PLUGIN", "RELOAD", true, "Configuration and language files were reloaded.");
    }

    private void updateYamlWithMissingSections(String fileName, boolean saveDefaultWhenMissing) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            if (!saveDefaultWhenMissing) {
                return;
            }
            saveResource(fileName, false);
        }

        YamlConfiguration currentConfig = YamlConfiguration.loadConfiguration(file);
        currentConfig.options().parseComments(true);
        YamlConfiguration defaultConfig;

        try (InputStream defaultConfigStream = getResource(fileName)) {
            if (defaultConfigStream == null) {
                getLogger().warning("Default " + fileName + " resource was not found; skipping file update.");
                debugLog("CONFIG", "UPDATE", false, "Missing default resource: " + fileName + ".");
                return;
            }
            defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8)
            );
            defaultConfig.options().parseComments(true);
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Failed to read default " + fileName + " while updating file.", exception);
            debugLog("CONFIG", "UPDATE", false, "Unable to read default file " + fileName + ": " + exception.getMessage());
            return;
        }

        boolean changed = addMissingConfigValues(currentConfig, defaultConfig, "");
        if (!changed) {
            return;
        }

        try {
            currentConfig.save(file);
            reloadConfig();
            getLogger().info("Updated " + fileName + " with newly added default values.");
            debugLog("CONFIG", "UPDATE", true, "Added missing entries from default " + fileName + ".");
        } catch (IOException exception) {
            getLogger().log(Level.WARNING, "Failed to save updated " + fileName + ".", exception);
            debugLog("CONFIG", "UPDATE", false, "Failed to save updated " + fileName + ": " + exception.getMessage());
        }
    }

    private boolean addMissingConfigValues(ConfigurationSection target, ConfigurationSection defaults, String parentPath) {
        boolean changed = false;

        for (String key : defaults.getKeys(false)) {
            String fullPath = parentPath.isEmpty() ? key : parentPath + "." + key;
            Object defaultValue = defaults.get(key);

            if (defaultValue instanceof ConfigurationSection defaultSection) {
                if (!target.isConfigurationSection(key)) {
                    target.createSection(key);
                    changed = true;
                }

                ConfigurationSection targetSection = target.getConfigurationSection(key);
                if (targetSection != null) {
                    changed |= addMissingConfigValues(targetSection, defaultSection, fullPath);
                }
                continue;
            }

            if (!target.contains(key)) {
                target.set(key, defaultValue);
                changed = true;
            }
        }

        return changed;
    }

    public boolean isProtocolLibAvailable() {
        return protocolLibAvailable;
    }

    public boolean isDebugModeEnabled() {
        return getConfig().getBoolean("debug.enabled", false);
    }

    public void debugLog(String action, String checkpoint, boolean success, String details) {
        if (!isDebugModeEnabled()) {
            return;
        }

        String status = success ? "PASS" : "FAIL";
        String message = String.format("[DEBUG][%s][%s][%s] %s", action, checkpoint, status, details);
        if (success) {
            getLogger().info(message);
            return;
        }
        getLogger().warning(message);
    }

    private void applyHorseCommandAliases() {
        PluginCommand horseCommand = getCommand("horse");
        if (horseCommand == null) {
            debugLog("PLUGIN", "COMMAND_ALIAS", false, "Horse command is unavailable while applying aliases.");
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
                debugLog("PLUGIN", "COMMAND_ALIAS", false, "Command map was unavailable for alias refresh.");
            }
        } catch (ReflectiveOperationException exception) {
            getLogger().log(Level.WARNING, "Failed to refresh horse command aliases.", exception);
            debugLog("PLUGIN", "COMMAND_ALIAS", false, "Failed to refresh aliases due to reflection error: " + exception.getMessage());
        }
        debugLog("PLUGIN", "COMMAND_ALIAS", true, "Applied /horse aliases: " + aliases);
    }

    private void registerListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        FileConfiguration config = getConfig();

        pluginManager.registerEvents(new HorseSpawnListener(), this);
        pluginManager.registerEvents(new HorseBreedListener(), this);
        pluginManager.registerEvents(new HorseFeedListener(), this);
        pluginManager.registerEvents(new HorseItemBlockerListener(), this);
        pluginManager.registerEvents(new HorseMountListener(), this);

        debugLog("LISTENER", "REGISTER_BASE", true, "Registered core horse listeners.");

        if (config.getBoolean("training.enabled", true) && config.getBoolean("training.categories.riding.enabled", true)) {
            pluginManager.registerEvents(new HorseTrainingRidingListener(), this);
            debugLog("LISTENER", "REGISTER", true, "Registered HorseTrainingRidingListener.");
        }

        if (config.getBoolean("training.enabled", true) && config.getBoolean("training.categories.brushing.enabled", true)) {
            pluginManager.registerEvents(new HorseTrainingBrushingListener(), this);
            debugLog("LISTENER", "REGISTER", true, "Registered HorseTrainingBrushingListener.");
        }

        if (config.getBoolean("settings.allow-rightclick-spawn", true)) {
            pluginManager.registerEvents(new RightClickListener(), this);
            debugLog("LISTENER", "REGISTER", true, "Registered RightClickListener.");
        }

        if (config.getBoolean("settings.rider-invulnerable", false)) {
            pluginManager.registerEvents(new RiderInvulnerableListener(), this);
            debugLog("LISTENER", "REGISTER", true, "Registered RiderInvulnerableListener.");
        }

        if (config.getBoolean("settings.fix-step-height", true)) {
            pluginManager.registerEvents(new HorseStepHeightListener(), this);
            debugLog("LISTENER", "REGISTER", true, "Registered HorseStepHeightListener.");
        }

        if (config.getBoolean("settings.mounted-damage-boost.enabled", false)) {
            pluginManager.registerEvents(new MountedDamageBoostListener(), this);
            debugLog("LISTENER", "REGISTER", true, "Registered MountedDamageBoostListener.");
        }

        if (config.getBoolean("settings.sand-slowness.enabled", false)) {
            pluginManager.registerEvents(new SandSlownessListener(), this);
            debugLog("LISTENER", "REGISTER", true, "Registered SandSlownessListener.");
        }

        if (config.getBoolean("trample.enabled", true)) {
            pluginManager.registerEvents(new TrampleListener(this), this);
            debugLog("LISTENER", "REGISTER", true, "Registered TrampleListener.");
        }

        if (!config.getBoolean("traits.enabled", true)) {
            debugLog("LISTENER", "REGISTER_TRAITS", false, "Trait listeners were skipped because traits are disabled.");
            return;
        }

        if (isAnyTraitEnabled("hellmare", "dashboost", "kickback", "ghosthorse", "revenantcurse")) {
            pluginManager.registerEvents(new TraitActivationListener(), this);
            debugLog("LISTENER", "REGISTER", true, "Registered TraitActivationListener.");
        }

        if (isAnyTraitEnabled("dashboost", "ghosthorse")) {
            pluginManager.registerEvents(new TraitCleanupListener(), this);
            debugLog("LISTENER", "REGISTER", true, "Registered TraitCleanupListener.");
        }

        if (isAnyTraitEnabled("frosthooves", "featherhooves", "fireheart")) {
            pluginManager.registerEvents(new PassiveTraitListener(), this);
            debugLog("LISTENER", "REGISTER", true, "Registered PassiveTraitListener.");
        }

        if (config.getBoolean("traits.revenantcurse.enabled", false)) {
            pluginManager.registerEvents(new RevenantCurseListener(), this);
            debugLog("LISTENER", "REGISTER", true, "Registered RevenantCurseListener.");
        }

        if (config.getBoolean("traits.skyburst.enabled", false)
                || config.getBoolean("traits.heavenhooves.enabled", false)) {
            pluginManager.registerEvents(new HorseJumpListener(), this);
            debugLog("LISTENER", "REGISTER", true, "Registered HorseJumpListener.");
        }
    }

    private boolean isHorseTrampleEnabled(FileConfiguration config) {
        for (me.luisgamedev.betterhorses.utils.SupportedMountType mountType : me.luisgamedev.betterhorses.utils.SupportedMountType.values()) {
            if (config.getBoolean("settings.horse-trample.mount-types." + mountType.getConfigKey() + ".enabled", false)) {
                return true;
            }
        }
        return false;
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
