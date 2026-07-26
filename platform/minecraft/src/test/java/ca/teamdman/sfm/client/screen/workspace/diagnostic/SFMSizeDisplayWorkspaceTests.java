package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SFMSizeDisplayWorkspaceTests {
    private static final SFMScreenPanelBounds VIEWPORT = new SFMScreenPanelBounds(0, 0, 1202, 722);

    @Test
    void fullAndHalfUseTheSharedWorkspaceAllocatorAndDistinctColours() {
        var full = SFMSizeDisplayWorkspace.layout(SFMSizeDisplayWorkspace.Allocation.FULL);
        assertEquals(VIEWPORT, full.bounds(VIEWPORT, 2).get(full.panels().get(0).id()));

        var half = SFMSizeDisplayWorkspace.layout(SFMSizeDisplayWorkspace.Allocation.HALF);
        var bounds = half.bounds(VIEWPORT, 2);
        assertEquals(new SFMScreenPanelBounds(0, 0, 600, 722), bounds.get(half.panels().get(0).id()));
        assertEquals(new SFMScreenPanelBounds(602, 0, 600, 722), bounds.get(half.panels().get(1).id()));
        assertNotEquals(
                background(half, 0),
                background(half, 1),
                "adjacent allocations must be visually distinguishable"
        );
    }

    @Test
    void thirdsAreEqualPeersRatherThanHandCalculatedRectangles() {
        var thirds = SFMSizeDisplayWorkspace.layout(SFMSizeDisplayWorkspace.Allocation.THIRD);
        var bounds = thirds.bounds(VIEWPORT, 2);

        assertEquals(3, thirds.panels().size());
        assertEquals(399, bounds.get(thirds.panels().get(0).id()).width());
        assertEquals(399, bounds.get(thirds.panels().get(1).id()).width());
        assertEquals(400, bounds.get(thirds.panels().get(2).id()).width());
        assertEquals(3, thirds.panels().stream().map(entry -> background(entry.panel())).distinct().count());
    }

    @Test
    void nestedFixtureExercisesBothWorkspaceAxesWithDistinctRegions() {
        var nested = SFMSizeDisplayWorkspace.layout(SFMSizeDisplayWorkspace.Allocation.NESTED);
        var bounds = nested.bounds(VIEWPORT, 2);

        assertEquals(new SFMScreenPanelBounds(0, 0, 600, 722), bounds.get(nested.panels().get(0).id()));
        assertEquals(new SFMScreenPanelBounds(602, 0, 600, 360), bounds.get(nested.panels().get(1).id()));
        assertEquals(new SFMScreenPanelBounds(602, 362, 600, 360), bounds.get(nested.panels().get(2).id()));
        assertEquals(3, nested.panels().stream().map(entry -> background(entry.panel())).distinct().count());
    }

    private int background(SFMWorkspaceLayout layout, int index) {
        return ((SFMSizeDisplayPanel) layout.panels().get(index).panel()).backgroundColour();
    }

    private int background(ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel panel) {
        return ((SFMSizeDisplayPanel) panel).backgroundColour();
    }
}
