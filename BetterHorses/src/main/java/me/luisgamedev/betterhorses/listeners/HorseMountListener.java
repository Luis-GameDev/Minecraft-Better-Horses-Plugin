package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.tasks.DuomountRotationTask;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class HorseMountListener implements Listener {
    private double Amplifier = 0.5;

    @EventHandler
    public void onHorseMount(PlayerInteractEntityEvent event) {

        if (!(event.getRightClicked() instanceof AbstractHorse horse)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        debugLog("MOUNT", "ATTEMPT", true, player.getName() + " attempting to mount horse");

        FileConfiguration config = BetterHorses.getInstance().getConfig();

        if (config.getBoolean("settings.restrict-mounting-to-owner", false) && !player.hasPermission("betterhorses.bypass")) {
            NamespacedKey ownerKey = new NamespacedKey(BetterHorses.getInstance(), "owner");
            var data = horse.getPersistentDataContainer();

            if (!data.has(ownerKey, PersistentDataType.STRING)) return;

            String ownerUUID = data.get(ownerKey, PersistentDataType.STRING);
            if (ownerUUID == null) return;

            if (!player.getUniqueId().toString().equals(ownerUUID)) {
                event.setCancelled(true);
                player.sendMessage(
                        BetterHorses.getInstance().getLang()
                                .getFormatted("messages.not-horse-owner")
                );
            }
        }

        if ((horse.getPassengers().size() == 1) && config.getBoolean("settings.allow-duo-horses", true)) {
            debugLog("DUOMOUNT", "ATTEMPT", true, "Attempting to place 2nd player");
            Boat boat = (Boat) horse.getWorld().spawnEntity(horse.getLocation(), EntityType.BOAT);

            boat.setGravity(false);
            boat.setInvulnerable(true);
            boat.setSilent(true);
            boat.setPersistent(false);
            boat.setInvisible(true);
            boat.setNoPhysics(true);
            boat.setVisibleByDefault(false);

            ArmorStand blocker = (ArmorStand) horse.getWorld().spawnEntity(horse.getLocation(), EntityType.ARMOR_STAND);

            blocker.setVisible(false);
            blocker.setMarker(true);
            blocker.setGravity(false);
            blocker.setInvulnerable(true);
            blocker.setSilent(true);
            blocker.setPersistent(false);

            horse.addPassenger(boat);

            boat.addPassenger(blocker);
            boat.addPassenger(player);

            if (BetterHorses.getInstance().getDuomountRotationTask() != null) {
                BetterHorses.getInstance().getDuomountRotationTask().registerBoat(boat);
            }
            debugLog("DUOMOUNT", "FINISH", true, "Placed 2nd player on horse");
        }
    }

    public double getOffSetX(Entity horse) {
        float pitch = horse.getLocation().getYaw() + 90.0F;
        return this.Amplifier * -Math.cos(inRadians(pitch));
    }

    public double getOffSetZ(Entity horse) {
        float pitch = horse.getLocation().getYaw() + 90.0F;
        return this.Amplifier * -Math.sin(inRadians(pitch));
    }

    public double inRadians(float degrees) {
        return (double)degrees * Math.PI / 180.0;
    }

    public double GetArmorstandHeight(Entity entity) {
        if (entity instanceof Mule) {
            return 0.2;
        } else {
            return entity instanceof Donkey ? 0.1 : 0.45;
        }
    }

    public void debugLog(String action, String checkpoint, boolean success, String details) {
        if (!BetterHorses.getInstance().isDebugModeEnabled()) {
            return;
        }

        String status = success ? "PASS" : "FAIL";
        String message = String.format("[DEBUG][%s][%s][%s] %s", action, checkpoint, status, details);
        if (success) {
            BetterHorses.getInstance().getLogger().info(message);
            return;
        }
        BetterHorses.getInstance().getLogger().warning(message);
    }
}
