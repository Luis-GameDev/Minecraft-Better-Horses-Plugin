package me.luisgamedev.betterhorses.api;

import jdk.jfr.Description;
import me.luisgamedev.betterhorses.BetterHorses;
import me.luisgamedev.betterhorses.api.events.BetterHorseDespawnEvent;
import me.luisgamedev.betterhorses.api.events.BetterHorseSpawnEvent;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.traits.TraitRegistry;
import me.luisgamedev.betterhorses.training.TrainingManager;
import me.luisgamedev.betterhorses.utils.AttributeResolver;
import me.luisgamedev.betterhorses.utils.HorseArmorUtils;
import me.luisgamedev.betterhorses.utils.MountConfig;
import me.luisgamedev.betterhorses.utils.SupportedMountType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.AbstractHorseInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

public class BetterHorsesAPI {

    @Description("Creates a horseitem and either returns the ItemStack or directly puts it into the provided Inventory")
    public static ItemStack createHorseItem(@Nonnull double health, @Nonnull double speed, @Nonnull double jump, @Nonnull String gender, @Nullable String name, @Nullable Player owner, @Nullable Inventory targetInventory, @Nullable boolean dropIfFull, @Nullable String traitOverride, @Nonnull boolean isNeutered, @Nonnull Integer growthStage, @Nullable SupportedMountType mountType) {

        BetterHorses plugin = BetterHorses.getInstance();
        LanguageManager lang = plugin.getLang();
        plugin.debugLog("API_CREATE_ITEM", "START", true, "Creating horse item health=" + health + ", speed=" + speed + ", jump=" + jump + ".");
        SupportedMountType targetMountType = mountType == null ? SupportedMountType.HORSE : mountType;

        String genderSymbol = gender.equals("male") ? lang.getRaw("messages.gender-male") : gender.equals("female") ? lang.getRaw("messages.gender-female") : "?";

        String materialName = plugin.getConfig().getString("settings.horse-item", "SADDLE");
        Material material = Material.getMaterial(materialName.toUpperCase());
        if (material == null || !material.isItem()) material = Material.SADDLE;
        plugin.debugLog("API_CREATE_ITEM", "MATERIAL", true, "Using horse item material " + material + ".");

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        int growth = growthStage > 10 || growthStage < 1 ? 10 : growthStage;

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(BetterHorseKeys.GENDER, PersistentDataType.STRING, gender);
        data.set(BetterHorseKeys.HEALTH, PersistentDataType.DOUBLE, health);
        data.set(BetterHorseKeys.CURRENT_HEALTH, PersistentDataType.DOUBLE, health);
        data.set(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE, speed);
        data.set(BetterHorseKeys.JUMP, PersistentDataType.DOUBLE, jump);
        if(owner != null) {
            data.set(BetterHorseKeys.OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());
        }
        data.set(BetterHorseKeys.NAME, PersistentDataType.STRING, name.replace(ChatColor.GOLD.toString(), ""));
        data.set(BetterHorseKeys.STYLE, PersistentDataType.STRING, Horse.Style.WHITE.name());
        data.set(BetterHorseKeys.COLOR, PersistentDataType.STRING, Horse.Color.CREAMY.name());
        data.set(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER, growth);
        data.set(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING, targetMountType.getEntityType().name());
        if (isNeutered) {
            data.set(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE, (byte) 1);
        }
        TrainingManager.ensureTrainingData(data);

        FileConfiguration config = plugin.getConfig();
        String traitForLore = null;
        if (config.getBoolean("traits.enabled")) {
            ConfigurationSection traitsSection = config.getConfigurationSection("traits");

            if (traitOverride != null) {
                if (!traitOverride.equalsIgnoreCase("none") && traitsSection != null && traitsSection.isConfigurationSection(traitOverride)) {
                    ConfigurationSection traitConfig = traitsSection.getConfigurationSection(traitOverride);
                    if (traitConfig.getBoolean("enabled", false)) {
                        data.set(BetterHorseKeys.TRAIT, PersistentDataType.STRING, traitOverride.toLowerCase());
                        traitForLore = traitOverride;
                    }
                }
            } else if (traitsSection != null) {
                for (String trait : traitsSection.getKeys(false)) {
                    if (trait.equals("enabled")) continue;
                    ConfigurationSection tSec = traitsSection.getConfigurationSection(trait);
                    if (tSec == null || !tSec.getBoolean("enabled", false)) continue;

                    double chance = tSec.getDouble("chance", 0);
                    if (Math.random() < chance) {
                        data.set(BetterHorseKeys.TRAIT, PersistentDataType.STRING, trait.toLowerCase());
                        traitForLore = trait;
                        break;
                    }
                }
            }
        }

        List<String> lore = buildHorseLore(
                config,
                lang,
                data,
                genderSymbol,
                health,
                health,
                speed,
                jump,
                growth,
                traitForLore,
                isNeutered
        );

        meta.setLore(lore);
        meta.setDisplayName(name);
        item.setItemMeta(meta);

        if(targetInventory != null) {
            plugin.debugLog("API_CREATE_ITEM", "INVENTORY", true, "Adding horse item to target inventory.");
            HashMap<Integer, ItemStack> leftovers = targetInventory.addItem(item);
            if (!leftovers.isEmpty() && dropIfFull) {
                owner.getWorld().dropItem(owner.getLocation(), item);
            }
        }

        plugin.debugLog("API_CREATE_ITEM", "COMPLETE", true, "Horse item created with mount type " + targetMountType.getEntityType() + ".");
        return item;
    }

