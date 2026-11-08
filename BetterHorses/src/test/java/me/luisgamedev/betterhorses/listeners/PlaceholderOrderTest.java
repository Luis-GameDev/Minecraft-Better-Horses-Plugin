package me.luisgamedev.betterhorses.listeners;

import me.luisgamedev.betterhorses.api.BetterHorseKeys;
import me.luisgamedev.betterhorses.api.BetterHorsesAPI;
import me.luisgamedev.betterhorses.language.LanguageManager;
import me.luisgamedev.betterhorses.training.TrainingManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import static me.luisgamedev.betterhorses.listeners.TraitLifecycleRegressionTest.*;
import static org.junit.jupiter.api.Assertions.*;

class PlaceholderOrderTest {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    @Test void betterHorsesValuesThenPapiOutputAreResolvedBeforeMiniMessage() throws Exception {
        OrderedLanguage language = language();
        language.getConfig().set("line", "<gradient:%demo_start%:%demo_end%>%value%</gradient>");
        var actual = language.getFormattedRawComponent((OfflinePlayer) null, "line", "%value%", "%demo_value%");
        assertEquals("<gradient:%demo_start%:%demo_end%>%demo_value%</gradient>", language.beforePapi);
        assertEquals(MINI.deserialize("<gradient:red:blue><bold>123456</bold></gradient>"), actual);
        assertEquals(1, language.parses);
    }

    @Test void composedApiGenderAndNameAreParsedTogetherAtTheEnd() throws Exception {
        try (Fixture f = new Fixture()) {
            HorseItemTransactionRegressionTest.prepareApi(f);
            OrderedLanguage language = language();
            language.getConfig().set("messages.gender-male", "%demo_plain%");
            language.getConfig().set("messages.horse-item-name", "<gradient:red:blue>%name% %gender%</gradient>");
            f.plugin.language = language;
            var item = BetterHorsesAPI.createHorseItem(20, .2, .7, "male", "Probe", f.player.handle,
                    null, false, "none", false, 10, null);
            assertEquals(LEGACY.serialize(MINI.deserialize("<gradient:red:blue>Probe 124</gradient>")),
                    item.getItemMeta().getDisplayName());
            assertEquals(1, language.parses);
            assertEquals(f.player.handle, language.viewer);
        }
    }

    @Test void trainingExpandsNumericValuesAndPapiBeforeApplyingItsGradient() throws Exception {
        try (Fixture f = new Fixture()) {
            OrderedLanguage language = language();
            f.plugin.language = language;
            f.config.set("training.enabled", true);
            f.config.set("training.lore.enabled", true);
            f.config.set("training.categories.riding.units-per-percent", 100.0);
            f.horse.data.set(BetterHorseKeys.TRAINING_RIDING_UNITS, PersistentDataType.DOUBLE, 1200.0);
            language.getConfig().set("training-lore.categories.riding", "<gradient:red:blue>%percent% %demo_plain%</gradient>");
            String actual = TrainingManager.getTrainingCategoryLine(f.horse.data, "riding", f.player.handle);
            assertEquals(LEGACY.serialize(MINI.deserialize("<gradient:red:blue>12 124</gradient>")), actual);
            assertEquals(1, language.parses);
            assertEquals(f.player.handle, language.viewer);
            assertFalse(language.beforePapi.contains("%percent%"));
        }
    }

    private static OrderedLanguage language() throws Exception {
        OrderedLanguage language = (OrderedLanguage) ((Unsafe) field(Unsafe.class, "theUnsafe").get(null))
                .allocateInstance(OrderedLanguage.class);
        field(LanguageManager.class, "lang").set(language, new YamlConfiguration());
        field(LanguageManager.class, "miniMessage").set(language, MINI);
        field(LanguageManager.class, "legacySerializer").set(language, LEGACY);
        return language;
    }

    public static class OrderedLanguage extends LanguageManager {
        String beforePapi;
        int parses;
        OfflinePlayer viewer;
        public OrderedLanguage() { super(null, null); }
        @Override protected String parsePlaceholders(OfflinePlayer player, String message) {
            beforePapi = message;
            viewer = player;
            parses++;
            return message.replace("%demo_start%", "red").replace("%demo_end%", "blue")
                    .replace("%demo_value%", "<bold>123456</bold>").replace("%demo_plain%", "124");
        }
    }
}
