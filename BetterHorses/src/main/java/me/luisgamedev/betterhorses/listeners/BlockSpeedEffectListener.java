package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public class BlockSpeedEffectListener implements Listener {

    private static final int EFFECT_DURATION_TICKS = 40;

    private final BetterHorses plugin;
    private final Map<Material, Map<SupportedMountType, PotionEffect>> effectsByBlock = new EnumMap<>(Material.class);

    public BlockSpeedEffectListener(BetterHorses plugin) {
        this.plugin = plugin;
        loadEffects(plugin.getConfig());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!(event.getPlayer().getVehicle() instanceof AbstractHorse mount)) return;

        SupportedMountType mountType = SupportedMountType.fromEntity(mount).orElse(null);
        if (mountType == null || !mountType.isEnabled(plugin.getConfig())) return;

        Block blockBelowMount = mount.getLocation().subtract(0, 0.1, 0).getBlock();
        Map<SupportedMountType, PotionEffect> effectsByMount = effectsByBlock.get(blockBelowMount.getType());
        if (effectsByMount == null) return;

        PotionEffect effect = effectsByMount.get(mountType);
        if (effect != null) {
            mount.addPotionEffect(effect, true);
        }
    }

    private void loadEffects(FileConfiguration config) {
        int entryNumber = 0;
        for (Map<?, ?> entry : config.getMapList("settings.block-speed-effects.entries")) {
            entryNumber++;
            Material block = parseBlock(entry.get("block"));
            EffectStrength effectStrength = EffectStrength.parse(entry.get("effect"));
            SupportedMountType mountType = parseMountType(entry.get("mount-type"));

            if (block == null || !block.isBlock() || effectStrength == null || mountType == null) {
                plugin.getLogger().warning("Ignoring invalid block-speed-effects entry #" + entryNumber
                        + ". Expected a block, an effect from SLOWNESS_I/SLOWNESS_II/SPEED_I/SPEED_II/SPEED_III,"
                        + " and a supported mount-type.");
                continue;
            }

            PotionEffect effect = new PotionEffect(
                    effectStrength.type(),
                    EFFECT_DURATION_TICKS,
                    effectStrength.amplifier(),
                    false,
                    true,
                    true
            );
            effectsByBlock.computeIfAbsent(block, ignored -> new EnumMap<>(SupportedMountType.class))
                    .put(mountType, effect);
        }
    }

    private Material parseBlock(Object value) {
        if (!(value instanceof String blockName) || blockName.isBlank()) return null;
        return Material.matchMaterial(blockName.trim());
    }

    private SupportedMountType parseMountType(Object value) {
        if (!(value instanceof String mountName)) return null;
        return SupportedMountType.fromUserInput(mountName).orElse(null);
    }

    private enum EffectStrength {
        SLOWNESS_I(PotionEffectType.SLOWNESS, 0),
        SLOWNESS_II(PotionEffectType.SLOWNESS, 1),
        SPEED_I(PotionEffectType.SPEED, 0),
        SPEED_II(PotionEffectType.SPEED, 1),
        SPEED_III(PotionEffectType.SPEED, 2);

        private final PotionEffectType type;
        private final int amplifier;

        EffectStrength(PotionEffectType type, int amplifier) {
            this.type = type;
            this.amplifier = amplifier;
        }

        private PotionEffectType type() {
            return type;
        }

        private int amplifier() {
            return amplifier;
        }

        private static EffectStrength parse(Object value) {
            if (!(value instanceof String effectName) || effectName.isBlank()) return null;
            String normalized = effectName.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            try {
                return valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }
}