    public static Optional<BetterHorse> getBetterHorse(AbstractHorse horse) {
        if (!isBetterHorse(horse)) return Optional.empty();
        return Optional.of(new BetterHorse(horse));
    }

    public static Optional<BetterHorseItem> getBetterHorse(ItemStack item) {
        if (!isHorseItem(item)) return Optional.empty();
        return Optional.of(new BetterHorseItem(item));
    }

    public static @Nullable AbstractHorse toHorse(@Nonnull ItemStack item, @Nonnull Player player) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer data = meta.getPersistentDataContainer();

        Double health = data.get(BetterHorseKeys.HEALTH, PersistentDataType.DOUBLE);
        Double currentHealth = data.get(BetterHorseKeys.CURRENT_HEALTH, PersistentDataType.DOUBLE);
        Double speed = data.get(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE);
        Double jump = data.get(BetterHorseKeys.JUMP, PersistentDataType.DOUBLE);
        String gender = data.get(BetterHorseKeys.GENDER, PersistentDataType.STRING);
        String ownerUUID = player.getUniqueId().toString();
        String styleStr = data.get(BetterHorseKeys.STYLE, PersistentDataType.STRING);
        String colorStr = data.get(BetterHorseKeys.COLOR, PersistentDataType.STRING);
        String saddleStr = data.get(BetterHorseKeys.SADDLE, PersistentDataType.STRING);
        String armorStr = data.get(BetterHorseKeys.ARMOR, PersistentDataType.STRING);
        String armorData = data.get(BetterHorseKeys.ARMOR_DATA, PersistentDataType.STRING);
        String customName = data.get(BetterHorseKeys.NAME, PersistentDataType.STRING);
        String trait = data.get(BetterHorseKeys.TRAIT, PersistentDataType.STRING);
        Byte neutered = data.get(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE);
        Integer storedStage = data.get(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER);
        String mountTypeName = data.get(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING);
        long brushTrainingCooldown = data.getOrDefault(BetterHorseKeys.TRAINING_BRUSH_COOLDOWN, PersistentDataType.LONG, 0L);
        long feedTrainingCooldown = data.getOrDefault(BetterHorseKeys.TRAINING_FEED_COOLDOWN, PersistentDataType.LONG, 0L);
        Long cooldown = data.has(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG)
                ? data.get(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG)
                : null;

        int growthStage = storedStage != null ? storedStage : 10;
        SupportedMountType mountType = SupportedMountType.fromNameOrDefault(mountTypeName);

        if (health == null || speed == null || jump == null || gender == null) {
            return null;
        }

        if (!mountType.isEnabled(BetterHorses.getInstance().getConfig())) {
            return null;
        }

        AbstractHorse horse;
        try {
            horse = mountType.spawn(player.getLocation());
        } catch (Exception e) {
            return null;
        }

        if (horse == null || !horse.isValid()) {
            return null;
        }

