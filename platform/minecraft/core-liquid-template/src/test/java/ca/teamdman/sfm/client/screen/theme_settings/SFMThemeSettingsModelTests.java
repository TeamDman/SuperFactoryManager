package ca.teamdman.sfm.client.screen.theme_settings;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.theme.*;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class SFMThemeSettingsModelTests {
    @Test void exposesAndEditsAllTypedPropertyCategories() {
        SFMThemeSettingsModel model = new SFMThemeSettingsModel(SFMClientTheme.defaults());
        assertEquals(EnumSet.allOf(SFMThemeProperty.Kind.class), model.properties().stream()
                .map(SFMThemeProperty::kind).collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(SFMThemeProperty.Kind.class))));
        model.select(SFMThemeProperty.Kind.COLOUR, "panel.background");
        model.setSelectedColour(0xFF345678);
        model.select(SFMThemeProperty.Kind.FILE_ICON, ".sfml");
        model.setSelectedIcon(SFMItemIcon.vanilla("chest", "SFM program"));
        assertEquals(0xFF345678, model.draft().colour(SFMColourRole.PANEL_BACKGROUND));
        assertEquals("minecraft:chest", model.draft().fileIcon(".sfml").requestedItem().toString());
        assertTrue(model.dirty());
    }

    @Test void syntaxColourEditPreservesStyleFlagsAndResetRestoresLoadedValue() {
        SFMThemeSettingsModel model = new SFMThemeSettingsModel(SFMClientTheme.defaults());
        model.select(SFMThemeProperty.Kind.SYNTAX, "keyword");
        SFMSyntaxStyle before = model.draft().syntax("keyword");
        model.setSelectedColour(0xFF010203);
        SFMSyntaxStyle after = model.draft().syntax("keyword");
        assertEquals(before.bold(), after.bold());
        assertEquals(before.italic(), after.italic());
        assertEquals(before.underlined(), after.underlined());
        model.resetSelected();
        assertEquals(before, model.draft().syntax("keyword"));
    }
}
