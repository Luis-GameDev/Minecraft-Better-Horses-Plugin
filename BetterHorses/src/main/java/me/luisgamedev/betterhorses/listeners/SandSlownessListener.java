package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumSet;
import java.util.Set;

public class SandSlownessListener implements Listener {

    private static final Set<Material> SAND_BLOCKS = EnumSet.of(
            Material.SAND,
            Material.RED_SAND,
            Material.SUSPICIOUS_SAND
    );
    private static final PotionEffect SAND_SLOWNESS = new PotionEffect(
            PotionEffectType.SLOWNESS,
            40,
            1,
            false,
            true,
            true
    );

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!(event.getPlayer().getVehicle() instanceof AbstractHorse mount)) return;

        SupportedMountType mountType = SupportedMountType.fromEntity(mount).orElse(null);
        if (mountType == null || mountType == SupportedMountType.CAMEL) return;
        if (!mountType.isEnabled(BetterHorses.getInstance().getConfig())) return;

        Block blockBelowMount = mount.getLocation().subtract(0, 0.1, 0).getBlock();
        if (!SAND_BLOCKS.contains(blockBelowMount.getType())) return;

        mount.addPotionEffect(SAND_SLOWNESS);
    }
}
