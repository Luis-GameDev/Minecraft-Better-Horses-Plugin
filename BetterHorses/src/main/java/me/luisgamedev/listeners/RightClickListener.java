package me.luisgamedev.listeners;

import me.luisgamedev.BetterHorses;
import me.luisgamedev.language.LanguageManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class RightClickListener implements Listener {

        @EventHandler
        public void onRightClick(PlayerInteractEvent event) {

            // Check for proper right click with item
            if (event.getHand() != EquipmentSlot.HAND) return;

            Action action = event.getAction();
            if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

            Player player = event.getPlayer();
            ItemStack item = player.getInventory().getItemInMainHand();

            if (item == null || item.getType() == Material.AIR) return;


            LanguageManager lang = BetterHorses.getInstance().getLang();
            FileConfiguration config = BetterHorses.getInstance().getConfig();

            String configuredItem = BetterHorses.getInstance().getConfig().getString("settings.horse-item", "SADDLE");
            Material expectedMaterial = Material.getMaterial(configuredItem.toUpperCase());
            if (expectedMaterial == null || !expectedMaterial.isItem()) expectedMaterial = Material.SADDLE;

            if (item == null || item.getType() != expectedMaterial || !item.hasItemMeta()) {
                player.sendMessage(lang.get("messages.invalid-item"));
                return;
            }
            if(!config.getBoolean("settings.allow-rightclick-spawn")) return;

            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer data = meta.getPersistentDataContainer();

            Double health = data.get(new NamespacedKey(BetterHorses.getInstance(), "health"), PersistentDataType.DOUBLE);
            Double currentHealth = data.get(new NamespacedKey(BetterHorses.getInstance(), "current_health"), PersistentDataType.DOUBLE);
            Double speed = data.get(new NamespacedKey(BetterHorses.getInstance(), "speed"), PersistentDataType.DOUBLE);
            Double jump = data.get(new NamespacedKey(BetterHorses.getInstance(), "jump"), PersistentDataType.DOUBLE);
            String gender = data.get(new NamespacedKey(BetterHorses.getInstance(), "gender"), PersistentDataType.STRING);
            String ownerUUID = player.getUniqueId().toString();
            String styleStr = data.get(new NamespacedKey(BetterHorses.getInstance(), "style"), PersistentDataType.STRING);
            String colorStr = data.get(new NamespacedKey(BetterHorses.getInstance(), "color"), PersistentDataType.STRING);
            String saddleStr = data.get(new NamespacedKey(BetterHorses.getInstance(), "saddle"), PersistentDataType.STRING);
            String armorStr = data.get(new NamespacedKey(BetterHorses.getInstance(), "armor"), PersistentDataType.STRING);
            String customName = data.get(new NamespacedKey(BetterHorses.getInstance(), "name"), PersistentDataType.STRING);
            String trait = data.get(new NamespacedKey(BetterHorses.getInstance(), "trait"), PersistentDataType.STRING);
            Byte neutered = data.get(new NamespacedKey(BetterHorses.getInstance(), "neutered"), PersistentDataType.BYTE);

            if (health == null || speed == null || jump == null || gender == null) {
                player.sendMessage(lang.get("messages.invalid-horse-data"));
                return;
            }

            Horse horse;
            try {
                horse = player.getWorld().spawn(player.getLocation(), Horse.class);
            } catch (Exception e) {
                player.sendMessage(lang.get("messages.cant-spawn"));
                return;
            }

            if (horse == null || !horse.isValid()) {
                player.sendMessage(lang.get("messages.cant-spawn"));
                return;
            }

            setAttribute(horse, Attribute.GENERIC_MAX_HEALTH, health);
            setAttribute(horse, Attribute.GENERIC_MOVEMENT_SPEED, speed);
            setAttribute(horse, Attribute.HORSE_JUMP_STRENGTH, jump);
            horse.setHealth(currentHealth != null ? currentHealth : health);
            horse.setTamed(true);
            horse.setOwner(player);

            horse.getPersistentDataContainer().set(
                    new NamespacedKey(BetterHorses.getInstance(), "owner"),
                    PersistentDataType.STRING,
                    ownerUUID
            );

            if (gender != null) {
                horse.getPersistentDataContainer().set(new NamespacedKey(BetterHorses.getInstance(), "gender"), PersistentDataType.STRING, gender);
            }

            if (trait != null && !trait.isBlank()) {
                horse.getPersistentDataContainer().set(new NamespacedKey(BetterHorses.getInstance(), "trait"), PersistentDataType.STRING, trait);
            }

            if (neutered != null && neutered == (byte) 1) {
                horse.getPersistentDataContainer().set(new NamespacedKey(BetterHorses.getInstance(), "neutered"), PersistentDataType.BYTE, (byte) 1);
            }

            if (customName != null && !customName.isBlank()) {
                horse.setCustomName(customName);
                horse.setCustomNameVisible(true);
            }

            try {
                horse.setStyle(Horse.Style.valueOf(styleStr));
                horse.setColor(Horse.Color.valueOf(colorStr));
            } catch (Exception ignored) {}

            if (saddleStr != null) {
                horse.getInventory().setSaddle(new ItemStack(Material.valueOf(saddleStr)));
            }
            if (armorStr != null) {
                horse.getInventory().setArmor(new ItemStack(Material.valueOf(armorStr)));
            }

            item.setAmount(item.getAmount() - 1);
            player.sendMessage(lang.get("messages.horse-respawned"));
            return;
        }

        private static void setAttribute(Horse horse, Attribute attribute, double value) {
            AttributeInstance attr = horse.getAttribute(attribute);
            if (attr != null) {
                attr.setBaseValue(value);
            }
        }
    }
