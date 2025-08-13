package me.luisgamedev.betterhorses.bootstrap;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.RegistryEvents;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import io.papermc.paper.registry.entry.entries.enchantment.EnchantmentRegistryEntry;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.EquipmentSlotGroup;

public class SwingDamageBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(
                RegistryEvents.ENCHANTMENT.compose().newHandler(event -> {
                    event.registry().register(
                            Key.key("betterhorses", "swing_damage"),
                            b -> b
                                    .description(Component.text("Swing Damage"))
                                    .supportedItems(event.getOrCreateTag(ItemTypeTagKeys.SWORDS))
                                    .anvilCost(1)
                                    .maxLevel(3)
                                    .weight(10)
                                    .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(1, 1))
                                    .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(30, 1))
                                    .activeSlots(EquipmentSlotGroup.MAINHAND)
                    );
                })
        );
    }
}
