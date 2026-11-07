package me.luisgamedev.betterhorses.language;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LanguageManagerTest {
    private static LanguageManager language;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    @BeforeAll static void initializeParserWithoutAWorldOrConfigurationFile() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        language = (LanguageManager) ((Unsafe) unsafeField.get(null)).allocateInstance(LanguageManager.class);
        set("miniMessage", MINI_MESSAGE);
        set("legacySerializer", LegacyComponentSerializer.builder().character('§')
                .hexColors().useUnusualXRepeatedCharacterHexFormat().build());
    }

    @Test void queryParametersAndCommandArgumentsStayLiteral() {
        for (String source : List.of(
                "<click:open_url:'https://example.com/?horse=1&color=red&amount=2'>Horse</click>",
                "<click:open_url:\"https://example.com/?horse=1&color=red\">Horse</click>",
                "<click:run_command:'/horse info &color=red'>Horse</click>",
                "<click:run_command:'/horse name \\'Daisy\\' &color=red'>Horse</click>",
                "<hover:show_text:'Compare > keep &color=red'>Horse</hover>")) {
            assertEquals(MINI_MESSAGE.deserialize(source), language.parse(null, source), source);
        }
    }

    @Test void ampersandsWithoutLegacyCodesStayLiteral() {
        String source = "Rock & roll; &z stays literal";
        assertEquals(MINI_MESSAGE.deserialize(source), language.parse(null, source));
    }

    @Test void escapedTagOpeningsDoNotHideLaterLegacyFormatting() {
        assertEquals("<red>§aGreen", language.parseToString(null, "\\<red>&aGreen"));
    }

    @Test void legacyColorsResetEveryEarlierDecoration() {
        for (String decoration : List.of("&k", "&l", "&m", "&n", "&o", "&l&o&n")) {
            String source = decoration + "Before &aAfter";
            String expected = LegacyComponentSerializer.legacySection().serialize(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(source));
            assertEquals(expected, language.parseToString(null, source), source);
        }
    }

    @Test void allLegacyRgbFormatsResetDecorationsAndKeepTheirExactColor() {
        String expected = "§lBold §x§1§2§a§b§3§4Green";
        for (String color : List.of("&#12ab34", "&x&1&2&a&b&3&4", "§x§1§2§a§b§3§4")) {
            assertEquals(expected, language.parseToString(null, "&lBold " + color + "Green"), color);
        }
    }

    @Test void aLegacyColorChangeRetainsTheEnclosingClickEvent() {
        Component parsed = language.parse(null, "<click:run_command:'/horse info'>&lBold &aGreen</click>");
        assertEquals(ClickEvent.runCommand("/horse info"), firstClick(parsed));
        assertEquals("§lBold §aGreen", LegacyComponentSerializer.legacySection().serialize(parsed));
    }

    @Test void ordinaryMiniMessageColorsAndGradientsAreUnchanged() {
        for (String source : List.of("<#12ab34>Horse</#12ab34>",
                "<bold><gradient:#12ab34:#4477ff>Horse</gradient></bold>")) {
            assertEquals(MINI_MESSAGE.deserialize(source), language.parse(null, source));
        }
    }

    @Test void aPlainComparisonDoesNotStartAMiniMessageTag() {
        assertEquals("2 < 3 §aGreen > 1", language.parseToString(null, "2 < 3 &aGreen > 1"));
    }

    private static ClickEvent firstClick(Component component) {
        if (component.clickEvent() != null) return component.clickEvent();
        for (Component child : component.children()) {
            ClickEvent click = firstClick(child);
            if (click != null) return click;
        }
        return null;
    }

    private static void set(String name, Object value) throws Exception {
        Field field = LanguageManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(language, value);
    }
}
