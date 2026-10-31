package me.luisgamedev.betterhorses.inspection;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HorseInspectionTextTest {
    @Test
    void mountDataIsInsertedAfterFormattingAndPlaceholderParsing() {
        String name = "<click:run_command:'/op stranger'><red>Horse</red></click> %player_name% &c";
        AtomicReference<String> parserInput = new AtomicReference<>();
        Component rendered = HorseInspectionText.renderTemplate("<gold>%name%</gold> %other_expansion%",
                Map.of("name", Component.text(name)), input -> {
                    parserInput.set(input);
                    return MiniMessage.miniMessage().deserialize(input.replace("%other_expansion%", "External value"));
                });

        assertEquals(name + " External value", PlainTextComponentSerializer.plainText().serialize(rendered));
        assertFalse(parserInput.get().contains(name));
        assertFalse(hasClickEvent(rendered));
    }

    @Test
    void replacementDataIsNotInterpretedAsAnotherReplacement() {
        Component rendered = HorseInspectionText.renderTemplate("%name% / %owner%",
                Map.of("name", Component.text("bhinspectiontoken1end %owner%"), "owner", Component.text("Owner")),
                MiniMessage.miniMessage()::deserialize);

        assertEquals("bhinspectiontoken1end %owner% / Owner", PlainTextComponentSerializer.plainText().serialize(rendered));
    }

    @Test
    void progressUsesConfiguredUnitsAndStaysBounded() {
        assertEquals(25.0, HorseInspectionText.trainingPercent(2500.0, 100.0));
        assertEquals(0.0, HorseInspectionText.trainingPercent(-1.0, 1.0));
        assertEquals(100.0, HorseInspectionText.trainingPercent(1000.0, 2.0));
        assertEquals(100.0, HorseInspectionText.trainingPercent(1.0, 0.0));
        assertEquals(0.0, HorseInspectionText.trainingPercent(Double.NaN, 2.0));
        assertEquals(0.0, HorseInspectionText.trainingPercent(Double.POSITIVE_INFINITY, 2.0));
        assertEquals(10.0, HorseInspectionText.trainingPercent(100.0, Double.NaN));
    }

    @Test
    void jumpConversionMatchesItemLore() {
        assertEquals(2.8917057190264, HorseInspectionText.jumpHeight(0.7), 0.000000001);
        assertEquals(5.2926242638, HorseInspectionText.jumpHeight(1.0), 0.000000001);
    }

    private static boolean hasClickEvent(Component component) {
        return component.clickEvent() != null || component.children().stream().anyMatch(HorseInspectionTextTest::hasClickEvent);
    }
}