        double maxScale = BetterHorses.getInstance().getConfig().getDouble("horse-growth-settings.max-size", 1.3);
        int threshold = BetterHorses.getInstance().getConfig().getInt("horse-growth-settings.ride-and-breed-threshhold", 7);
        float minScale = (growthStage >= threshold) ? 0.85f : 0.7f;
        double scale = minScale + ((maxScale - minScale) / 10.0) * growthStage;

        if (MountConfig.isGrowthEnabled(BetterHorses.getInstance().getConfig(), mountType)) {
            setAttribute(horse, Attribute.valueOf("SCALE"), scale);
            if (growthStage >= threshold) {
                horse.setAdult();
                horse.setAgeLock(false);
            } else {
                horse.setBaby();
                horse.setAgeLock(true);
            }
        }

        horse.getPersistentDataContainer().set(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER, growthStage);

        setAttribute(horse, AttributeResolver.generic("MAX_HEALTH"), health);
        setAttribute(horse, AttributeResolver.generic("MOVEMENT_SPEED"), speed);
        setAttribute(horse, Attribute.valueOf("HORSE_JUMP_STRENGTH"), jump);
        horse.setHealth(currentHealth != null ? currentHealth : health);
        horse.setTamed(true);
        horse.setOwner((AnimalTamer) player);

        PersistentDataContainer horseData = horse.getPersistentDataContainer();
        horseData.set(BetterHorseKeys.BASE_HEALTH, PersistentDataType.DOUBLE,
                data.getOrDefault(BetterHorseKeys.BASE_HEALTH, PersistentDataType.DOUBLE, health));
        horseData.set(BetterHorseKeys.BASE_SPEED, PersistentDataType.DOUBLE,
                data.getOrDefault(BetterHorseKeys.BASE_SPEED, PersistentDataType.DOUBLE, speed));
        horseData.set(BetterHorseKeys.BASE_JUMP, PersistentDataType.DOUBLE,
                data.getOrDefault(BetterHorseKeys.BASE_JUMP, PersistentDataType.DOUBLE, jump));
        horseData.set(BetterHorseKeys.TRAINING_RIDING_UNITS, PersistentDataType.DOUBLE,
                data.getOrDefault(BetterHorseKeys.TRAINING_RIDING_UNITS, PersistentDataType.DOUBLE, 0.0));
        horseData.set(BetterHorseKeys.TRAINING_BRUSHING_UNITS, PersistentDataType.DOUBLE,
                data.getOrDefault(BetterHorseKeys.TRAINING_BRUSHING_UNITS, PersistentDataType.DOUBLE, 0.0));
        horseData.set(BetterHorseKeys.TRAINING_FEEDING_UNITS, PersistentDataType.DOUBLE,
                data.getOrDefault(BetterHorseKeys.TRAINING_FEEDING_UNITS, PersistentDataType.DOUBLE, 0.0));
        horseData.set(BetterHorseKeys.TRAINING_BRUSH_COOLDOWN, PersistentDataType.LONG, brushTrainingCooldown);
        horseData.set(BetterHorseKeys.TRAINING_FEED_COOLDOWN, PersistentDataType.LONG, feedTrainingCooldown);

        horseData.set(BetterHorseKeys.OWNER, PersistentDataType.STRING, ownerUUID);
        horseData.set(BetterHorseKeys.GENDER, PersistentDataType.STRING, gender);
        horseData.set(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING, mountType.getEntityType().name());

        if (trait != null && !trait.isBlank()) {
            horseData.set(BetterHorseKeys.TRAIT, PersistentDataType.STRING, trait);
        }
        if (neutered != null && neutered == (byte) 1) {
            horseData.set(BetterHorseKeys.NEUTERED, PersistentDataType.BYTE, (byte) 1);
        }
        if (cooldown != null) {
            horseData.set(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG, cooldown);
        }

        if (customName != null && !customName.isBlank()) {
            horse.setCustomName(customName);
            horse.setCustomNameVisible(true);
        }

        if (horse instanceof Horse h) {
            try {
                h.setStyle(Horse.Style.valueOf(styleStr));
                h.setColor(Horse.Color.valueOf(colorStr));
            } catch (Exception ignored) {}
        }

        if (saddleStr != null) {
            horse.getInventory().setSaddle(new ItemStack(Material.valueOf(saddleStr)));
        }
        if (armorStr != null) {
            ItemStack armorItem = restoreArmorItem(armorStr, armorData);
            if (armorItem != null) {
                HorseArmorUtils.setArmor(horse.getInventory(), armorItem);
            }
        }

