package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.commands.CustomHorseCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static me.luisgamedev.betterhorses.listeners.TraitLifecycleRegressionTest.*;
import static org.junit.jupiter.api.Assertions.*;

class HorseCreationOptionsTest {
    @Test void explicitNoneDoesNotRollWhileNullKeepsTheRandomDefault() throws Exception {
        for (String option : new String[]{"none", "NONE", "null", "NuLl"}) {
            try (Fixture f = new Fixture()) {
                HorseItemTransactionRegressionTest.prepareApi(f);
                f.config.set("traits.dashboost.chance", 1.0);
                ItemStack[] delivered = {null};
                PlayerInventory inventory = proxy(PlayerInventory.class, (target, method, args) -> {
                    if (method.getName().equals("addItem")) {
                        delivered[0] = ((ItemStack[]) args[0])[0];
                        return new HashMap<Integer, ItemStack>();
                    }
                    return zero(method.getReturnType());
                });
                Player player = proxy(Player.class, (target, method, args) -> method.getName().equals("getInventory")
                        ? inventory : f.player.invoke(target, method, args));
                assertTrue(new CustomHorseCommand().onCommand(player, null, "horsecreate",
                        new String[]{"20", ".2", ".7", "male", "Probe", option, "10", "horse", "false"}));
                assertNotNull(delivered[0]);
                String trait = delivered[0].getItemMeta().getPersistentDataContainer().get(BetterHorseKeys.TRAIT, PersistentDataType.STRING);
                assertEquals(option.equalsIgnoreCase("none") ? null : "dashboost", trait, option);
            }
        }
    }

    @Test void apiGenderSymbolsMatchAcceptedCaseInsensitiveValues() throws Exception {
        try (Fixture f = new Fixture()) {
            HorseItemTransactionRegressionTest.prepareApi(f);
            for (String gender : new String[]{"male", "MALE", "female", "FeMaLe"}) {
                ItemStack item = BetterHorsesAPI.createHorseItem(20, .2, .7, gender, "Probe", null, null, false, "none", false, 10, null);
                assertEquals("Probe " + gender.toLowerCase(java.util.Locale.ROOT), item.getItemMeta().getDisplayName());
            }
        }
    }
}
