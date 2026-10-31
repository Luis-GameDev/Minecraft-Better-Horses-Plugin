package me.luisgamedev.betterhorses;

import me.luisgamedev.betterhorses.commands.CustomHorseCommand;
import me.luisgamedev.betterhorses.commands.HorseCommand;
import me.luisgamedev.betterhorses.commands.HorseCommandHelp;
import me.luisgamedev.betterhorses.commands.HorseCommandCompleter;
import me.luisgamedev.betterhorses.commands.HorseCreateTabCompleter;
import me.luisgamedev.betterhorses.growing.HorseGrowthManager;
import me.luisgamedev.betterhorses.inspection.HorseInspectionManager;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.listeners.HorseMountListener;
import me.luisgamedev.betterhorses.listeners.*;
import me.luisgamedev.betterhorses.tasks.TraitParticleTask;
import me.luisgamedev.betterhorses.shaders.PostProcessingManager;
import me.luisgamedev.betterhorses.shaders.ShaderConfiguration;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.vehicle.VehicleEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
    private BukkitAudiences audiences;
    private ListenerRegistrationManager listenerRegistrationManager;
    private HorseCommand horseCommandExecutor;
    private PostProcessingManager postProcessingManager;
    private HorseInspectionManager horseInspectionManager;


    @Override
    public void onDisable() {
        if (horseInspectionManager != null) horseInspectionManager.shutdown();
        if (postProcessingManager != null) postProcessingManager.shutdown();
        me.luisgamedev.betterhorses.traits.TraitRegistry.cleanupAll();
        if (listenerRegistrationManager != null) listenerRegistrationManager.shutdown();
        if(audiences != null) audiences.close();
    }

    @Override
    public void onEnable() {
        instance = this;
        initializeConfigurationFiles();
        WorldAccessPolicy.reload(this);
        audiences = BukkitAudiences.create(this);
        debugLog("PLUGIN", "ENABLE_START", true, "Starting BetterHorses plugin bootstrap.");
        languageManager = new LanguageManager(this, audiences);
        horseInspectionManager = new HorseInspectionManager(this);

        configureListeners();
        listenerRegistrationManager.synchronize();
        horseInspectionManager.start();

        PluginCommand horseCommand = getCommand("horse");
        if (horseCommand != null) {
            horseCommandExecutor = new HorseCommand();
            horseCommand.setTabCompleter(new HorseCommandCompleter(horseCommandExecutor));
            horseCommand.setExecutor(horseCommandExecutor);
            updateHorseCommandUsage();
            applyHorseCommandAliases();
        }
        getCommand("horsecreate").setExecutor(new CustomHorseCommand());
        getCommand("horsecreate").setTabCompleter(new HorseCreateTabCompleter());

        new HorseGrowthManager(this).start();
        postProcessingManager = new PostProcessingManager(this);
        postProcessingManager.start();

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

    /** Request a coalesced shader update; safe to call from any thread. */
    public void refreshShaders(org.bukkit.entity.Player player) {
        if (postProcessingManager != null) postProcessingManager.requestUpdate(player);
    }

    /** Call on the mount's owning thread after a metadata change. */
    public void refreshMountShaders(org.bukkit.entity.Entity mount) {
        if (postProcessingManager != null) postProcessingManager.requestMountUpdate(mount);
    }

    private void initializeConfigurationFiles() {
        saveDefaultConfig();
        reloadConfig();
        updateYamlWithMissingSections("config.yml", false);
        updateYamlWithMissingSections("language.yml", true);
        reloadConfig();
    }

    public void reloadPluginConfiguration() {
        me.luisgamedev.betterhorses.traits.TraitRegistry.cleanupAll();
        HorseStepHeightListener.cleanupAll();
        updateYamlWithMissingSections("config.yml", false);
        updateYamlWithMissingSections("language.yml", true);
        reloadConfig();
        WorldAccessPolicy.reload(this);
        listenerRegistrationManager.synchronize();
        languageManager.reload();
        if (postProcessingManager != null) postProcessingManager.reload();
        if (horseCommandExecutor != null) horseCommandExecutor.synchronizeSubcommands();
        updateHorseCommandUsage();
        applyHorseCommandAliases();
        Bukkit.getOnlinePlayers().forEach(org.bukkit.entity.Player::updateCommands);
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

        YamlConfiguration currentConfig = new YamlConfiguration();
        try {
            currentConfig.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            getLogger().log(Level.WARNING, "Failed to read " + fileName + " while updating file.", exception);
            return;
        }
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
            if (fileName.equals("config.yml")) {
                ShaderConfiguration.preserveRules(currentConfig, file);
                ShaderConfiguration.saveWithBraces(currentConfig, file);
            } else {
                currentConfig.save(file);
            }
            reloadConfig();
            getLogger().info("Updated " + fileName + " with newly added default values.");
            debugLog("CONFIG", "UPDATE", true, "Added missing entries from default " + fileName + ".");
        } catch (IOException | InvalidConfigurationException exception) {
            getLogger().log(Level.WARNING, "Failed to save updated " + fileName + ".", exception);
            debugLog("CONFIG", "UPDATE", false, "Failed to save updated " + fileName + ": " + exception.getMessage());
        }
    }

    private boolean addMissingConfigValues(ConfigurationSection target, ConfigurationSection defaults, String parentPath) {
        boolean changed = false;

        for (String key : defaults.getKeys(false)) {
            String fullPath = parentPath.isEmpty() ? key : parentPath + "." + key;
            // Shader rules are user actions, not defaults to merge into existing rules.
            if (fullPath.equals("postprocessing-shaders.rules") && target.contains(key)) continue;
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

    private void updateHorseCommandUsage() {
        PluginCommand command = getCommand("horse");
        if (command != null && horseCommandExecutor != null) {
            HorseCommandHelp.updateUsage(command, getServer().getHelpMap(),
                    "/<command> <" + String.join("|", horseCommandExecutor.getRegisteredSubcommands()) + ">");
        }
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

    private void configureListeners() {
        listenerRegistrationManager = new ListenerRegistrationManager(this::registerWorldFilteredEvents);

        defineCore("horse-spawn", HorseSpawnListener::new);
        defineCore("horse-breed", HorseBreedListener::new);
        defineCore("horse-feed", HorseFeedListener::new);
        defineCore("horse-item-blocker", HorseItemBlockerListener::new);
        // Mount permission enforcement is always active; owner restriction is an optional
        // second responsibility inside this same listener.
        defineCore("horse-mount", HorseMountListener::new);
        defineCore("horse-permission", HorsePermissionListener::new);
        defineCore("horse-interactions", () -> new HorseInteractionListener(this, horseInspectionManager));
        // Cleanup must also run in disabled worlds and after a world-filter change.
        listenerRegistrationManager.define("horse-inspection-cleanup", () -> horseInspectionManager, () -> true,
                listener -> getServer().getPluginManager().registerEvents(listener, this));
        listenerRegistrationManager.define("world-command", WorldCommandListener::new, () -> true,
                listener -> getServer().getPluginManager().registerEvents(listener, this));

        listenerRegistrationManager.define("training-data-lifecycle", () -> new TrainingDataLifecycleListener(this), () -> true,
                listener -> {
                    getServer().getPluginManager().registerEvents(listener, this);
                    ((TrainingDataLifecycleListener) listener).start();
                });

        listenerRegistrationManager.define("right-click-spawn", RightClickListener::new,
                () -> getConfig().getBoolean("settings.allow-rightclick-spawn", true));
        listenerRegistrationManager.define("training-riding", HorseTrainingRidingListener::new,
                () -> trainingCategoryEnabled("riding"));
        listenerRegistrationManager.define("training-brushing", HorseTrainingBrushingListener::new,
                () -> trainingCategoryEnabled("brushing"));
        listenerRegistrationManager.define("rider-invulnerable", RiderInvulnerableListener::new,
                () -> getConfig().getBoolean("settings.rider-invulnerable", false));
        listenerRegistrationManager.define("step-height", HorseStepHeightListener::new,
                () -> getConfig().getBoolean("settings.fix-step-height", true));
        listenerRegistrationManager.define("mounted-damage-boost", MountedDamageBoostListener::new,
                () -> getConfig().getBoolean("settings.mounted-damage-boost.enabled", false));
        listenerRegistrationManager.define("block-speed-effects", () -> new BlockSpeedEffectListener(this),
                () -> getConfig().getBoolean("settings.block-speed-effects.enabled", false));
        listenerRegistrationManager.define("trample", () -> new TrampleListener(this),
                () -> getConfig().getBoolean("trample.enabled", false));

        listenerRegistrationManager.define("trait-activation", TraitActivationListener::new,
                () -> traitsEnabled("hellmare", "dashboost", "kickback", "ghosthorse", "revenantcurse"));
        listenerRegistrationManager.define("trait-cleanup", TraitCleanupListener::new,
                () -> traitsEnabled("dashboost", "ghosthorse"));
        listenerRegistrationManager.define("trait-undead", UndeadTraitListener::new,
                () -> traitsEnabled("undead"));
        listenerRegistrationManager.define("trait-passive", PassiveTraitListener::new,
                () -> traitsEnabled("frosthooves", "featherhooves", "fireheart"));
        listenerRegistrationManager.define("trait-revenant-curse", RevenantCurseListener::new,
                () -> traitsEnabled("revenantcurse"));
        listenerRegistrationManager.define("trait-jump", HorseJumpListener::new,
                () -> traitsEnabled("skyburst", "heavenhooves"));

        debugLog("LISTENER", "DEFINE", true, "Configured centralized listener registrations.");
    }

    private void defineCore(String key, java.util.function.Supplier<? extends Listener> factory) {
        listenerRegistrationManager.define(key, factory, () -> true);
    }

    private boolean trainingCategoryEnabled(String category) {
        return getConfig().getBoolean("training.enabled", false)
                && getConfig().getBoolean("training.categories." + category + ".enabled", true);
    }

    private boolean traitsEnabled(String... traits) {
        return getConfig().getBoolean("traits.enabled", true) && isAnyTraitEnabled(traits);
    }

    @SuppressWarnings("unchecked")
    private void registerWorldFilteredEvents(Listener listener) {
        PluginManager manager = getServer().getPluginManager();
        for (Method method : listener.getClass().getMethods()) {
            EventHandler handler = method.getAnnotation(EventHandler.class);
            if (handler == null || method.getParameterCount() != 1
                    || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) continue;
            Class<? extends Event> eventType = (Class<? extends Event>) method.getParameterTypes()[0];
            manager.registerEvent(eventType, listener, handler.priority(), (ignored, event) -> {
                // Related Bukkit events can share a HandlerList, so this executor may be
                // called with an event that is not accepted by the listener method.
                if (!eventType.isInstance(event)) return;
                if (!isEventWorldEnabled(event)) return;
                try {
                    method.invoke(listener, event);
                } catch (InvocationTargetException exception) {
                    throw new EventException(exception.getCause());
                } catch (ReflectiveOperationException exception) {
                    throw new EventException(exception);
                }
            }, this, handler.ignoreCancelled());
        }
    }

    private boolean isEventWorldEnabled(Event event) {
        if (event instanceof PlayerEvent playerEvent) return WorldAccessPolicy.isEnabled(playerEvent.getPlayer().getWorld());
        if (event instanceof EntityEvent entityEvent) return WorldAccessPolicy.isEnabled(entityEvent.getEntity().getWorld());
        if (event instanceof BlockEvent blockEvent) return WorldAccessPolicy.isEnabled(blockEvent.getBlock().getWorld());
        if (event instanceof VehicleEvent vehicleEvent) return WorldAccessPolicy.isEnabled(vehicleEvent.getVehicle().getWorld());
        if (event instanceof InventoryEvent inventoryEvent && inventoryEvent.getView().getPlayer() instanceof org.bukkit.entity.Entity entity) {
            return WorldAccessPolicy.isEnabled(entity.getWorld());
        }
        return true;
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
