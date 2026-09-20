package ca.teamdman.sfm.client.screen.workspace;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMPanelEntryAffordanceLayoutTests {
    @Test
    void numberedRegionsShareOneStableGuiCoordinateGeometryForPaintAndHitTesting() {
        SFMScreenPanel first = new SFMTestScreenPanel("first");
        SFMScreenPanel second = new SFMTestScreenPanel("second");
        SFMScreenPanel third = new SFMTestScreenPanel("third");
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.group(SFMWorkspaceLayout.stack(
                1,
                SFMWorkspaceLayout.panel(first),
                SFMWorkspaceLayout.panel(second),
                SFMWorkspaceLayout.panel(third)
        ));
        List<SFMWorkspaceLayout.PanelEntry> entries = layout.panels();
        SFMWorkspaceStackId paneId = layout.stackId(entries.get(0).id()).orElseThrow();

        List<SFMPanelEntryAffordanceLayout.HitRegion> regions = SFMPanelEntryAffordanceLayout.layout(
                new SFMScreenPanelBounds(10, 20, 80, 50),
                paneId,
                entries,
                entries.get(1).id()
        );

        assertEquals(3, regions.size());
        assertEquals(new SFMScreenPanelBounds(50, 56, 11, 11), regions.get(0).bounds());
        assertEquals(new SFMScreenPanelBounds(63, 56, 11, 11), regions.get(1).bounds());
        assertEquals(new SFMScreenPanelBounds(76, 56, 11, 11), regions.get(2).bounds());
        assertFalse(regions.get(0).focused());
        assertTrue(regions.get(1).focused());
        assertSame(second, regions.get(1).capturedPanel());
        assertEquals("panel-entry-" + entries.get(1).id().value(), regions.get(1).stableId());
        assertSame(
                regions.get(1),
                SFMPanelEntryAffordanceLayout.hitTest(regions, 68, 60).orElseThrow()
        );
        assertTrue(SFMPanelEntryAffordanceLayout.hitTest(regions, 62, 60).isEmpty());
    }

    @Test
    void singleEntryPaneHasNoNumberedAffordance() {
        SFMScreenPanel only = new SFMTestScreenPanel("only");
        SFMWorkspaceLayout layout = SFMWorkspaceLayout.single(only);
        SFMWorkspaceLayout.PanelEntry entry = layout.panels().get(0);

        assertTrue(SFMPanelEntryAffordanceLayout.layout(
                new SFMScreenPanelBounds(0, 0, 200, 100),
                layout.stackId(entry.id()).orElseThrow(),
                List.of(entry),
                entry.id()
        ).isEmpty());
    }
}
