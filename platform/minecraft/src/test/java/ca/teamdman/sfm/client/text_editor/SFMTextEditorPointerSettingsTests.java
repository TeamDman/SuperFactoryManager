package ca.teamdman.sfm.client.text_editor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SFMTextEditorPointerSettingsTests {
    @Test void allCombinationsKeepSelectionSeparateAndTogglesIndependent() {
        for (boolean wheel : new boolean[]{false, true}) {
            for (boolean middle : new boolean[]{false, true}) {
                var settings = new SFMTextEditorPointerSettings(wheel, middle);
                assertNotEquals(0, settings.panButton());
                assertNotEquals(0, settings.actionButton());
                assertNotEquals(settings.panButton(), settings.actionButton());
                assertEquals(settings, settings.toggleWheel().toggleWheel());
                assertEquals(settings, settings.toggleButtons().toggleButtons());
                assertEquals(middle, settings.toggleWheel().middlePans());
                assertEquals(wheel, settings.toggleButtons().wheelZooms());
                assertEquals(settings.panButton(), settings.toggleButtons().actionButton());
            }
        }
    }
    @Test void changingOneEditorDoesNotChangeTheSharedDefault() {
        var second = SFMTextEditorPointerSettings.DEFAULT;
        var first = second.toggleWheel().toggleButtons();
        assertNotEquals(first, second);
        assertTrue(second.middlePans());
        assertTrue(second.wheelZooms());
    }
}
