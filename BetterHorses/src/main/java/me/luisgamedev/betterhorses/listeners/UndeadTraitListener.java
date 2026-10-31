package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.growing.GrowthScale;
import me.luisgamedev.betterhorses.growing.HorseSize;
import me.luisgamedev.betterhorses.utils.MountConfig;
import me.luisgamedev.betterhorses.training.TrainingManager;
import me.luisgamedev.betterhorses.traits.TraitParticleResolver;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import me.luisgamedev.betterhorses.utils.HorseArmorUtils;
import me.luisgamedev.betterhorses.utils.ItemStackSerialization;
import me.luisgamedev.betterhorses.utils.HorseStats;
import me.luisgamedev.betterhorses.utils.PermissionUtils;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.io.ObjectInputFilter;

public class UndeadTraitListener implements Listener {
    private static final String TRAIT = "undead";
    private static final String CARGO_PREFIX = "cargo-v2:";
    private static final int MAX_CARGO_SLOTS = 54;
    private final BetterHorses plugin = BetterHorses.getInstance();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof AbstractHorse horse)) return;
        if (horse instanceof SkeletonHorse) return;
        if (!SupportedMountType.isSupported(horse)) return;
        if (!TRAIT.equalsIgnoreCase(horse.getPersistentDataContainer().get(BetterHorseKeys.TRAIT, PersistentDataType.STRING))) return;
        if (event.getFinalDamage() < horse.getHealth()) return;
        if (!canUseUndeadTrait(horse)) return;
        event.setCancelled(true);
        transformToSkeleton(horse);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (!(event.getRightClicked() instanceof SkeletonHorse skeleton)) return;
        if (!isUndeadSkeleton(skeleton)) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item == null || item.getType() != getTransformBackItem()) return;
        if (!PermissionUtils.canUseTrait(player, TRAIT)) return;
        event.setCancelled(true);
        ItemStack expected = item.clone();
        // Spawn events may be cancelled or may replace the player's held item. Commit only
        // while both source entities and the intended payment are still present.
        if (!transformBack(skeleton, () -> expected.equals(player.getInventory().getItem(event.getHand())))) return;
        ItemStack current = player.getInventory().getItem(event.getHand());
        if (current.getAmount() > 1) current.setAmount(current.getAmount() - 1);
        else player.getInventory().setItem(event.getHand(), null);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onUndeadSkeletonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof SkeletonHorse skeleton)) return;
        if (!isUndeadSkeleton(skeleton)) return;
        PersistentDataContainer data = skeleton.getPersistentDataContainer();
        ItemStack armor = readStoredArmor(data);
        if (armor != null) {
            data.remove(BetterHorseKeys.UNDEAD_ARMOR_DATA);
            skeleton.getWorld().dropItemNaturally(skeleton.getLocation(), armor);
        }
        ItemStack[] chestContents = readStoredChestContents(data);
        if (chestContents != null) {
            data.remove(BetterHorseKeys.UNDEAD_CHEST_CONTENTS);
            for (ItemStack item : chestContents) {
                if (item != null && !item.getType().isAir()) {
                    skeleton.getWorld().dropItemNaturally(skeleton.getLocation(), item);
                }
            }
        }
    }

    private boolean canUseUndeadTrait(AbstractHorse horse) {
        for (Entity passenger : horse.getPassengers()) {
            if (passenger instanceof Player player) {
                return PermissionUtils.canUseTrait(player, TRAIT);
            }
        }
        return !(horse.getOwner() instanceof Player player) || PermissionUtils.canUseTrait(player, TRAIT);
    }

    private boolean transformToSkeleton(AbstractHorse original) {
        SkeletonHorse skeleton = null;
        boolean committed = false;
        try {
            HorseSize.ensure(original.getPersistentDataContainer(), plugin.getConfig());
            Location location = original.getLocation();
            List<Entity> passengers = List.copyOf(original.getPassengers());
            PersistentDataContainer oldData = original.getPersistentDataContainer();
            double maxHealth = HorseStats.maxHealth(original);
            double speed = HorseStats.speed(original);
            double jump = HorseStats.jump(original);
            ItemStack armor = equipment(original);
            ItemStack saddle = original.getInventory().getSaddle();
            boolean carryingChest = original instanceof ChestedHorse chested && chested.isCarryingChest();
            // Complete serialization before the original can be removed.
            byte[] armorData = armor == null ? null : ItemStackSerialization.serialize(armor);
            String chestContents = carryingChest ? serializeStorageContents(original.getInventory().getStorageContents()) : null;
            skeleton = location.getWorld().spawn(location, SkeletonHorse.class);
            if (skeleton == null || !skeleton.isValid()) return false;
            copyBetterHorsesData(oldData, skeleton.getPersistentDataContainer());
            PersistentDataContainer data = skeleton.getPersistentDataContainer();
            data.set(BetterHorseKeys.UNDEAD_SKELETON, PersistentDataType.BYTE, (byte) 1);
            data.set(BetterHorseKeys.UNDEAD_ORIGINAL_TYPE, PersistentDataType.STRING, original.getType().name());
            data.set(BetterHorseKeys.UNDEAD_ORIGINAL_HEALTH, PersistentDataType.DOUBLE, maxHealth);
            data.set(BetterHorseKeys.UNDEAD_ORIGINAL_SPEED, PersistentDataType.DOUBLE, speed);
            data.set(BetterHorseKeys.UNDEAD_ORIGINAL_JUMP, PersistentDataType.DOUBLE, jump);
            if (original instanceof Horse h) {
                data.set(BetterHorseKeys.UNDEAD_ORIGINAL_COLOR, PersistentDataType.STRING, h.getColor().name());
                data.set(BetterHorseKeys.UNDEAD_ORIGINAL_STYLE, PersistentDataType.STRING, h.getStyle().name());
            }
            if (armorData != null) data.set(BetterHorseKeys.UNDEAD_ARMOR_DATA, PersistentDataType.BYTE_ARRAY, armorData);
            if (carryingChest) {
                data.set(BetterHorseKeys.UNDEAD_CHESTED, PersistentDataType.BYTE, (byte) 1);
                data.set(BetterHorseKeys.UNDEAD_CHEST_CONTENTS, PersistentDataType.STRING, chestContents);
            }
            applyStats(skeleton, maxHealth, speed, jump);
            applySize(skeleton);
            skeleton.setTamed(original.isTamed());
            skeleton.setOwner(original.getOwner());
            skeleton.getInventory().setSaddle(saddle == null ? null : saddle.clone());
            if (original.getCustomName() != null) {
                skeleton.setCustomName(original.getCustomName());
                skeleton.setCustomNameVisible(original.isCustomNameVisible());
            }
            if (!skeleton.isValid() || !original.isValid()) { skeleton.remove(); return false; }
            if (original.isLeashed()) original.getWorld().dropItemNaturally(location, new ItemStack(Material.LEAD));
            original.eject();
            original.remove();
            if (original.isValid()) throw new IllegalStateException("Original mount could not be removed");
            committed = true;
            remountNextTick(skeleton, passengers);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (!committed && skeleton != null) skeleton.remove();
            plugin.getLogger().log(java.util.logging.Level.WARNING, committed
                    ? "Undead transformation completed but deferred feedback failed"
                    : "Undead transformation failed; the source mount was retained", failure);
            return committed;
        }
    }

    private boolean transformBack(SkeletonHorse skeleton) { return transformBack(skeleton, () -> true); }

    private boolean transformBack(SkeletonHorse skeleton, BooleanSupplier paymentUnchanged) {
        AbstractHorse restored = null;
        boolean committed = false;
        try {
            HorseSize.ensure(skeleton.getPersistentDataContainer(), plugin.getConfig());
            PersistentDataContainer data = skeleton.getPersistentDataContainer();
            SupportedMountType type = SupportedMountType.fromName(data.get(BetterHorseKeys.UNDEAD_ORIGINAL_TYPE,
                    PersistentDataType.STRING)).orElse(SupportedMountType.HORSE);
            List<Entity> passengers = List.copyOf(skeleton.getPassengers());
            // Invalid saved cargo/equipment must never become a successful empty restoration.
            ItemStack armor = readStoredArmor(data);
            ItemStack[] cargo = readStoredChestContents(data);
            restored = type.spawn(skeleton.getLocation());
            if (restored == null || !restored.isValid()) return false;
            copyBetterHorsesData(data, restored.getPersistentDataContainer());
            cleanupUndeadKeys(restored.getPersistentDataContainer());
            Double storedHealth = HorseStats.stored(data, BetterHorseKeys.UNDEAD_ORIGINAL_HEALTH);
            Double storedSpeed = HorseStats.stored(data, BetterHorseKeys.UNDEAD_ORIGINAL_SPEED);
            Double storedJump = HorseStats.stored(data, BetterHorseKeys.UNDEAD_ORIGINAL_JUMP);
            double maxHealth = storedHealth == null ? HorseStats.maxHealth(skeleton) : storedHealth;
            applyStats(restored, maxHealth, storedSpeed == null ? HorseStats.speed(skeleton) : storedSpeed,
                    storedJump == null ? HorseStats.jump(skeleton) : storedJump);
            applySize(restored);
            restored.setTamed(skeleton.isTamed());
            restored.setOwner(skeleton.getOwner());
            restoreChestContents(restored, data, cargo);
            // The currently equipped saddle is authoritative; an old inventory snapshot never recreates it.
            ItemStack saddle = skeleton.getInventory().getSaddle();
            restored.getInventory().setSaddle(saddle == null ? null : saddle.clone());
            if (restored instanceof Horse horse) {
                setHorseVariant(horse, data.get(BetterHorseKeys.UNDEAD_ORIGINAL_COLOR, PersistentDataType.STRING),
                        data.get(BetterHorseKeys.UNDEAD_ORIGINAL_STYLE, PersistentDataType.STRING));
            }
            if (armor != null) {
                if (restored.getInventory().getSize() < 2) throw new IllegalArgumentException("Mount has no equipment slot");
                restored.getInventory().setItem(1, armor);
            }
            if (skeleton.getCustomName() != null) {
                restored.setCustomName(skeleton.getCustomName());
                restored.setCustomNameVisible(skeleton.isCustomNameVisible());
            }
            if (!restored.isValid() || !skeleton.isValid() || !paymentUnchanged.getAsBoolean()) {
                restored.remove(); return false;
            }
            skeleton.eject();
            skeleton.remove();
            if (skeleton.isValid()) throw new IllegalStateException("Skeleton mount could not be removed");
            committed = true;
            // Feedback and deferred remounting follow the successful transaction.
            if (plugin.getConfig().getBoolean("traits.undead.transform-back-animation", true)) {
                restored.getWorld().spawnParticle(TraitParticleResolver.getTraitParticle("undead", Particle.SOUL),
                        restored.getLocation().add(0, 1, 0), 40, .6, .7, .6, .03);
                restored.getWorld().playSound(restored.getLocation(), Sound.ENTITY_SKELETON_HORSE_AMBIENT, 1f, .8f);
            }
            remountNextTick(restored, passengers);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (!committed && restored != null) restored.remove();
            plugin.getLogger().log(java.util.logging.Level.WARNING, committed
                    ? "Undead restoration completed but deferred feedback failed"
                    : "Undead restoration failed; the source mount and restoration item were retained", failure);
            return committed;
        }
    }

    private void remountNextTick(AbstractHorse mount, List<Entity> passengers) {
        Runnable remount = () -> {
            if (!mount.isValid()) return;
            for (Entity passenger : passengers) {
                if (!(passenger instanceof Player)) continue;
                // The replacement callback owns only the mount after a passenger teleport.
                if (!Bukkit.isOwnedByCurrentRegion(passenger)) continue;
                if (!passenger.isValid() || passenger.getVehicle() != null || passenger.getWorld() != mount.getWorld()
                        || passenger.getLocation().distanceSquared(mount.getLocation()) > 16.0D) continue;
                mount.addPassenger(passenger);
            }
        };
        plugin.scheduler().entity(mount, 1L, ignored -> remount.run(), () -> { });
    }

    private Material getTransformBackItem() {
        try { return Material.valueOf(plugin.getConfig().getString("traits.undead.transform-back-item", "ENCHANTED_GOLDEN_APPLE").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return Material.ENCHANTED_GOLDEN_APPLE; }
    }
    private boolean isUndeadSkeleton(AbstractHorse horse) { return horse.getPersistentDataContainer().has(BetterHorseKeys.UNDEAD_SKELETON, PersistentDataType.BYTE); }
    private void applyStats(AbstractHorse horse, double health, double speed, double jump) {
        PersistentDataContainer data = horse.getPersistentDataContainer();
        data.set(BetterHorseKeys.HEALTH, PersistentDataType.DOUBLE, health);
        data.set(BetterHorseKeys.BASE_HEALTH, PersistentDataType.DOUBLE, health);
        data.set(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE, speed);
        data.set(BetterHorseKeys.BASE_SPEED, PersistentDataType.DOUBLE, speed);
        data.set(BetterHorseKeys.JUMP, PersistentDataType.DOUBLE, jump);
        data.set(BetterHorseKeys.BASE_JUMP, PersistentDataType.DOUBLE, jump);
        setAttribute(horse, AttributeResolver.generic("MAX_HEALTH"), health);
        setAttribute(horse, AttributeResolver.generic("MOVEMENT_SPEED"), speed);
        setAttribute(horse, AttributeResolver.horseJumpStrength(), jump);
        TrainingManager.recalculateAndApplyBonuses(horse);
        AttributeInstance maximum = horse.getAttribute(AttributeResolver.generic("MAX_HEALTH"));
        horse.setHealth(maximum == null ? health : maximum.getValue());
    }
    private void applySize(AbstractHorse horse) {
        SupportedMountType type = SupportedMountType.fromEntity(horse).orElse(SupportedMountType.HORSE);
        double base = 1.0D;
        if (MountConfig.isGrowthEnabled(plugin.getConfig(), type)) {
            int stage = horse.getPersistentDataContainer().getOrDefault(BetterHorseKeys.GROWTH_STAGE,
                    PersistentDataType.INTEGER, horse.isAdult() ? GrowthScale.MAX_STAGE : 1);
            base = GrowthScale.forStageUnclamped(stage, plugin.getConfig().getDouble("horse-growth-settings.max-size", 1.3D),
                    horse.isAdult(), GrowthScale.babyScale(type.name(), Bukkit.getBukkitVersion()));
            HorseSize.apply(horse, base, plugin.getConfig());
        } else {
            HorseSize.applyWithoutGrowth(horse, plugin.getConfig());
        }
    }

    private void setAttribute(AbstractHorse horse, Attribute attribute, double value) { AttributeInstance instance = horse.getAttribute(attribute); if (instance != null) instance.setBaseValue(value); }
    private void copyBetterHorsesData(PersistentDataContainer from, PersistentDataContainer to) {
        for (org.bukkit.NamespacedKey key : from.getKeys()) {
            if (!key.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT))) continue;
            copyKey(from, to, key, PersistentDataType.STRING); copyKey(from, to, key, PersistentDataType.DOUBLE); copyKey(from, to, key, PersistentDataType.LONG); copyKey(from, to, key, PersistentDataType.INTEGER); copyKey(from, to, key, PersistentDataType.BYTE); copyKey(from, to, key, PersistentDataType.BYTE_ARRAY);
        }
    }
    private <T, Z> void copyKey(PersistentDataContainer from, PersistentDataContainer to, org.bukkit.NamespacedKey key, PersistentDataType<T, Z> type) { if (from.has(key, type)) to.set(key, type, from.get(key, type)); }
    private void cleanupUndeadKeys(PersistentDataContainer data) { data.remove(BetterHorseKeys.UNDEAD_SKELETON); data.remove(BetterHorseKeys.UNDEAD_ORIGINAL_TYPE); data.remove(BetterHorseKeys.UNDEAD_ORIGINAL_HEALTH); data.remove(BetterHorseKeys.UNDEAD_ORIGINAL_SPEED); data.remove(BetterHorseKeys.UNDEAD_ORIGINAL_JUMP); data.remove(BetterHorseKeys.UNDEAD_ORIGINAL_COLOR); data.remove(BetterHorseKeys.UNDEAD_ORIGINAL_STYLE); data.remove(BetterHorseKeys.UNDEAD_ARMOR_DATA); data.remove(BetterHorseKeys.UNDEAD_CHESTED); data.remove(BetterHorseKeys.UNDEAD_CHEST_CONTENTS); }
    private ItemStack readStoredArmor(PersistentDataContainer data) {
        byte[] bytes = data.get(BetterHorseKeys.UNDEAD_ARMOR_DATA, PersistentDataType.BYTE_ARRAY);
        if (bytes != null) return ItemStackSerialization.deserialize(bytes);
        // Old llama snapshots stored their decor in inventory slot 1 rather than armor data.
        String original = data.get(BetterHorseKeys.UNDEAD_ORIGINAL_TYPE, PersistentDataType.STRING);
        String chest = data.get(BetterHorseKeys.UNDEAD_CHEST_CONTENTS, PersistentDataType.STRING);
        if ("LLAMA".equals(original) && chest != null && !chest.startsWith(CARGO_PREFIX)) {
            ItemStack[] legacy = decodeStorageContents(chest);
            if (legacy.length > 1) return legacy[1];
        }
        return null;
    }

    private ItemStack equipment(AbstractHorse horse) {
        ItemStack armor = HorseArmorUtils.getArmor(horse.getInventory());
        if (armor != null) return armor;
        return horse.getInventory().getSize() > 1 ? horse.getInventory().getItem(1) : null;
    }
    private void restoreChestContents(AbstractHorse horse, PersistentDataContainer data, ItemStack[] cargo) {
        if (!data.has(BetterHorseKeys.UNDEAD_CHESTED, PersistentDataType.BYTE)) return;
        if (!(horse instanceof ChestedHorse chestedHorse)) throw new IllegalArgumentException("Stored chest on an incompatible mount");
        if (cargo == null) throw new IllegalArgumentException("Stored chest has no cargo data");
        chestedHorse.setCarryingChest(true);
        if (cargo.length > horse.getInventory().getSize() - 2) throw new IllegalArgumentException("Stored cargo exceeds mount inventory");
        for (int i = 0; i < cargo.length; i++) horse.getInventory().setItem(i + 2, cargo[i]);
    }
    private String serializeStorageContents(ItemStack[] contents) {
        if (contents == null || contents.length < 2 || contents.length - 2 > MAX_CARGO_SLOTS)
            throw new IllegalArgumentException("Invalid mount inventory size");
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeInt(contents.length - 2);
            for (int i = 2; i < contents.length; i++) output.writeObject(contents[i]);
            output.flush();
            return CARGO_PREFIX + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("Could not preserve undead cargo", failure);
        }
    }
    private ItemStack[] readStoredChestContents(PersistentDataContainer data) {
        String serialized = data.get(BetterHorseKeys.UNDEAD_CHEST_CONTENTS, PersistentDataType.STRING);
        if (serialized == null) {
            if (data.has(BetterHorseKeys.UNDEAD_CHESTED, PersistentDataType.BYTE))
                throw new IllegalArgumentException("Stored chest is missing cargo data");
            return null;
        }
        ItemStack[] contents = decodeStorageContents(serialized);
        // Legacy storage arrays include saddle and decor. Only their cargo is restorable/dropable.
        if (!serialized.startsWith(CARGO_PREFIX)) {
            if (contents.length < 2) throw new IllegalArgumentException("Incomplete legacy inventory");
            return Arrays.copyOfRange(contents, 2, contents.length);
        }
        return contents;
    }

    private ItemStack[] decodeStorageContents(String serialized) {
        if (serialized.isBlank() || serialized.length() > 12 * 1024 * 1024)
            throw new IllegalArgumentException("Invalid stored cargo data");
        String payload = serialized.startsWith(CARGO_PREFIX) ? serialized.substring(CARGO_PREFIX.length()) : serialized;
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(payload));
             BukkitObjectInputStream input = new BukkitObjectInputStream(bytes)) {
            input.setObjectInputFilter(info -> info.depth() > 64 || info.references() > 100_000
                    || info.arrayLength() > 1_000_000 || info.streamBytes() > 8 * 1024 * 1024
                    ? ObjectInputFilter.Status.REJECTED : ObjectInputFilter.Status.UNDECIDED);
            int length = input.readInt();
            if (length < 0 || length > MAX_CARGO_SLOTS + 2) throw new IllegalArgumentException("Invalid cargo length");
            ItemStack[] contents = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                Object item = input.readObject();
                if (item != null && !(item instanceof ItemStack)) throw new IllegalArgumentException("Invalid cargo item");
                contents[i] = (ItemStack) item;
            }
            return contents;
        } catch (IOException | ClassNotFoundException | RuntimeException failure) {
            throw new IllegalArgumentException("Stored undead cargo could not be read", failure);
        }
    }
    private void setHorseVariant(Horse horse, String color, String style) { try { if (color != null) horse.setColor(Horse.Color.valueOf(color)); } catch (IllegalArgumentException ignored) {} try { if (style != null) horse.setStyle(Horse.Style.valueOf(style)); } catch (IllegalArgumentException ignored) {} }
}
