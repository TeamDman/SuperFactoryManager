package ca.teamdman.sfm.client.screen.item_picker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMItemPickerLayoutTests {
    @Test
    public void fullScreenUsesGridAndDedicatedPreview() {
        SFMItemPickerLayout layout = SFMItemPickerLayout.calculate(0, 0, 1200, 720);
        assertFalse(layout.compact());
        assertFalse(layout.belowMinimum());
        assertTrue(layout.columns() >= 5);
        assertTrue(layout.preview().width() >= 180);
        assertEquals(layout.content().width(),
                layout.results().width() + 8 + layout.preview().width());
    }

    @Test
    public void multiplexerPanelUsesCompactListOrGridWithoutPreview() {
        SFMItemPickerLayout layout = SFMItemPickerLayout.calculate(600, 0, 300, 220);
        assertTrue(layout.compact());
        assertFalse(layout.belowMinimum());
        assertEquals(layout.content().width(), layout.preview().width());
        assertTrue(layout.preview().height() > 0);
        assertTrue(layout.columns() >= 1);
        assertTrue(layout.results().x() >= 600);
        assertTrue(layout.results().x() + layout.results().width() <= 900);
    }

    @Test
    public void standardScaledFullScreenRetainsCurrentSelectionPreview() {
        SFMItemPickerLayout layout = SFMItemPickerLayout.calculate(0, 0, 600, 360);
        assertFalse(layout.compact());
        assertTrue(layout.preview().width() >= 180);
    }

    @Test
    public void highGuiScaleFullScreenUsesHorizontalSelectionPreview() {
        SFMItemPickerLayout layout = SFMItemPickerLayout.calculate(0, 0, 300, 180);
        assertTrue(layout.compact());
        assertEquals(layout.content().width(), layout.preview().width());
        assertTrue(layout.preview().height() > 0);
    }

    @Test
    public void undersizedPanelProducesSafeDiagnosticGeometry() {
        SFMItemPickerLayout layout = SFMItemPickerLayout.calculate(10, 20, 100, 80);
        assertTrue(layout.belowMinimum());
        assertTrue(layout.content().width() > 0);
        assertTrue(layout.results().height() >= 0);
        assertTrue(layout.footer().y() + layout.footer().height() <= 100);
    }
}