        return horse;
    }

    public static @Nullable ItemStack toItem(@Nonnull AbstractHorse horse, @Nullable Player ownerOverride) {
        BetterHorses plugin = BetterHorses.getInstance();
        LanguageManager lang = plugin.getLang();
        PersistentDataContainer data = horse.getPersistentDataContainer();

        SupportedMountType mountType = SupportedMountType.fromEntity(horse)
                .filter(type -> type.isEnabled(plugin.getConfig()))
                .orElse(null);
        if (mountType == null) return null;

        NamespacedKey genderKey = BetterHorseKeys.GENDER;
        NamespacedKey traitKey = BetterHorseKeys.TRAIT;
        NamespacedKey neuterKey = BetterHorseKeys.NEUTERED;
        NamespacedKey growthKey = BetterHorseKeys.GROWTH_STAGE;
        NamespacedKey cooldownKey = BetterHorseKeys.COOLDOWN;

        String gender;
        if (!data.has(genderKey, PersistentDataType.STRING)) {
            gender = Math.random() < 0.5 ? "male" : "female";
            data.set(genderKey, PersistentDataType.STRING, gender);
        } else {
            gender = data.getOrDefault(genderKey, PersistentDataType.STRING, "unknown");
        }

        String trait = data.has(traitKey, PersistentDataType.STRING) ? data.get(traitKey, PersistentDataType.STRING) : null;
        boolean isNeutered = data.has(neuterKey, PersistentDataType.BYTE) && data.get(neuterKey, PersistentDataType.BYTE) == (byte) 1;
        Long cooldown = data.has(cooldownKey, PersistentDataType.LONG) ? data.get(cooldownKey, PersistentDataType.LONG) : null;

        int growthStage;
        if (MountConfig.isGrowthEnabled(plugin.getConfig(), mountType)) {
            growthStage = data.has(growthKey, PersistentDataType.INTEGER) ? data.get(growthKey, PersistentDataType.INTEGER) : 10;
        } else {
            growthStage = 10;
        }

        String genderSymbol = gender.equalsIgnoreCase("male") ? lang.getRaw("messages.gender-male") : gender.equalsIgnoreCase("female") ? lang.getRaw("messages.gender-female") : "?";

        TraitRegistry.revertDashBoostIfActive(horse);
        TrainingManager.ensureBaseStats(horse);

        double maxHealth = horse.getAttribute(AttributeResolver.generic("MAX_HEALTH")).getBaseValue();
        double currentHealth = horse.getHealth();
        double speed = horse.getAttribute(AttributeResolver.generic("MOVEMENT_SPEED")).getBaseValue();
        AttributeInstance jumpAttr = horse.getAttribute(Attribute.valueOf("HORSE_JUMP_STRENGTH"));
        double jump = jumpAttr != null ? jumpAttr.getBaseValue() : 0.0;

        Horse.Style style = horse instanceof Horse ? ((Horse) horse).getStyle() : Horse.Style.WHITE;
        Horse.Color color = horse instanceof Horse ? ((Horse) horse).getColor() : Horse.Color.WHITE;
        AbstractHorseInventory inv = horse.getInventory();
        ItemStack saddle = inv.getSaddle();
        ItemStack armor = HorseArmorUtils.getArmor(inv);

        ItemStack item = new ItemStack(getHorseItemMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer itemData = meta.getPersistentDataContainer();

        itemData.set(genderKey, PersistentDataType.STRING, gender);
        String name = horse.getCustomName() != null ? horse.getCustomName() : mountType.getDisplayName(lang);
        meta.setDisplayName(ChatColor.GOLD + name + " " + genderSymbol);

        List<String> lore = buildHorseLore(
                plugin.getConfig(),
                lang,
                data,
                genderSymbol,
                currentHealth,
                maxHealth,
                speed,
                jump,
                growthStage,
                trait,
                isNeutered
        );
        meta.setLore(lore);

        if (horse.getCustomName() != null) {
            itemData.set(BetterHorseKeys.NAME, PersistentDataType.STRING, horse.getCustomName());
        }
        itemData.set(BetterHorseKeys.HEALTH, PersistentDataType.DOUBLE, maxHealth);
        itemData.set(BetterHorseKeys.CURRENT_HEALTH, PersistentDataType.DOUBLE, currentHealth);
        itemData.set(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE, speed);
        itemData.set(BetterHorseKeys.JUMP, PersistentDataType.DOUBLE, jump);
        itemData.set(BetterHorseKeys.BASE_HEALTH, PersistentDataType.DOUBLE, data.getOrDefault(BetterHorseKeys.BASE_HEALTH, PersistentDataType.DOUBLE, maxHealth));
        itemData.set(BetterHorseKeys.BASE_SPEED, PersistentDataType.DOUBLE, data.getOrDefault(BetterHorseKeys.BASE_SPEED, PersistentDataType.DOUBLE, speed));
        itemData.set(BetterHorseKeys.BASE_JUMP, PersistentDataType.DOUBLE, data.getOrDefault(BetterHorseKeys.BASE_JUMP, PersistentDataType.DOUBLE, jump));
        itemData.set(BetterHorseKeys.TRAINING_RIDING_UNITS, PersistentDataType.DOUBLE, data.getOrDefault(BetterHorseKeys.TRAINING_RIDING_UNITS, PersistentDataType.DOUBLE, 0.0));
        itemData.set(BetterHorseKeys.TRAINING_BRUSHING_UNITS, PersistentDataType.DOUBLE, data.getOrDefault(BetterHorseKeys.TRAINING_BRUSHING_UNITS, PersistentDataType.DOUBLE, 0.0));
        itemData.set(BetterHorseKeys.TRAINING_FEEDING_UNITS, PersistentDataType.DOUBLE, data.getOrDefault(BetterHorseKeys.TRAINING_FEEDING_UNITS, PersistentDataType.DOUBLE, 0.0));
        itemData.set(BetterHorseKeys.TRAINING_BRUSH_COOLDOWN, PersistentDataType.LONG, data.getOrDefault(BetterHorseKeys.TRAINING_BRUSH_COOLDOWN, PersistentDataType.LONG, 0L));
        itemData.set(BetterHorseKeys.TRAINING_FEED_COOLDOWN, PersistentDataType.LONG, data.getOrDefault(BetterHorseKeys.TRAINING_FEED_COOLDOWN, PersistentDataType.LONG, 0L));

        String ownerUUID = ownerOverride != null
                ? ownerOverride.getUniqueId().toString()
                : Optional.ofNullable(horse.getOwner()).map(AnimalTamer::getUniqueId).map(Object::toString).orElse(null);
        if (ownerUUID != null) {
            itemData.set(BetterHorseKeys.OWNER, PersistentDataType.STRING, ownerUUID);
        }

        itemData.set(BetterHorseKeys.STYLE, PersistentDataType.STRING, style.name());
        itemData.set(BetterHorseKeys.COLOR, PersistentDataType.STRING, color.name());
        itemData.set(BetterHorseKeys.GROWTH_STAGE, PersistentDataType.INTEGER, growthStage);
        itemData.set(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING, mountType.getEntityType().name());
        if (trait != null) itemData.set(traitKey, PersistentDataType.STRING, trait.toLowerCase());
        if (isNeutered) itemData.set(neuterKey, PersistentDataType.BYTE, (byte) 1);
        if (cooldown != null) itemData.set(BetterHorseKeys.COOLDOWN, PersistentDataType.LONG, cooldown);
        if (saddle != null) itemData.set(BetterHorseKeys.SADDLE, PersistentDataType.STRING, saddle.getType().name());
        if (armor != null) {
            itemData.set(BetterHorseKeys.ARMOR, PersistentDataType.STRING, armor.getType().name());
            itemData.set(BetterHorseKeys.ARMOR_DATA, PersistentDataType.STRING, Base64.getEncoder().encodeToString(armor.serializeAsBytes()));
        }

        item.setItemMeta(meta);
        return item;
    }

    private static @Nullable ItemStack restoreArmorItem(String armorMaterial, @Nullable String serializedArmor) {
        if (serializedArmor != null && !serializedArmor.isBlank()) {
            try {
                return ItemStack.deserializeBytes(Base64.getDecoder().decode(serializedArmor));
            } catch (IllegalArgumentException ignored) {
            }
        }

        try {
            return new ItemStack(Material.valueOf(armorMaterial));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static List<String> buildHorseLore(
            FileConfiguration config,
            LanguageManager lang,
            PersistentDataContainer data,
            String genderSymbol,
            double currentHealth,
            double maxHealth,
            double speed,
            double jump,
            int growth,
            @Nullable String trait,
            boolean isNeutered
    ) {
        List<String> lore = new ArrayList<>();
        Object layout = config.get("settings.horse-item-lore-layout");
        if (!(layout instanceof List<?>)) {
            layout = getDefaultHorseLoreLayout();
        }

        appendLoreLayoutNodes(
                layout,
                lore,
                lang,
                data,
                genderSymbol,
                currentHealth,
                maxHealth,
                speed,
                jump,
                growth,
                trait,
                isNeutered
        );

        return lore;
    }

    private static List<Object> getDefaultHorseLoreLayout() {
        List<Object> layout = new ArrayList<>();

        List<Object> statsChildren = new ArrayList<>();
        statsChildren.add("gender");
        statsChildren.add("health");
        statsChildren.add("speed");
        statsChildren.add("jump");
        statsChildren.add("growth");
        statsChildren.add("blank");
        layout.add(Map.of("stats", statsChildren));

        List<Object> trainingChildren = new ArrayList<>();
        trainingChildren.add("riding");
        trainingChildren.add("brushing");
        trainingChildren.add("feeding");
        trainingChildren.add("blank");
        layout.add(Map.of("training", trainingChildren));

        layout.add(Map.of("trait", List.of("blank")));
        layout.add("neutered");
        return layout;
    }

    private static void appendLoreLayoutNodes(
            Object node,
            List<String> lore,
            LanguageManager lang,
            PersistentDataContainer data,
            String genderSymbol,
            double currentHealth,
            double maxHealth,
            double speed,
            double jump,
            int growth,
            @Nullable String trait,
            boolean isNeutered
    ) {
        if (node instanceof List<?> list) {
            for (Object child : list) {
                appendLoreLayoutNodes(child, lore, lang, data, genderSymbol, currentHealth, maxHealth, speed, jump, growth, trait, isNeutered);
            }
            return;
        }

        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String rawPart = String.valueOf(entry.getKey());
                List<String> sectionLines = resolveHorseLoreLines(rawPart, lang, data, genderSymbol, currentHealth, maxHealth, speed, jump, growth, trait, isNeutered);
                boolean parentIsGroup = sectionLines.isEmpty() && !isDirectLoreLineToken(rawPart);
                if (!sectionLines.isEmpty()) {
                    lore.addAll(sectionLines);
                }

                if (!sectionLines.isEmpty() || parentIsGroup) {
                    appendLoreLayoutNodes(entry.getValue(), lore, lang, data, genderSymbol, currentHealth, maxHealth, speed, jump, growth, trait, isNeutered);
                }
            }
            return;
        }

        if (node instanceof String rawPart) {
            lore.addAll(resolveHorseLoreLines(rawPart, lang, data, genderSymbol, currentHealth, maxHealth, speed, jump, growth, trait, isNeutered));
        }
    }

    private static boolean isDirectLoreLineToken(String rawPart) {
        String part = rawPart == null ? "" : rawPart.trim().toLowerCase();
        return switch (part) {
            case "gender", "health", "speed", "jump", "growth", "trait", "neutered", "training", "blank", "riding", "brushing", "feeding" -> true;
            default -> part.startsWith("literal:");
        };
    }

    private static List<String> resolveHorseLoreLines(
            String rawPart,
            LanguageManager lang,
            PersistentDataContainer data,
            String genderSymbol,
            double currentHealth,
            double maxHealth,
            double speed,
            double jump,
            int growth,
            @Nullable String trait,
            boolean isNeutered
    ) {
        String part = rawPart == null ? "" : rawPart.trim().toLowerCase();
        List<String> sectionLines = new ArrayList<>();

        switch (part) {
            case "gender" -> sectionLines.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-gender", "%value%", genderSymbol));
            case "health" -> sectionLines.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-health", "%value%", String.format("%.2f", currentHealth), "%max%", String.format("%.2f", maxHealth)));
            case "speed" -> sectionLines.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-speed", "%value%", String.format("%.4f", speed)));
            case "jump" -> sectionLines.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-jump", "%value%", String.format("%.4f", jump)));
            case "growth" -> sectionLines.add(ChatColor.GRAY + lang.getFormattedRaw("messages.lore-growth", "%value%", String.format("%d", growth)));
            case "trait" -> {
                if (trait != null && !trait.isBlank()) {
                    sectionLines.add(ChatColor.GOLD + lang.getFormattedRaw("messages.trait-line", "%trait%", formatTraitName(trait)));
                }
            }
            case "neutered" -> {
                if (isNeutered) {
                    sectionLines.add(ChatColor.DARK_GRAY + lang.getRaw("messages.lore-neutered"));
                }
            }
            case "training" -> {
                String titleLine = TrainingManager.getTrainingTitleLine();
                if (!titleLine.isEmpty()) {
                    sectionLines.add(titleLine);
                }
            }
            case "riding", "brushing", "feeding" -> {
                String line = TrainingManager.getTrainingCategoryLine(data, part);
                if (!line.isEmpty()) {
                    sectionLines.add(line);
                }
            }
            case "blank" -> sectionLines.add("");
            default -> {
                if (part.startsWith("literal:")) {
                    sectionLines.add(ChatColor.translateAlternateColorCodes('&', rawPart.substring("literal:".length())));
                }
            }
        }

        return sectionLines;
    }

    public static boolean isBetterHorse(Entity entity) {
        if (!(entity instanceof AbstractHorse horse)) return false;
        PersistentDataContainer data = horse.getPersistentDataContainer();
        return data.has(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING)
                || (data.has(BetterHorseKeys.HEALTH, PersistentDataType.DOUBLE)
                && data.has(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE)
                && data.has(BetterHorseKeys.JUMP, PersistentDataType.DOUBLE));
    }

    public static boolean isHorseItem(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) return false;
        PersistentDataContainer data = itemStack.getItemMeta().getPersistentDataContainer();
        return data.has(BetterHorseKeys.MOUNT_TYPE, PersistentDataType.STRING)
                || (data.has(BetterHorseKeys.HEALTH, PersistentDataType.DOUBLE)
                && data.has(BetterHorseKeys.SPEED, PersistentDataType.DOUBLE)
                && data.has(BetterHorseKeys.JUMP, PersistentDataType.DOUBLE));
    }

    public static void callSpawnEvent(AbstractHorse horse, ItemStack sourceItem, BetterHorseSpawnEvent.SpawnCause cause) {
        BetterHorses.getInstance().debugLog("API_EVENT", "SPAWN", true, "Calling BetterHorseSpawnEvent for " + horse.getUniqueId() + " cause=" + cause + ".");
        Bukkit.getPluginManager().callEvent(new BetterHorseSpawnEvent(horse, sourceItem, cause));
    }

    public static boolean callDespawnEvent(AbstractHorse horse, ItemStack resultItem) {
        BetterHorses.getInstance().debugLog("API_EVENT", "DESPAWN", true, "Calling BetterHorseDespawnEvent for " + horse.getUniqueId() + ".");
        BetterHorseDespawnEvent event = new BetterHorseDespawnEvent(horse, resultItem);
        Bukkit.getPluginManager().callEvent(event);
        boolean cancelled = event.isCancelled();
        BetterHorses.getInstance().debugLog("API_EVENT", "DESPAWN_RESULT", !cancelled, "Despawn event cancelled=" + cancelled + ".");
        return cancelled;
    }

    private static String formatTraitName(String raw) {
        LanguageManager lang = BetterHorses.getInstance().getLang();
        String path = "traits." + raw.toLowerCase();

        if (lang.getConfig().contains(path)) {
            return ChatColor.translateAlternateColorCodes('&', lang.getConfig().getString(path));
        }

        return raw.substring(0, 1).toUpperCase() + raw.substring(1);
    }

    private static Material getHorseItemMaterial() {
        String materialName = BetterHorses.getInstance().getConfig().getString("settings.horse-item", "SADDLE");
        Material material = Material.getMaterial(materialName.toUpperCase());
        if (material == null || !material.isItem()) return Material.SADDLE;
        return material;
    }

    private static void setAttribute(AbstractHorse horse, Attribute attribute, double value) {
        AttributeInstance attr = horse.getAttribute(attribute);
        if (attr != null) {
            attr.setBaseValue(value);
        }
    }
}
