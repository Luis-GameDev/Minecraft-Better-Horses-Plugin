package me.luisgamedev.betterhorses.inspection;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.events.BetterHorseEntityNeuterEvent;
import me.luisgamedev.betterhorses.api.events.BetterHorseNeuterEvent;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import me.luisgamedev.betterhorses.utils.NeuteringPolicy;
import me.luisgamedev.betterhorses.utils.WorldAccessPolicy;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/** Applies live-mount neutering only after both cancellable events have accepted it. */
public final class HorseNeuteringService {
    private final BetterHorses plugin;
    private final DoubleSupplier random;
    private final Set<UUID> inProgress = ConcurrentHashMap.newKeySet();

    public HorseNeuteringService(BetterHorses plugin) {
        this(plugin, () -> ThreadLocalRandom.current().nextDouble());
    }

    HorseNeuteringService(BetterHorses plugin, DoubleSupplier random) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.random = Objects.requireNonNull(random, "random");
    }

    /** Returns true only when this call successfully neutered the mount. */
    public boolean neuter(Player player, AbstractHorse horse, EquipmentSlot hand) {
        if (!canNeuter(player, horse, hand, true) || !inProgress.add(horse.getUniqueId())) return false;
        try {
            ItemStack original = player.getInventory().getItemInMainHand().clone();
            GameMode gameMode = player.getGameMode();
            // The compatibility subtype shares the unified event's handlers and cancellation state.
            // Dispatch once so both existing entity listeners and unified listeners see this attempt.
            BetterHorseNeuterEvent event = new BetterHorseEntityNeuterEvent(player, horse, hand);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled() || !unchanged(player, horse, hand, original, gameMode)) return false;

            Wear wear = prepareWear(player, original);
            if (wear == null || !unchanged(player, horse, hand, original, gameMode)) return false;

            horse.getPersistentDataContainer().set(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE, (byte) 1);
            horse.setLoveModeTicks(0);
            if (wear.changed()) player.getInventory().setItemInMainHand(wear.result());
            plugin.refreshMountShaders(horse);
            if (wear.broken()) {
                Bukkit.getPluginManager().callEvent(new PlayerItemBreakEvent(player, original.clone()));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
            }
            horse.getWorld().playSound(horse.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1.0F, 1.0F);
            horse.damage(0.0D);
            horse.playHurtAnimation(player.getLocation().getYaw());
            plugin.getLang().send(player, "messages.successfully-castrated");
            return true;
        } finally {
            inProgress.remove(horse.getUniqueId());
        }
    }

    private boolean canNeuter(Player player, AbstractHorse horse, EquipmentSlot hand, boolean feedback) {
        if (!NeuteringPolicy.isEnabled(plugin.getConfig())) return false;
        if (player == null || horse == null || hand != EquipmentSlot.HAND || !player.isSneaking()
                || !horse.isValid() || horse.isDead() || !player.isOnline() || player.isDead()
                || !plugin.getConfig().getBoolean("horse-neutering.shears-enabled", false)
                || !WorldAccessPolicy.isEnabled(horse.getWorld())
                || !player.getWorld().equals(horse.getWorld())
                || player.getInventory().getItemInMainHand().getType() != Material.SHEARS) return false;
        SupportedMountType type = SupportedMountType.fromEntity(horse).orElse(null);
        if (type == null || !type.isEnabled(plugin.getConfig())) return false;
        if (!player.hasPermission("betterhorses.neuter")) {
            if (feedback) plugin.getLang().sendFormatted(player, "messages.insufficient-permission", "%command%", "shears neutering");
            return false;
        }
        if (!horse.isTamed()) {
            if (feedback) plugin.getLang().send(player, "messages.neutering-requires-tamed");
            return false;
        }
        if (!player.hasPermission("betterhorses.bypass") && !player.getUniqueId().equals(ownerId(horse))) {
            if (feedback) plugin.getLang().sendFormatted(player, "messages.not-horse-owner", "%mount%", type.getDisplayName(plugin.getLang(), player));
            return false;
        }
        if (Byte.valueOf((byte) 1).equals(horse.getPersistentDataContainer().get(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE))) {
            if (feedback) plugin.getLang().send(player, "messages.already-castrated");
            return false;
        }
        return true;
    }

    private UUID ownerId(AbstractHorse horse) {
        String stored = horse.getPersistentDataContainer().get(BetterHorseKeys.OWNER, PersistentDataType.STRING);
        if (stored != null && !stored.isBlank()) {
            try { return UUID.fromString(stored); }
            catch (IllegalArgumentException ignored) { /* Fall back to the entity's owner. */ }
        }
        AnimalTamer owner = horse.getOwner();
        return owner == null ? null : owner.getUniqueId();
    }

    private boolean unchanged(Player player, AbstractHorse horse, EquipmentSlot hand, ItemStack original, GameMode gameMode) {
        return player.getGameMode() == gameMode && original.equals(player.getInventory().getItemInMainHand())
                && canNeuter(player, horse, hand, false);
    }

    private Wear prepareWear(Player player, ItemStack original) {
        if (!(original.getItemMeta() instanceof Damageable meta)) return null;
        if (player.getGameMode() == GameMode.CREATIVE || meta.isUnbreakable()) return new Wear(original, false, false);
        int unbreaking = Math.max(0, meta.getEnchantLevel(Enchantment.UNBREAKING));
        if (unbreaking > 0 && random.getAsDouble() >= 1.0D / (unbreaking + 1.0D)) return new Wear(original, false, false);

        PlayerItemDamageEvent damage = new PlayerItemDamageEvent(player, original.clone(), 1);
        Bukkit.getPluginManager().callEvent(damage);
        if (damage.isCancelled()) return null;
        int amount = Math.max(0, damage.getDamage());
        if (amount == 0) return new Wear(original, false, false);

        int maximum = meta.hasMaxDamage() ? meta.getMaxDamage() : original.getType().getMaxDurability();
        if (maximum <= 0) return null;
        ItemStack result = original.clone();
        if ((long) meta.getDamage() + amount >= maximum) {
            result.setAmount(original.getAmount() - 1);
            if (result.getAmount() <= 0) result = new ItemStack(Material.AIR);
            else {
                meta.setDamage(0);
                if (!result.setItemMeta(meta)) return null;
            }
            return new Wear(result, true, true);
        }
        meta.setDamage(meta.getDamage() + amount);
        if (!result.setItemMeta(meta)) return null;
        return new Wear(result, true, false);
    }

    private record Wear(ItemStack result, boolean changed, boolean broken) {}
}
