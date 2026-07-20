package ca.teamdman.sfm.client.screen.file_explorer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMFileExplorerLayoutTests {
    @Test
    public void largeViewportUsesComfortableLayout() {
        SFMFileExplorerLayout layout = SFMFileExplorerLayout.calculate(10, 20, 800, 600);
        assertFalse(layout.compact());
        assertFalse(layout.belowMinimum());
        assertEquals(18, layout.content().x());
        assertEquals(28, layout.content().y());
        assertEquals(layout.content().height(), layout.header().height() + layout.list().height() + layout.status().height());
    }

    @Test
    public void panelSizedViewportUsesCompactLayoutWithoutLeavingItsBounds() {
        SFMFileExplorerLayout layout = SFMFileExplorerLayout.calculate(40, 50, 240, 150);
        assertTrue(layout.compact());
        assertFalse(layout.belowMinimum());
        assertTrue(layout.content().x() >= 40);
        assertTrue(layout.content().y() >= 50);
        assertTrue(layout.content().x() + layout.content().width() <= 280);
        assertTrue(layout.content().y() + layout.content().height() <= 200);
    }

    @Test
    public void undersizedViewportIsExplicitlyReportedAndStillProducesSafeGeometry() {
        SFMFileExplorerLayout layout = SFMFileExplorerLayout.calculate(0, 0, 100, 60);
        assertTrue(layout.belowMinimum());
        assertTrue(layout.content().width() > 0);
        assertTrue(layout.header().height() + layout.list().height() + layout.status().height() <= layout.content().height());
    }
}
