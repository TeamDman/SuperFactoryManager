package ca.teamdman.sfm.client.screen.color;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SFMColorInputPanelLayoutTests {
    @Test
    void wideLayoutKeepsEveryControlInsideItsPanel() {
        SFMColorInputPanelLayout layout = SFMColorInputPanelLayout.fit(new SFMScreenPanelBounds(0, 0, 600, 360));
        assertFalse(layout.compact());
        assertInside(layout.panel(), layout.hueSaturation());
        assertInside(layout.panel(), layout.valueSlider());
        assertInside(layout.panel(), layout.swatch());
        assertInside(layout.panel(), layout.hex());
        assertInside(layout.panel(), layout.channels());
        assertInside(layout.panel(), layout.recents());
        assertInside(layout.panel(), layout.reset());
        assertInside(layout.panel(), layout.cancel());
        assertInside(layout.panel(), layout.confirm());
    }

    @Test
    void compactLayoutStacksControlsWithoutLeavingBounds() {
        SFMColorInputPanelLayout layout = SFMColorInputPanelLayout.fit(new SFMScreenPanelBounds(20, 30, 420, 330));
        assertTrue(layout.compact());
        assertTrue(layout.valueSlider().y() >= layout.hueSaturation().bottom());
        assertInside(layout.panel(), layout.channels());
        assertInside(layout.panel(), layout.recents());
        assertTrue(layout.confirm().right() <= layout.panel().right());
    }

    @Test
    void halfWorkspaceUsesUltraCompactLayoutWithoutHorizontalOverflow() {
        SFMColorInputPanelLayout layout = SFMColorInputPanelLayout.fit(new SFMScreenPanelBounds(200, 0, 200, 360));
        assertTrue(layout.compact());
        assertInside(layout.panel(), layout.hueSaturation());
        assertInside(layout.panel(), layout.hex());
        assertInside(layout.panel(), layout.order());
        assertInside(layout.panel(), layout.channels());
        assertInside(layout.panel(), layout.recents());
        assertTrue(layout.reset().right() <= layout.cancel().x());
        assertTrue(layout.cancel().right() <= layout.confirm().x());
    }

    @Test
    void puppetScaleCompactLayoutKeepsHitTargetsClear() {
        SFMColorInputPanelLayout layout = SFMColorInputPanelLayout.fit(new SFMScreenPanelBounds(0, 0, 400, 240));
        assertAllInside(layout);
        assertFalse(intersects(layout.channels(), layout.confirm()));
        assertFalse(intersects(layout.channels(), layout.cancel()));
        assertFalse(intersects(layout.recents(), layout.reset()));
    }

    @Test
    void puppetScaleSidePanelKeepsHitTargetsClear() {
        SFMColorInputPanelLayout layout = SFMColorInputPanelLayout.fit(new SFMScreenPanelBounds(201, 0, 199, 240));
        assertAllInside(layout);
        assertFalse(intersects(layout.channels(), layout.confirm()));
        assertFalse(intersects(layout.channels(), layout.cancel()));
        assertFalse(intersects(layout.recents(), layout.reset()));
    }

    private static void assertAllInside(SFMColorInputPanelLayout layout) {
        assertInside(layout.panel(), layout.hueSaturation());
        assertInside(layout.panel(), layout.valueSlider());
        assertInside(layout.panel(), layout.swatch());
        assertInside(layout.panel(), layout.hex());
        assertInside(layout.panel(), layout.order());
        assertInside(layout.panel(), layout.channels());
        assertInside(layout.panel(), layout.recents());
        assertInside(layout.panel(), layout.reset());
        assertInside(layout.panel(), layout.cancel());
        assertInside(layout.panel(), layout.confirm());
    }

    private static boolean intersects(SFMColorInputPanelLayout.Rect left, SFMColorInputPanelLayout.Rect right) {
        return left.x() < right.right() && left.right() > right.x()
                && left.y() < right.bottom() && left.bottom() > right.y();
    }

    private static void assertInside(SFMColorInputPanelLayout.Rect outer, SFMColorInputPanelLayout.Rect inner) {
        assertTrue(inner.x() >= outer.x());
        assertTrue(inner.y() >= outer.y());
        assertTrue(inner.right() <= outer.right());
        assertTrue(inner.bottom() <= outer.bottom());
    }
}
