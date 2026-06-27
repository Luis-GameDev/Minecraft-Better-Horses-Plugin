package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.traits.TraitParticleResolver;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import me.luisgamedev.betterhorses.utils.HorseArmorUtils;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
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

import java.util.List;
import java.util.Locale;

public class UndeadTraitListener implements Listener {
    private static final String TRAIT = "undead";
    private final BetterHorses plugin = BetterHorses.getInstance();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof AbstractHorse horse)) return;
        if (horse instanceof SkeletonHorse) return;
        if (!SupportedMountType.isSupported(horse)) return;
        if (!TRAIT.equalsIgnoreCase(horse.getPersistentDataContainer().get(BetterHorseKeys.TRAIT, PersistentDataType.STRING))) return;
        if (event.getFinalDamage() < horse.getHealth()) return;
        event.setCancelled(true);
        transformToSkeleton(horse);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (!(event.getRightClicked() instanceof SkeletonHorse skeleton)) return;
        if (!isUndeadSkeleton(skeleton)) return;
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (item == null || item.getType() != getTransformBackItem()) return;
        event.setCancelled(true);
        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1); else event.getPlayer().getInventory().setItem(event.getHand(), null);
        transformBack(skeleton);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onUndeadSkeletonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof SkeletonHorse skeleton)) return;
        if (!isUndeadSkeleton(skeleton)) return;
        PersistentDataContainer data = skeleton.getPersistentDataContainer();
        ItemStack armor = readStoredArmor(data);
        if (armor == null) return;
        data.remove(BetterHorseKeys.UNDEAD_ARMOR_DATA);
        skeleton.getWorld().dropItemNaturally(skeleton.getLocation(), armor);
    }

    private void transformToSkeleton(AbstractHorse original) {
        Location location = original.getLocation();
        List<Entity> passengers = List.copyOf(original.getPassengers());
        PersistentDataContainer oldData = original.getPersistentDataContainer();
        double maxHealth = getAttribute(original, AttributeResolver.generic("MAX_HEALTH"), 20.0);
        double speed = getAttribute(original, AttributeResolver.generic("MOVEMENT_SPEED"), 0.2);
        double jump = getAttribute(original, AttributeResolver.horseJumpStrength(), 0.7);
        ItemStack armor = HorseArmorUtils.getArmor(original.getInventory());
        ItemStack saddle = original.getInventory().getSaddle();

        SkeletonHorse skeleton = location.getWorld().spawn(location, SkeletonHorse.class);
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
        if (armor != null) data.set(BetterHorseKeys.UNDEAD_ARMOR_DATA, PersistentDataType.BYTE_ARRAY, armor.serializeAsBytes());
        applyStats(skeleton, maxHealth, speed, jump);
        skeleton.setHealth(maxHealth);
        skeleton.setTamed(original.isTamed());
        skeleton.setOwner(original.getOwner());
        skeleton.getInventory().setSaddle(saddle == null ? null : saddle.clone());
        if (original.getCustomName() != null) {
            skeleton.setCustomName(original.getCustomName());
            skeleton.setCustomNameVisible(original.isCustomNameVisible());
        }
        if (original.isLeashed()) original.getWorld().dropItemNaturally(original.getLocation(), new ItemStack(Material.LEAD));
        original.eject();
        original.remove();
        remountNextTick(skeleton, passengers);
    }

    private void transformBack(SkeletonHorse skeleton) {
        PersistentDataContainer data = skeleton.getPersistentDataContainer();
        SupportedMountType type = SupportedMountType.fromName(data.get(BetterHorseKeys.UNDEAD_ORIGINAL_TYPE, PersistentDataType.STRING)).orElse(SupportedMountType.HORSE);
        List<Entity> passengers = List.copyOf(skeleton.getPassengers());
        AbstractHorse restored = type.spawn(skeleton.getLocation());
        copyBetterHorsesData(data, restored.getPersistentDataContainer());
        cleanupUndeadKeys(restored.getPersistentDataContainer());
        double maxHealth = data.getOrDefault(BetterHorseKeys.UNDEAD_ORIGINAL_HEALTH, PersistentDataType.DOUBLE, getAttribute(skeleton, AttributeResolver.generic("MAX_HEALTH"), 20.0));
        applyStats(restored, maxHealth,
                data.getOrDefault(BetterHorseKeys.UNDEAD_ORIGINAL_SPEED, PersistentDataType.DOUBLE, getAttribute(skeleton, AttributeResolver.generic("MOVEMENT_SPEED"), 0.2)),
                data.getOrDefault(BetterHorseKeys.UNDEAD_ORIGINAL_JUMP, PersistentDataType.DOUBLE, getAttribute(skeleton, AttributeResolver.horseJumpStrength(), 0.7)));
        restored.setHealth(maxHealth);
        restored.setTamed(skeleton.isTamed());
        restored.setOwner(skeleton.getOwner());
        restored.getInventory().setSaddle(skeleton.getInventory().getSaddle());
        if (restored instanceof Horse horse) {
            setHorseVariant(horse, data.get(BetterHorseKeys.UNDEAD_ORIGINAL_COLOR, PersistentDataType.STRING), data.get(BetterHorseKeys.UNDEAD_ORIGINAL_STYLE, PersistentDataType.STRING));
            ItemStack armor = readStoredArmor(data);
            if (armor != null) HorseArmorUtils.setArmor(restored.getInventory(), armor);
        }
        if (skeleton.getCustomName() != null) {
            restored.setCustomName(skeleton.getCustomName());
            restored.setCustomNameVisible(skeleton.isCustomNameVisible());
        }
        if (plugin.getConfig().getBoolean("traits.undead.transform-back-animation", true)) {
            restored.getWorld().spawnParticle(TraitParticleResolver.getTraitParticle("undead", Particle.SOUL), restored.getLocation().add(0, 1, 0), 40, .6, .7, .6, .03);
            restored.getWorld().playSound(restored.getLocation(), Sound.ENTITY_SKELETON_HORSE_AMBIENT, 1f, .8f);
        }
        skeleton.eject();
        skeleton.remove();
        remountNextTick(restored, passengers);
    }

    private void remountNextTick(AbstractHorse mount, List<Entity> passengers) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!mount.isValid()) return;
            for (Entity passenger : passengers) {
                if (passenger instanceof Player && passenger.isValid() && passenger.getVehicle() == null) mount.addPassenger(passenger);
            }
        });
    }

    private Material getTransformBackItem() {
        try { return Material.valueOf(plugin.getConfig().getString("traits.undead.transform-back-item", "ENCHANTED_GOLDEN_APPLE").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return Material.ENCHANTED_GOLDEN_APPLE; }
    }
    private boolean isUndeadSkeleton(AbstractHorse horse) { return horse.getPersistentDataContainer().has(BetterHorseKeys.UNDEAD_SKELETON, PersistentDataType.BYTE); }
    private void applyStats(AbstractHorse horse, double health, double speed, double jump) { setAttribute(horse, AttributeResolver.generic("MAX_HEALTH"), health); setAttribute(horse, AttributeResolver.generic("MOVEMENT_SPEED"), speed); setAttribute(horse, AttributeResolver.horseJumpStrength(), jump); }
    private double getAttribute(AbstractHorse horse, Attribute attribute, double fallback) { AttributeInstance instance = horse.getAttribute(attribute); return instance == null ? fallback : instance.getValue(); }
    private void setAttribute(AbstractHorse horse, Attribute attribute, double value) { AttributeInstance instance = horse.getAttribute(attribute); if (instance != null) instance.setBaseValue(value); }
    private void copyBetterHorsesData(PersistentDataContainer from, PersistentDataContainer to) {
        for (org.bukkit.NamespacedKey key : from.getKeys()) {
            if (!key.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT))) continue;
            copyKey(from, to, key, PersistentDataType.STRING); copyKey(from, to, key, PersistentDataType.DOUBLE); copyKey(from, to, key, PersistentDataType.LONG); copyKey(from, to, key, PersistentDataType.INTEGER); copyKey(from, to, key, PersistentDataType.BYTE); copyKey(from, to, key, PersistentDataType.BYTE_ARRAY);
        }
    }
    private <T, Z> void copyKey(PersistentDataContainer from, PersistentDataContainer to, org.bukkit.NamespacedKey key, PersistentDataType<T, Z> type) { if (from.has(key, type)) to.set(key, type, from.get(key, type)); }
    private void cleanupUndeadKeys(PersistentDataContainer data) { data.remove(BetterHorseKeys.UNDEAD_SKELETON); data.remove(BetterHorseKeys.UNDEAD_ORIGINAL_TYPE); data.remove(BetterHorseKeys.UNDEAD_ORIGINAL_HEALTH); data.remove(BetterHorseKeys.UNDEAD_ORIGINAL_SPEED); data.remove(BetterHorseKeys.UNDEAD_ORIGINAL_JUMP); data.remove(BetterHorseKeys.UNDEAD_ORIGINAL_COLOR); data.remove(BetterHorseKeys.UNDEAD_ORIGINAL_STYLE); data.remove(BetterHorseKeys.UNDEAD_ARMOR_DATA); }
    private ItemStack readStoredArmor(PersistentDataContainer data) { byte[] bytes = data.get(BetterHorseKeys.UNDEAD_ARMOR_DATA, PersistentDataType.BYTE_ARRAY); if (bytes == null || bytes.length == 0) return null; try { return ItemStack.deserializeBytes(bytes); } catch (Exception ignored) { return null; } }
    private void setHorseVariant(Horse horse, String color, String style) { try { if (color != null) horse.setColor(Horse.Color.valueOf(color)); } catch (IllegalArgumentException ignored) {} try { if (style != null) horse.setStyle(Horse.Style.valueOf(style)); } catch (IllegalArgumentException ignored) {} }
}
