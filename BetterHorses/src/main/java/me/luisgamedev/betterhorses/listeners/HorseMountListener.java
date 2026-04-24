package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

public class HorseMountListener implements Listener {
    private double Amplifier = 0.5;

    @EventHandler
    public void onHorseMount(PlayerInteractEntityEvent event) {

        if (!(event.getRightClicked() instanceof AbstractHorse horse)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        if (player.hasPermission("betterhorses.bypass")) return;

        FileConfiguration config = BetterHorses.getInstance().getConfig();

        if (config.getBoolean("settings.restrict-mounting-to-owner", false)) {
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

        if (!(horse.getPassengers().size() == 1)) {
            ArmorStand armorStand = (ArmorStand)player.getWorld().spawnEntity(horse.getLocation().add(getOffSetX(horse), GetArmorstandHeight(event.getRightClicked()), getOffSetZ(horse)), EntityType.ARMOR_STAND);
            armorStand.setGravity(false);
            armorStand.setSmall(true);
            //armorStand.setVisible(false);
            horse.addPassenger(armorStand);
            armorStand.addPassenger(player);
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
}
