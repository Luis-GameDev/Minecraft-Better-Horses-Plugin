import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HorseCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /horse <spawn|despawn|create>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn":
                return RespawnCommand.spawnHorseFromItem(player);
            case "despawn":
                return DespawnCommand.despawnHorseToItem(player);
            case "create":
                return CustomHorseCommand.createHorseItem(player, args);
            default:
                player.sendMessage(ChatColor.RED + "Unknown subcommand. Use /horse <spawn|despawn|create>");
                return true;
        }
    }
}
