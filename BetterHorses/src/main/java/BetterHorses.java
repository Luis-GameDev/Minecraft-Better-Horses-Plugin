import org.bukkit.plugin.java.JavaPlugin;

public class BetterHorses extends JavaPlugin {

    private static BetterHorses instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new HorseSpawnListener(), this);
        getServer().getPluginManager().registerEvents(new HorseBreedListener(), this);
        getCommand("horse").setExecutor(new HorseCommand());
        getCommand("horse").setTabCompleter(new HorseCommandCompleter());
        getCommand("horse").setExecutor(new HorseCommand());
    }

    public static BetterHorses getInstance() {
        return instance;
    }
}
