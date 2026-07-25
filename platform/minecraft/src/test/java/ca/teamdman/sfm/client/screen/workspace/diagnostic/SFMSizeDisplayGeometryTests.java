package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMSizeDisplayGeometryTests {
    @Test
    void backgroundCoversTheWholeAllocationAndTextIsCentered() {
        var bounds = new SFMScreenPanelBounds(10, 20, 101, 80);
        var geometry = SFMSizeDisplayGeometry.create(bounds, "640 × 360", 50, 9, 0xFF2563EB);

        assertEquals(bounds, geometry.background());
        assertEquals(35, geometry.textX());
        assertEquals(55, geometry.textY());
        assertEquals("640 × 360", geometry.text());
    }

    @Test
    void contrastRuleChoosesWhiteForDarkAndBlackForLightBackgrounds() {
        assertEquals(0xFFFFFFFF, SFMSizeDisplayGeometry.accessibleTextColour(0xFF1D4ED8));
        assertEquals(0xFF000000, SFMSizeDisplayGeometry.accessibleTextColour(0xFFF97316));
        assertEquals(0xFF000000, SFMSizeDisplayGeometry.accessibleTextColour(0xFFFFFFFF));
    }

    @Test
    void alphaIsForcedOpaqueBecauseTheDisplayIsASolidBackground() {
        var geometry = SFMSizeDisplayGeometry.create(
                new SFMScreenPanelBounds(0, 0, 20, 20), "0 × 0", 20, 9, 0x00222222);

        assertEquals(0xFFFFFFFF, geometry.textColour());
    }

    @Test
    void negativeTextDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SFMSizeDisplayGeometry.create(
                new SFMScreenPanelBounds(0, 0, 20, 20), "size", -1, 9, 0xFF000000));
    }
}
